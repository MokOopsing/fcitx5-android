/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.english

import org.fcitx.fcitx5.android.engine.session.Action
import org.fcitx.fcitx5.android.engine.session.Choice
import org.fcitx.fcitx5.android.engine.session.Offer
import org.fcitx.fcitx5.android.engine.session.Session
import org.fcitx.fcitx5.android.engine.session.Snapshot

/**
 * English typed a word at a time, as Gboard has it. The word typed is the first candidate, what
 * space commits; after it the words it may be ([words]): longer ones it starts, then, where it is
 * no word, those one edit from it. A word committed by space or picked is followed by a space;
 * punctuation typed right after puts that space behind it (`word .` as `word. `), and space
 * typed again turns it into a full stop (`word  ` as `word. `). A lone i is I. Enter commits the
 * word as typed and is the app's all the same: a search runs at once. A tap in the word puts the
 * cursor there, where letters go in and backspace deletes.
 *
 * Keys it does not [read][reads] (digits, punctuation, space with nothing typed) end the word,
 * committed as typed, and are the app's. The host hands it every key (see the host's
 * `Keyboard`): space and digits pick nothing here. Where nothing is to be suggested (a password,
 * an editor asking for none, suggestions turned off) the addon hands it none: the keys type
 * themselves.
 */
class EnglishSession(
    private val words: EnglishWords?,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) : Session {

    private val input = StringBuilder()
    private var cursor = 0 // in input
    private var candidates: List<String> = emptyList()
    private var page = 0

    // what this session committed last, if the cursor stayed after it: a space it ends with is
    // the session's own, which the space rules may take back
    private var last = ""

    override var learning = true

    override fun reads(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || (c == '\'' || c == '-') && input.isNotEmpty()

    override fun apply(action: Action): Snapshot = when (action) {
        is Action.Key -> key(action.char)
        Action.Backspace -> backspace()
        is Action.Select -> if (action.index in 0 until pageSize) pick(page * pageSize + action.index) else snapshot(handled = input.isNotEmpty())
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
        Action.Reset, is Action.Context -> {
            clear()
            last = ""
            snapshot()
        }
        Action.Refine, is Action.Forget, is Action.Pin, is Action.Unpin, is Action.Block, is Action.Syllable, is Action.Letter -> snapshot()
    }

    override fun candidates(from: Int, count: Int): List<Choice> =
        candidates.subList(minOf(from, candidates.size), minOf(from + count, candidates.size)).map { Choice(it) }

    override fun offers(index: Int): Set<Offer> = emptySet()

    private fun key(c: Char): Snapshot {
        if (reads(c)) {
            input.insert(cursor++, c)
            read()
            return snapshot()
        }
        if (input.isNotEmpty()) {
            // space ends the word with a space of the session's own; anything else is the app's
            if (c == ' ') return commit(typed(), space = true)
            val word = typed()
            clear()
            last = ""
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
            last = ""
            return snapshot(handled = false)
        }
        last = made
        return snapshot(commit = made, deleteBefore = 1)
    }

    private fun backspace(): Snapshot {
        last = ""
        if (input.isEmpty()) return snapshot(handled = false)
        // at the start of the word: nothing before it is the session's to delete
        if (cursor == 0) return snapshot()
        input.deleteCharAt(--cursor)
        read()
        return snapshot()
    }

    private fun pick(index: Int): Snapshot {
        val text = candidates.getOrNull(index) ?: return snapshot(handled = input.isNotEmpty())
        return commit(text, space = true)
    }

    private fun commit(text: String, space: Boolean): Snapshot {
        val committed = if (space) "$text " else text
        clear()
        last = committed
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
    }

    // the word typed, then what it may be, in its case
    private fun read() {
        page = 0
        if (input.isEmpty()) {
            candidates = emptyList()
            return
        }
        val typed = typed()
        val lower = input.toString().lowercase()
        val dictionary = words
        val suggested = if (dictionary == null) {
            emptyList()
        } else {
            // a word still being typed is more likely the start of one than a slip (he for hel)
            val fixes = if (dictionary.weight(lower) < 0) dictionary.corrections(lower, SUGGESTED) else emptyList()
            dictionary.completions(lower, SUGGESTED) + fixes
        }
        candidates = (listOf(typed) + suggested.map { inCase(it) }).distinct().take(SUGGESTED + 1)
    }

    /** What was typed, as committed: i alone, and i before an apostrophe (i'm), as I. */
    private fun typed(): String {
        val s = input.toString()
        return if (s == "i" || s.startsWith("i'")) "I" + s.substring(1) else s
    }

    // [word], in lower case, as the input has its case: Hello for he, HELLO for HE; I' at a
    // sentence's start is I'm's, not I'M's
    private fun inCase(word: String): String = when {
        input.count { it.isLetter() } > 1 && input.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase()
        input[0].isUpperCase() -> word.replaceFirstChar { it.uppercaseChar() }
        else -> typedI(word)
    }

    // a suggestion of i'm and the like as I'm
    private fun typedI(word: String) = if (word == "i" || word.startsWith("i'")) "I" + word.substring(1) else word

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
            predicting = false,
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

        /** Punctuation that takes the place of the space after a word, a space after it. */
        private const val SWAPPED = ".,!?;:"
    }
}
