/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.eval

import org.fcitx.fcitx5.android.engine.data.PinyinData
import org.fcitx.fcitx5.android.engine.lattice.Penalties
import org.fcitx.fcitx5.android.engine.lattice.PinyinDecoder
import org.fcitx.fcitx5.android.engine.lattice.Predictor
import org.fcitx.fcitx5.android.engine.lattice.WordScorer
import org.fcitx.fcitx5.android.engine.pinyin.PinyinSegmenter
import org.fcitx.fcitx5.android.engine.pinyin.Syllables
import java.util.Locale

/**
 * Every character of the dictionary typed alone by each of its readings, in full pinyin, with no
 * context: where it shows among the candidates, the decoder's sentences and then its words. Until
 * 2026-10-10 the words were ranked by score alone and cut at [PinyinDecoder.DEFAULT_WORDS]: a
 * character rare under its reading whose reading starts with common ones (觉 jiao, after 几 家 加 of
 * ji and jia) was not listed at all, 39408 of 50974 so. Run on an older :lib:ime-core to compare.
 */
class CharRanks(private val data: PinyinData) {

    /** How many readings of characters show at a rank of at most each of [BOUNDS], and past the last; [missed] the commonest not listed. */
    class Ranks(val within: IntArray, val past: Int, val missing: Int, val missed: List<String>) {
        val total get() = within.last() + past + missing
    }

    private val decoder = PinyinDecoder(data.dictionary, data.vocabulary, WordScorer.of(data.model), Penalties())
    private val segmenter = PinyinSegmenter(typos = false)

    fun measure(): Ranks {
        val counter = Counter()
        // a decode per reading, for all its characters
        val byReading = HashMap<Int, MutableList<String>>()
        for ((char, readings) in Predictor.charReadingsOf(data.dictionary, data.vocabulary)) {
            for (r in readings.distinct()) byReading.getOrPut(r) { ArrayList() } += String(Character.toChars(char))
        }
        for ((reading, chars) in byReading) {
            val decoding = decoder.decode(segmenter.segment(Syllables.spelling(reading).replace('ü', 'v')), words = Int.MAX_VALUE)
            val listed = (decoding.sentences.take(PinyinDecoder.DEFAULT_SENTENCES) + decoding.words).map { it.text }.distinct()
            val score = decoding.words.associate { it.text to it.score }
            for (char in chars) counter.count(char, listed.indexOf(char), score[char] ?: Float.NEGATIVE_INFINITY)
        }
        return counter.ranks()
    }

    private class Counter {
        val within = IntArray(BOUNDS.size)
        var past = 0
        var missing = 0
        val missed = ArrayList<Pair<String, Float>>()

        fun count(char: String, rank: Int, score: Float) {
            if (rank < 0) {
                missing++
                missed += char to score
                return
            }
            val bound = BOUNDS.indexOfFirst { rank < it }
            if (bound < 0) past++ else for (b in bound until BOUNDS.size) within[b]++
        }

        // the commonest first, as the decoder scores them
        fun ranks() = Ranks(within, past, missing, missed.sortedByDescending { it.second }.take(MISSED_SHOWN).map { it.first })
    }

    companion object {
        /** The ranks counted to: the first page, two, four, and where the words were cut. */
        val BOUNDS = intArrayOf(5, 10, 20, PinyinDecoder.DEFAULT_WORDS)
        const val MISSED_SHOWN = 20

        /** [ranks] as a table. */
        fun report(ranks: Ranks, out: Appendable) {
            out.appendLine("characters by reading typed alone: ${ranks.total}")
            val bounds = BOUNDS.joinToString("  ") { String.format(Locale.ROOT, "%8s", "top$it") }
            out.appendLine(String.format(Locale.ROOT, "%s  %8s  %8s", bounds, "past", "missing"))
            val within = ranks.within.joinToString("  ") { String.format(Locale.ROOT, "%8d", it) }
            out.appendLine(String.format(Locale.ROOT, "%s  %8d  %8d", within, ranks.past, ranks.missing))
            if (ranks.missed.isNotEmpty()) out.appendLine("missing: ${ranks.missed.joinToString(" ")}")
        }
    }
}
