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
 * chosen from a key's long press) is in the input upper case, in place of its digit: read as the
 * digit, keeping only the syllables with that letter there (`7H` is on the way to shi or sha, not
 * si or pian; fuzzy partners of another spelling go too).
 *
 * A key pressed again soon after itself (连按) is in the input as [again] its digit, a press of
 * its own: the run of such presses is read both ways, as that many keys (`744` fast is shi, as
 * typed at any speed) and as the letter it came to, as a letter typed as itself (4 4 is h, 4 4 4
 * i, 2 2 2 2 a again), for the decoder to weigh. Only a run whole within a syllable is a letter.
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
                // üe is typed ue too (略 lue): on a phone u and v share 8, on the user's keys maybe not
                val typings = letters.typings(s) + if ("ve" in s) letters.typings(s.replace("ve", "ue")) else emptyList()
                for (keys in typings.distinct()) {
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
    override fun reads(c: Char) = c in '2'..'9' && letters.letters(c).isNotEmpty()

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
        while (at + length < input.length && isKey(input[at + length]) && length < MAX_PRESSES) {
            length++
            val read = Read()
            for ((typed, tapped) in readings(input, at, at + length)) read.add(typed, tapped, endsAt(input, at + length))
            if (read.regular) longest = length
            if (read.full.isNotEmpty()) edges.add(at, length, Kind.SYLLABLE, merged(read.full))
            if (read.started.isNotEmpty()) edges.add(at, length, if (read.full.isNotEmpty()) Kind.EXTENDED else Kind.PARTIAL, merged(read.started))
            if (read.initial.isNotEmpty()) initialEdges += length to merged(read.initial)
            // no syllable goes on from any reading: nor from a longer one, but for a run going on
            // past here, which a press more may make a letter
            if (!read.goesOn && !continues(input, at + length)) break
        }
        for ((n, m) in initialEdges) {
            // at the end the start of a syllable is read already, as PARTIAL
            if (endsAt(input, at + n)) continue
            if (longest == 0 || abbreviations && longest <= n) edges.add(at, n, Kind.INITIAL, m)
        }
    }

    /** What the readings of some presses are, whole syllables, the starts of some, initials: each reading [add]ed. */
    private inner class Read {
        val full = ArrayList<SyllableMatches>()
        val started = ArrayList<SyllableMatches>()
        val initial = ArrayList<SyllableMatches>()
        var regular = false
        var goesOn = false // whether some reading starts a syllable longer

        /** The reading [typed], [tapped] if a run is a letter in it, at the end of the input or of a syllable separated if [atEnd]. */
        fun add(typed: String, tapped: Boolean, atEnd: Boolean) {
            if (typed.length > MAX_SYLLABLE) return
            // a letter typed as itself as any key typing it: every way to type a syllable is indexed
            val keys = buildString(typed.length) { typed.forEach { append(letters.key(it.lowercaseChar())) } }
            fun read(m: SyllableMatches?) = m?.let { fitting(it, typed) }?.let { if (tapped) it.with(SyllableMatches.TAPPED) else it }
            read(whole[keys])?.let {
                full += it
                if (isRegular(keys, it)) regular = true
            }
            if (keys in starts) goesOn = true
            if (atEnd) read(starts[keys])?.let { started += it }
            read(initials[keys])?.let { initial += it }
        }
    }

    /**
     * Each way the presses of [input] from [at] to [end] read, as letters typed as themselves and
     * digits are: every key pressed again a digit, and each run of a key pressed again whole in
     * them the letter it came to, or not. A run going on before [at] or past [end] is keys alone.
     */
    private fun readings(input: String, at: Int, end: Int): List<Pair<String, Boolean>> {
        val plain = plain(input.subSequence(at, end))
        var readings = listOf(plain to false)
        var p = at
        while (p < end) {
            val digit = digitOf(input[p])
            var q = p + 1
            while (q < input.length && input[q] == again(digit)) q++
            val whole = q <= end && (p == 0 || !continues(input, p))
            if (whole && q - p > 1 && digit in '2'..'9') {
                val keyLetters = letters.letters(digit)
                if (keyLetters.isNotEmpty()) {
                    val letter = keyLetters[(q - p - 1) % keyLetters.length].uppercaseChar()
                    // the runs before this one read either way: this one too, from where it is in each
                    val from = p - at
                    readings = readings.flatMap { (r, tapped) ->
                        val start = r.length - (end - at - from)
                        listOf(r to tapped, r.substring(0, start) + letter + r.substring(start + q - p) to true)
                    }
                }
            }
            p = minOf(q, end)
        }
        return readings
    }

    // [input] at [p] is a key pressed again after the key before it
    private fun continues(input: String, p: Int) = p < input.length && input[p] in AGAIN && digitOf(input[p - 1]) == digitOf(input[p])

    /**
     * How many letters the presses [text] (of an edge) stand for, read as [syllable]: a run of a
     * key pressed again may be one. The presses where no reading fits the syllable.
     */
    fun lettersOf(text: String, syllable: Int): Int {
        val spelling = Syllables.spelling(syllable)
        return readings(text, 0, text.length).map { it.first }.firstOrNull { typed ->
            val keys = buildString(typed.length) { typed.forEach { append(letters.key(it.lowercaseChar())) } }
            spelling.length >= typed.length && listOfNotNull(whole[keys], starts[keys], initials[keys])
                .any { m -> fitting(m, typed)?.let { it.indexOf(syllable) >= 0 } == true }
        }?.length ?: text.length
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

    // [all] as one: each syllable with its best flags, sorted as SyllableMatches are searched
    private fun merged(all: List<SyllableMatches>): SyllableMatches {
        if (all.size == 1) return all[0]
        val best = TreeMap<Int, Int>()
        for (m in all) for (i in 0 until m.size) keepBest(best, m.syllable(i), m.flags(i))
        return toMatches(best)
    }

    companion object {
        private const val MAX_SYLLABLE = 6

        // presses of a syllable, the runs of a key pressed again in it letters each
        private const val MAX_PRESSES = 2 * MAX_SYLLABLE

        /** A letter typed as itself, as it stands in the input: see the class. */
        val FIXED = 'A'..'Z'

        private const val AGAIN_BASE = '\uE000'

        /** [digit] pressed again soon after itself (连按), as it stands in the input: see the class. */
        fun again(digit: Char): Char = AGAIN_BASE + (digit - '0')

        /** The keys pressed again, as they stand in the input. */
        val AGAIN = again('2')..again('9')

        /** The digit [c] is, a key pressed again or not; else [c]. */
        fun digitOf(c: Char): Char = if (c in AGAIN) '0' + (c - AGAIN_BASE) else c

        /** [text] with each key pressed again as its digit: as it is shown or committed. */
        fun plain(text: CharSequence): String = buildString(text.length) { text.forEach { append(digitOf(it)) } }

        /** Whether [c] is a key typed: a digit, pressed again or not, or a letter typed as itself. */
        fun isKey(c: Char): Boolean = c in '2'..'9' || c in FIXED || c in AGAIN
    }
}
