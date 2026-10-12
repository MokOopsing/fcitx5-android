/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.pinyin

import org.fcitx.fcitx5.android.engine.pinyin.SpellingIndex.Companion.keepBest
import org.fcitx.fcitx5.android.engine.pinyin.SpellingIndex.Companion.toMatches
import org.fcitx.fcitx5.android.engine.pinyin.SyllableGraph.Companion.SEPARATOR
import org.fcitx.fcitx5.android.engine.pinyin.SyllableGraph.Kind
import java.util.TreeMap

/**
 * Cuts pinyin typed on a phone's nine keys (九键) into a [SyllableGraph]: each of 2 to 9 stands
 * for its [letters] (on a phone `2` abc … `9` wxyz, ü as v on `8`), so `64426` is ni'hao, mi'gao
 * and the rest, all of them in the graph for the decoder to weigh. A letter the user put on more
 * than one key is read from each. The separator is `'`: the session takes
 * the keyboard's 1 for it.
 *
 * From each digit the graph gets:
 * - every syllable the digits there type, of any length, with their [Fuzzy] partners;
 * - at the end of the input or before a separator, every syllable the digits may still become
 *   ([Kind.PARTIAL], or [Kind.EXTENDED] where they are a syllable too, as on the full keyboard:
 *   `64` on the way to nian, `2`, 啊, on the way to bu, so a lone 2 reads a first);
 * - where another digit follows, the initials the digits type (简拼, `94` zh): with
 *   [abbreviations] where no longer syllable is read there (sh in `745` 时间, not in `744`, shi),
 *   without only where nothing else is read but a syllable of one letter (`25` is 北京 too, not
 *   only 啊 then j …).
 *
 * A letter typed as itself ([Action.Letter][org.fcitx.fcitx5.android.engine.session.Action.Letter],
 * 连按) is in the input upper case, in place of its digit: read as the digit, keeping only the
 * syllables with that letter there (`7H`, 7 then 4 4 fast, is on the way to shi or sha, not si
 * or pian; fuzzy partners of another spelling go too).
 *
 * A syllable the user chose for the first digits not yet chosen
 * ([Action.Syllable][org.fcitx.fcitx5.android.engine.session.Action.Syllable]) is in the input as
 * its letters and a separator: read as that syllable alone, or as an initial for a letter (`h`).
 */
class T9Segmenter(
    fuzzy: Set<Fuzzy> = emptySet(),
    private val abbreviations: Boolean = true,
    val letters: NineKeyLetters = NineKeyLetters.STANDARD,
) : Segmenter {

    // digits typing a syllable whole, the start of one, and an initial
    private val whole = HashMap<String, SyllableMatches>()
    private val starts = HashMap<String, SyllableMatches>()
    private val initials = HashMap<String, SyllableMatches>()

    // a chosen letter, as an initial: its syllables
    private val byInitial = HashMap<String, SyllableMatches>()

    // digits typing a syllable of an initial and a final, or a final alone, of two letters or more:
    // 啊, 呃, 哦, 呣, 嗯 and 儿 alone would keep 简拼 off every 2, 3, 6 and 7
    private val regular = HashSet<String>()

    init {
        val wholeIds = HashMap<String, TreeMap<Int, Int>>()
        val startIds = HashMap<String, TreeMap<Int, Int>>()
        val initialIds = HashMap<String, TreeMap<Int, Int>>()
        val letterIds = HashMap<String, TreeMap<Int, Int>>()
        val initialRules = fuzzy.filter { it.onInitial }
        for (id in 0 until Syllables.count) {
            val spelling = Syllables.spelling(id)
            if (spelling[0].isUpperCase()) continue
            val parts = SpellingIndex.split(spelling)
            val spellings = if (parts == null) {
                listOf(spelling to 0)
            } else {
                val (init, fin) = parts
                val finalRules = fuzzy.filter { !it.onInitial && it.appliesAfter(init) }
                if (init.isNotEmpty()) keepBest(letterIds.getOrPut(init) { TreeMap() }, id, SyllableMatches.COMPLETION)
                SpellingIndex.variants(init, initialRules).flatMap { (i, iFlags) ->
                    if (i.isNotEmpty()) {
                        for (keys in letters.typings(i)) keepBest(initialIds.getOrPut(keys) { TreeMap() }, id, iFlags or SyllableMatches.COMPLETION)
                    }
                    // with no initial the final is the syllable: ou's partner u is none, as in 双拼
                    SpellingIndex.finalsAfter(i, fin, finalRules).map { (f, fFlags) -> i + f to (iFlags or fFlags) }
                }
            }
            for ((s, flags) in spellings) {
                for (keys in letters.typings(s)) {
                    if (parts != null && s.length > 1) regular += keys
                    keepBest(wholeIds.getOrPut(keys) { TreeMap() }, id, flags)
                    for (n in 1 until keys.length) keepBest(startIds.getOrPut(keys.substring(0, n)) { TreeMap() }, id, flags or SyllableMatches.COMPLETION)
                }
            }
        }
        wholeIds.forEach { (k, m) -> whole[k] = toMatches(m) }
        startIds.forEach { (k, m) -> starts[k] = toMatches(m) }
        initialIds.forEach { (k, m) -> initials[k] = toMatches(m) }
        letterIds.forEach { (k, m) -> byInitial[k] = toMatches(m) }
    }

    // the letters of a syllable taken are the session's to put in, not typed
    override fun reads(c: Char) = c in '2'..'9'

    override val typesLetters: Boolean get() = false

    override fun segment(input: String): SyllableGraph = GraphEdges.build(input) { at, edges ->
        if (input[at] in 'a'..'z') chosen(input, at, edges) else walk(input, at, edges)
    }

    /** The syllable, or initial, chosen at [at]: its letters up to the separator. */
    private fun chosen(input: String, at: Int, edges: GraphEdges) {
        var end = at
        while (end < input.length && input[end] in 'a'..'z') end++
        val text = input.substring(at, end)
        val id = Syllables.id(text)
        when {
            // a letter is taken as the initial it is offered as: m for ma, mi …, not 呣
            text.length == 1 && text in byInitial -> edges.add(at, 1, Kind.INITIAL, byInitial.getValue(text))
            id >= 0 -> edges.add(at, end - at, Kind.SYLLABLE, SyllableMatches(intArrayOf(id), intArrayOf(0)))
        }
    }

    private fun walk(input: String, at: Int, edges: GraphEdges) {
        var longest = 0
        var length = 0
        val initialEdges = ArrayList<Pair<Int, SyllableMatches>>()
        while (at + length < input.length && isKey(input[at + length]) && length < MAX_SYLLABLE) {
            length++
            val typed = input.substring(at, at + length)
            // a letter typed as itself as any key typing it: every way to type a syllable is indexed
            val keys = buildString(length) { typed.forEach { append(letters.key(it.lowercaseChar())) } }
            val full = whole[keys]?.let { fitting(it, typed) }
            val isRegular = isRegular(keys, full)
            if (full != null) {
                edges.add(at, length, Kind.SYLLABLE, full)
                if (isRegular) longest = length
            }
            if (endsAt(input, at + length)) {
                starts[keys]?.let { fitting(it, typed) }?.let { edges.add(at, length, if (full != null) Kind.EXTENDED else Kind.PARTIAL, it) }
            }
            initials[keys]?.let { fitting(it, typed) }?.let { initialEdges += length to it }
        }
        for ((n, m) in initialEdges) {
            // at the end the start of a syllable is read already, as PARTIAL
            if (endsAt(input, at + n)) continue
            if (longest == 0 || abbreviations && longest <= n) edges.add(at, n, Kind.INITIAL, m)
        }
    }

    // [spelling] has [c] at [k]; üe is spelt lve here and typed lue as well (略, 虐)
    private fun spells(spelling: String, k: Int, c: Char) =
        spelling.getOrNull(k) == c || c == 'u' && spelling.getOrNull(k) == 'v' && spelling.getOrNull(k + 1) == 'e'

    // the end of the input, or of a syllable the user separated
    private fun endsAt(input: String, at: Int) = at == input.length || input[at] == SEPARATOR

    // a regular syllable read for [keys] as [full], what a letter typed as itself left of them
    private fun isRegular(keys: String, full: SyllableMatches?): Boolean = when {
        keys !in regular || full == null -> false
        full === whole[keys] -> true
        // as [regular] has digits: of an initial and a final, or a final alone, of two letters or more
        else -> (0 until full.size).any { Syllables.spelling(full.syllable(it)).let { s -> s.length > 1 && SpellingIndex.split(s) != null } }
    }

    /** Those of [matches] spelt with each letter typed as itself in [typed] where it is; null if none. */
    private fun fitting(matches: SyllableMatches, typed: String): SyllableMatches? {
        if (typed.none { it in FIXED }) return matches
        val kept = (0 until matches.size).filter { i ->
            val spelling = Syllables.spelling(matches.syllable(i))
            typed.indices.all { k -> typed[k] !in FIXED || spells(spelling, k, typed[k].lowercaseChar()) }
        }
        if (kept.isEmpty()) return null
        return SyllableMatches(IntArray(kept.size) { matches.syllable(kept[it]) }, IntArray(kept.size) { matches.flags(kept[it]) })
    }

    companion object {
        private const val MAX_SYLLABLE = 6

        /** A letter typed as itself, as it stands in the input: see the class. */
        val FIXED = 'A'..'Z'

        /** Whether [c] is a key typed: a digit, or a letter typed as itself. */
        fun isKey(c: Char): Boolean = c in '2'..'9' || c in FIXED
    }
}
