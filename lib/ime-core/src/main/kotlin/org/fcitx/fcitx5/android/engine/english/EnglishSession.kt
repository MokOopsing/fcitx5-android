/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.english

import org.fcitx.fcitx5.android.engine.data.NgramModel
import org.fcitx.fcitx5.android.engine.session.Action
import org.fcitx.fcitx5.android.engine.session.Choice
import org.fcitx.fcitx5.android.engine.session.Offer
import org.fcitx.fcitx5.android.engine.session.Session
import org.fcitx.fcitx5.android.engine.session.Snapshot

/**
 * English typed a word at a time, as Gboard has it. The candidates are the word typed and the
 * words it may be ([EnglishDecoder]: those it spells, starts, or spells with a slip), scored after
 * the words before the cursor. A word typed that is none is corrected: the likeliest word it
 * spells otherwise (dont as don't, london as London) or with a slip (teh as the) is the first
 * candidate, what space commits; backspace right after takes the correction back, the word typed
 * shown again, and that word is never corrected again. Punctuation commits the word as space does,
 * correction and all.
 *
 * A word committed by space or picked is followed by a space and by the words that may come next
 * ([Snapshot.predicting]); punctuation typed right after puts that space behind it (`word .` as
 * `word. `), and space typed again turns it into a full stop (`word  ` as `word. `). A lone i is
 * I. Enter commits the word as typed and is the app's all the same: a search runs at once. A tap
 * in the word puts the cursor there, where letters go in and backspace deletes.
 *
 * Keys it does not [read][reads] (digits, punctuation, space with nothing typed) are the app's.
 * The host hands it every key (see the host's `Keyboard`): space and digits pick nothing here.
 * Where nothing is to be suggested (a password, an editor asking for none, suggestions turned
 * off) the addon hands it none: the keys type themselves.
 *
 * What it knows of the text before the cursor ([Action.Context] and what it committed since) is
 * kept only while [learning]: without, every word is scored as a sentence's first might be.
 */
class EnglishSession(
    private val data: EnglishData?,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    costs: TypingCosts = TypingCosts(),
    private val maxCorrection: Float = MAX_CORRECTION,
) : Session {

    private val decoder = data?.let { EnglishDecoder(it, costs) }

    private val input = StringBuilder()
    private var cursor = 0 // in input
    private var candidates: List<String> = emptyList()
    private var page = 0

    // what space commits in place of the word typed, if anything
    private var correction: String? = null

    // the word typed and what space committed for it, while backspace may take the correction back
    private var corrected: Pair<String, String>? = null

    // shown again by backspace: committed as typed
    private var keepTyped = false

    // words whose correction the user took back: theirs, never corrected
    private val kept = HashSet<String>()

    // what this session committed last, if the cursor stayed after it: a space it ends with is
    // the session's own, which the space rules may take back
    private var last = ""

    // the end of the text before the cursor, as far as the session knows it: if [known], from the
    // app's (Action.Context) on; else what it committed alone
    private var recent = ""
    private var known = false

    override var learning = true
        set(value) {
            field = value
            if (!value) {
                recent = ""
                known = false
            }
        }

    override fun reads(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || (c == '\'' || c == '-') && input.isNotEmpty()

    override fun apply(action: Action): Snapshot = when (action) {
        is Action.Key -> key(action.char)
        Action.Backspace -> backspace()
        is Action.Select -> if (action.index in 0 until pageSize) pick(page * pageSize + action.index) else snapshot(handled = candidates.isNotEmpty())
        is Action.Pick -> pick(action.index)
        Action.NextPage -> turn(page + 1)
        Action.PreviousPage -> turn(page - 1)
        Action.CommitRaw -> if (input.isEmpty()) snapshot(handled = false) else commit(typed(), space = false).copy(handled = false)
        is Action.Cursor -> if (input.isEmpty()) {
            snapshot(handled = false)
        } else {
            cursor = action.at.coerceIn(0, input.length)
            snapshot()
        }
        is Action.Context -> {
            forgetText()
            if (learning) {
                recent = action.before.takeLast(EnglishText.CONTEXT_CHARS)
                known = true
            }
            snapshot()
        }
        Action.Reset -> {
            forgetText()
            snapshot()
        }
        Action.Refine, is Action.Forget, is Action.Pin, is Action.Unpin, is Action.Block, is Action.Syllable, is Action.Letter -> snapshot()
    }

    override fun candidates(from: Int, count: Int): List<Choice> =
        candidates.subList(minOf(from, candidates.size), minOf(from + count, candidates.size)).map { Choice(it) }

    override fun offers(index: Int): Set<Offer> = emptySet()

    private fun key(c: Char): Snapshot {
        corrected = null
        if (reads(c)) {
            if (input.isEmpty()) candidates = emptyList()
            input.insert(cursor++, c)
            read()
            return snapshot()
        }
        if (input.isNotEmpty()) {
            val typed = typed()
            // space and punctuation end a word as meant; a digit or a symbol goes on with it as typed
            val word = if (c == ' ' || c in SWAPPED) correction ?: typed else typed
            // space ends the word with a space of the session's own; anything else is the app's
            if (c == ' ') return commit(word, space = true).also { if (word != typed) corrected = typed to last }
            clear()
            last = ""
            wrote(word + c)
            return snapshot(commit = word, handled = false)
        }
        val wordBefore = last.length >= 2 && last.last() == ' ' && last[last.length - 2].isLetterOrDigit()
        val made = when {
            !wordBefore -> null
            // twice: a full stop
            c == ' ' -> ". "
            c in SWAPPED -> "$c "
            else -> null
        }
        if (made == null) {
            clear()
            last = ""
            wrote(c.toString())
            return snapshot(handled = false)
        }
        last = made
        recent = recent.dropLast(1)
        wrote(made)
        candidates = predictions()
        page = 0
        return snapshot(commit = made, deleteBefore = 1)
    }

    private fun backspace(): Snapshot {
        val undo = corrected
        corrected = null
        if (input.isEmpty()) {
            if (undo != null && undo.second == last) return takeBack(undo.first, undo.second)
            last = ""
            candidates = emptyList()
            recent = recent.dropLast(1)
            return snapshot(handled = false)
        }
        last = ""
        // at the start of the word: nothing before it is the session's to delete
        if (cursor == 0) return snapshot()
        input.deleteCharAt(--cursor)
        read()
        return snapshot()
    }

    // the correction [committed] for [typed] taken back: the word typed shown again, kept as typed
    private fun takeBack(typed: String, committed: String): Snapshot {
        last = ""
        recent = recent.dropLast(committed.length)
        if (learning) kept += typed
        input.setLength(0)
        input.append(typed)
        cursor = input.length
        keepTyped = true
        read()
        return snapshot(deleteBefore = committed.length)
    }

    private fun pick(index: Int): Snapshot {
        val text = candidates.getOrNull(index) ?: return snapshot(handled = candidates.isNotEmpty())
        corrected = null
        return commit(text, space = true)
    }

    private fun commit(text: String, space: Boolean): Snapshot {
        val committed = if (space) "$text " else text
        clear()
        last = committed
        wrote(committed)
        if (space) candidates = predictions()
        return snapshot(commit = committed)
    }

    private fun turn(to: Int): Snapshot {
        if (to >= 0 && to * pageSize < candidates.size) page = to
        return snapshot()
    }

    private fun clear() {
        input.setLength(0)
        cursor = 0
        candidates = emptyList()
        page = 0
        correction = null
        keepTyped = false
    }

    private fun forgetText() {
        clear()
        last = ""
        recent = ""
        known = false
        corrected = null
    }

    // [text] went in before the cursor
    private fun wrote(text: String) {
        if (learning) recent = (recent + text).takeLast(EnglishText.CONTEXT_CHARS)
    }

    // the word typed, then what it may be, in its case; the correction first, if any
    private fun read() {
        page = 0
        correction = null
        if (input.isEmpty()) {
            candidates = emptyList()
            return
        }
        val typed = typed()
        val keys = EnglishData.key(input.toString())
        val before = before()
        // no decoder, no correction: the context is not looked at
        val context = decoder?.context(before) ?: 0L
        val found = decoder?.suggest(keys, context, SUGGESTED + 1).orEmpty()
        // a capital the sentence's start does not account for is the user's: a name, not a slip
        val upper = typed.count { it.isUpperCase() }
        val name = upper > 1 || upper == 1 && !(typed[0].isUpperCase() && before.lastOrNull().let { it == null || it == EnglishText.START })
        // each part of a word with a hyphen is one, which the words have none of: as typed
        correction = if (keepTyped || typed in kept || '-' in typed) null else correctionOf(typed, keys, found, context, name)?.let(::inCase)
        val words = found.map { inCase(checkNotNull(data).word(it.word)) }
        candidates = (listOfNotNull(correction) + typed + words).distinct().take(SUGGESTED + 1)
    }

    /**
     * What the word [typed] was meant to be, of what was [found] for its [keys] after [context]:
     * - typed no word, the likeliest the keys spell written otherwise (dont as don't, london as
     *   London), or else the likeliest a slip of no more than [maxCorrection] from them (teh as
     *   the), from [MIN_CORRECTED] keys; typed as a [name], a slip of no more than
     *   [NAME_CORRECTION], of a word with a capital (Stebe as Steve; Anushika is someone's);
     * - typed a word (nad, oft, of the many the pages have), the same if [KNOWN_MARGIN] likelier,
     *   but not a capital more or less (ladies, Ladies), nor a longer word (gear, years).
     * An apostrophe typed is never taken away. Null for none, or for keys that start a word likelier.
     */
    private fun correctionOf(typed: String, keys: String, found: List<EnglishDecoder.Suggestion>, context: Long, name: Boolean): String? {
        val data = data ?: return null
        val id = data.id(typed).takeIf { it != NgramModel.NO_WORD } ?: data.id(typed.lowercase())
        val own = if (id == NgramModel.NO_WORD) null else data.model.scoreAfter(context, id)
        val allowed = allowedFor(data, typed, id, known = own != null)
        val written = found.firstOrNull { !it.completes && EnglishData.key(data.word(it.word)) == keys && allowed(it) }
        val slip = slipOf(data, keys, found, name, allowed)
        val best = (if (own == null) written ?: slip else slip ?: written) ?: return null
        if (startsInstead(data, best, keys, known = own != null)) return null
        if (own != null && best.score - own < KNOWN_MARGIN) return null
        return data.word(best.word)
    }

    // what may stand for [typed] (the word [id], if [known]): not itself, an apostrophe typed kept,
    // and a word not as typed but for a capital more or less
    private fun allowedFor(data: EnglishData, typed: String, id: Int, known: Boolean): (EnglishDecoder.Suggestion) -> Boolean {
        val apostrophe = '\'' in typed
        // what follows the last apostrophe typed: s, ll, t, or nothing (dogs')
        val after = typed.substringAfterLast('\'').lowercase()
        return { s ->
            val text = data.word(s.word)
            val kept = !apostrophe || '\'' in text && text.substringAfterLast('\'').lowercase() == after
            s.word != id && kept && !(known && text.equals(typed, ignoreCase = true))
        }
    }

    private fun slipOf(
        data: EnglishData,
        keys: String,
        found: List<EnglishDecoder.Suggestion>,
        name: Boolean,
        allowed: (EnglishDecoder.Suggestion) -> Boolean,
    ): EnglishDecoder.Suggestion? {
        if (keys.length < MIN_CORRECTED) return null
        val most = if (name) NAME_CORRECTION else maxCorrection
        return found.firstOrNull { it.cost <= most && allowed(it) && (!name || data.word(it.word)[0].isUpperCase()) }
    }

    // typed so far, not wrong: the start of a word likelier than a slip (hel for hello, not he);
    // a letter short with a slip as well is wrong all the same (softwae for software), but for a word
    private fun startsInstead(data: EnglishData, best: EnglishDecoder.Suggestion, keys: String, known: Boolean): Boolean {
        if (!best.completes || known) return best.completes
        return best.cost == 0f || EnglishData.key(data.word(best.word)).length > keys.length + 1
    }

    // the words that may come next, a sentence's first capitalised
    private fun predictions(): List<String> {
        val decoder = decoder ?: return emptyList()
        val data = checkNotNull(data)
        val before = before()
        // nothing known of the text: nothing to follow
        if (before.isEmpty()) return emptyList()
        val start = before.lastOrNull() == EnglishText.START
        return decoder.predict(decoder.context(before), SUGGESTED + 1)
            .map { data.word(it.word) }
            .filter { it.first().isLetter() }
            .map { if (start) it.replaceFirstChar(Char::uppercaseChar) else it }
            .distinct()
    }

    // the words before the cursor, as EnglishText reads them: what the session committed, if that
    // is all it knows, starting no sentence that is known
    private fun before(): List<String> = EnglishText.context(recent, start = known)

    /** What was typed, as committed: i alone, and i before an apostrophe (i'm), as I. */
    private fun typed(): String {
        val s = input.toString()
        return if (s == "i" || s.startsWith("i'")) "I" + s.substring(1) else s
    }

    // [word], as written, as the input has its case: Hello for he, HELLO for HE; I' at a
    // sentence's start is I'm's, not I'M's
    private fun inCase(word: String): String = when {
        input.count { it.isLetter() } > 1 && input.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase()
        input[0].isUpperCase() -> word.replaceFirstChar { it.uppercaseChar() }
        else -> word
    }

    private fun snapshot(commit: String = "", handled: Boolean = true, deleteBefore: Int = 0): Snapshot {
        val from = page * pageSize
        val shown = candidates.subList(minOf(from, candidates.size), minOf(from + pageSize, candidates.size))
        return Snapshot(
            commit = commit,
            preedit = input.toString(),
            candidates = shown,
            page = page,
            hasPreviousPage = page > 0,
            hasNextPage = from + pageSize < candidates.size,
            handled = handled,
            predicting = input.isEmpty() && candidates.isNotEmpty(),
            total = candidates.size,
            first = from,
            deleteBefore = deleteBefore,
            cursor = if (cursor == input.length) -1 else cursor,
        )
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 5

        /** How many words are suggested besides the one typed. */
        const val SUGGESTED = 9

        /** The most a slip may cost ([TypingCosts]) for space to correct it: a key far from the one meant. */
        const val MAX_CORRECTION = 3.0f

        /** Fewer keys than this are corrected only as written otherwise (i as I): too many words are a slip from one. */
        const val MIN_CORRECTED = 2

        /** The most a slip may cost in a word typed with a capital of its own, a name likelier than not: a key beside. */
        const val NAME_CORRECTION = 1.0f

        /** How much likelier (log10) a word must be than the word typed, one too, to be put in its place: a hundred times. */
        const val KNOWN_MARGIN = 2f

        /** Punctuation that takes the place of the space after a word, a space after it. */
        private const val SWAPPED = ".,!?;:"
    }
}
