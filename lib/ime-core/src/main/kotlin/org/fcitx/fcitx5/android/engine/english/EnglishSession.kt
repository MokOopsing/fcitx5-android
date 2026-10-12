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
 *
 * While [learning], each word committed is counted in [EnglishUser], after the word before it:
 * scored up as typed again, offered after that word, and, the data lacking it, offered by its keys.
 * A word counted twice is the user's, never corrected; a correction taken back makes it theirs at
 * once. A long press on a word counted forgets it.
 */
class EnglishSession(
    private val data: EnglishData?,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val costs: TypingCosts = TypingCosts(),
    private val maxCorrection: Float = MAX_CORRECTION,
    user: EnglishUser? = null,
) : Session {

    // what the user committed, kept by the host; else for this session alone
    private val user = user ?: EnglishUser()

    private val decoder = data?.let { d -> EnglishDecoder(d, costs) { context, word -> scored(d, context, word) } }

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
        // an arrow, a tab, enter: the word left as it is, maybe half typed, so not learned
        Action.CommitRaw -> if (input.isEmpty()) snapshot(handled = false) else commit(typed(), space = false, learned = 0f).copy(handled = false)
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
        is Action.Forget -> forget(action.index)
        Action.Refine, is Action.Pin, is Action.Unpin, is Action.Block, is Action.Syllable, is Action.Letter -> snapshot()
    }

    override fun candidates(from: Int, count: Int): List<Choice> =
        candidates.subList(minOf(from, candidates.size), minOf(from + count, candidates.size)).map { Choice(it) }

    override fun offers(index: Int): Set<Offer> =
        if (candidates.getOrNull(index)?.let(::learnedAs) != null) setOf(Offer.FORGET) else emptySet()

    // the candidate [text] as the user committed it, if they did: as shown, or as at a sentence's start
    private fun learnedAs(text: String): String? =
        text.takeIf { user.count(it) > 0f } ?: text.replaceFirstChar(Char::lowercaseChar).takeIf { user.count(it) > 0f }

    private fun forget(index: Int): Snapshot {
        val word = candidates.getOrNull(index)?.let(::learnedAs) ?: return snapshot()
        user.forget(word)
        if (input.isEmpty()) candidates = predictions() else read()
        return snapshot()
    }

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
            // space ends the word with a space of the session's own, and punctuation with itself
            // and one; anything else is the app's
            if (c == ' ') return commit(word, space = true).also { if (word != typed) corrected = typed to last }
            if (c in SWAPPED) {
                learn(word, 1f)
                clear()
                return spaced("$word$c ", deleteBefore = 0)
            }
            clear()
            last = ""
            wrote(word + c)
            return snapshot(commit = word, handled = false)
        }
        return afterOwnSpace(c)
    }

    // [c] with no word typed: after the session's own space, it may take its place
    private fun afterOwnSpace(c: Char): Snapshot {
        // before the session's own space, if the cursor is still after it
        val before = if (last.length >= 2 && last.last() == ' ') last[last.length - 2] else null
        val word = before != null && before.isLetterOrDigit()
        val made = when {
            before == null -> null
            // twice after a word: a full stop
            c == ' ' && word -> ". "
            // after punctuation the space is there already
            c == ' ' && before in SWAPPED -> " "
            // in place of the space, and one after it: "word, " then "?" is "word? ", "?! " after "? "
            c in SWAPPED && (word || before in SWAPPED) -> "$c "
            else -> null
        }
        if (made == " ") {
            last = ""
            return snapshot()
        }
        if (made == null) {
            clear()
            last = ""
            wrote(c.toString())
            return snapshot(handled = false)
        }
        recent = recent.dropLast(1)
        return spaced(made, deleteBefore = 1)
    }

    // [made], ending with a space of the session's own, in place of the [deleteBefore] before it
    private fun spaced(made: String, deleteBefore: Int): Snapshot {
        last = made
        wrote(made)
        candidates = predictions()
        page = 0
        return snapshot(commit = made, deleteBefore = deleteBefore)
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
        // what was counted of the correction taken back, and the word typed the user's at once
        learn(committed.trimEnd(' '), -1f)
        learn(typed, EnglishUser.LEARNED)
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
        // the word typed picked over its correction: kept as typed, as by a correction taken back
        val kept = correction != null && text == typed()
        return commit(text, space = true, learned = if (kept) EnglishUser.LEARNED else 1f)
    }

    private fun commit(text: String, space: Boolean, learned: Float = 1f): Snapshot {
        val committed = if (space) "$text " else text
        learn(text, learned)
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

    // [word] committed: counted [count] times after the word before it, while learning
    private fun learn(word: String, count: Float) {
        if (count != 0f && learning && word.any { it.isLetter() }) user.learn(previous(), inSentence(word), count)
    }

    // the word before the cursor, if one is known
    private fun previous(): String? = before().lastOrNull()?.takeIf { it != EnglishText.START }

    /**
     * [word] as written inside a sentence, as the data has its words: a capital a sentence's start
     * accounts for is not the word's (Hello as hello), unless the word is written so anyway, as the
     * data has it (London, I) or the user typed it inside a sentence; a word in capitals as it is.
     */
    private fun inSentence(word: String): String {
        if (before().lastOrNull() != EnglishText.START || word.drop(1).any { it.isUpperCase() }) return word
        val lower = word.replaceFirstChar(Char::lowercaseChar)
        val data = data
        val capital = data != null && data.id(word) != NgramModel.NO_WORD && data.id(lower) == NgramModel.NO_WORD ||
            user.count(word) > 0f && user.count(lower) == 0f
        return if (capital) word else lower
    }

    // the user's words the data has, by id, and those it lacks, as of the user's [EnglishUser.changes]
    private var userIds: Set<Int> = emptySet()
    private var userOnly: List<String> = emptyList()
    private var userSeen = -1

    private fun refreshUser(data: EnglishData) {
        if (userSeen == user.changes) return
        userSeen = user.changes
        val ids = HashSet<Int>()
        val only = ArrayList<String>()
        for (word in user.words) {
            val id = data.id(word)
            if (id != NgramModel.NO_WORD) ids += id else if (data.id(word.replaceFirstChar(Char::lowercaseChar)) == NgramModel.NO_WORD) only += word
        }
        userIds = ids
        userOnly = only
    }

    // the word before, as of the last context scored: the same for every word a key's search scores
    private var scoredContext: Long? = null
    private var scoredPrev: String? = null

    // [word]'s score after [context], with the user's chance of it added
    private fun scored(data: EnglishData, context: Long, word: Int): Float {
        val base = data.model.scoreAfter(context, word)
        refreshUser(data)
        if (word !in userIds) return base
        if (context != scoredContext) {
            scoredContext = context
            scoredPrev = data.model.prevOf(context).takeIf { it != NgramModel.NO_WORD }?.let(data::word)
        }
        return user.score(base, scoredPrev, data.word(word))
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
        val theirs = learnedAs(typed)?.let(user::learned) == true
        correction = if (keepTyped || theirs || '-' in typed) null else correctionOf(typed, keys, found, context, name)?.let(::inCase)
        val data = data ?: return run { candidates = listOf(typed) }
        val words = (found.map { data.word(it.word) to it.score } + ownWords(data, keys, before))
            .sortedByDescending { it.second }
            .map { inCase(it.first) }
        candidates = (listOfNotNull(correction) + typed + words).distinct().take(SUGGESTED + 1)
    }

    // the words the user committed that the data lacks, which [keys] spell or start, scored by the user's counts alone
    private fun ownWords(data: EnglishData, keys: String, before: List<String>): List<Pair<String, Float>> {
        refreshUser(data)
        val prev = before.lastOrNull()?.takeIf { it != EnglishText.START }
        return userOnly.mapNotNull { word ->
            val key = EnglishData.key(word)
            val spelt = key == keys || keys.length >= costs.completeFrom && key.startsWith(keys)
            if (spelt) word to user.score(Float.NEGATIVE_INFINITY, prev, word) else null
        }
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
        val context = decoder.context(before)
        val prev = before.last().takeIf { !start }
        val model = decoder.predict(context, SUGGESTED + 1).map { data.word(it.word) to user.score(it.lm, prev, data.word(it.word)) }
        // what the user typed after the word before: theirs to type again
        val theirs = prev?.let(user::followers).orEmpty().map { word ->
            val id = data.id(word)
            word to user.score(if (id == NgramModel.NO_WORD) Float.NEGATIVE_INFINITY else data.model.scoreAfter(context, id), prev, word)
        }
        return (model + theirs).sortedByDescending { it.second }
            .map { it.first }
            .filter { it.first().isLetter() }
            .map { if (start) it.replaceFirstChar(Char::uppercaseChar) else it }
            .distinct()
            .take(SUGGESTED + 1)
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
