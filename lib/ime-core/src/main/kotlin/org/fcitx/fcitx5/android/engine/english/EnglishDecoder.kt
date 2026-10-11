/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.english

import org.fcitx.fcitx5.android.engine.data.NgramModel
import org.fcitx.fcitx5.android.engine.pinyin.KeyNeighbours

/**
 * What typing a word's keys otherwise costs, in the model's log10: a key beside the one meant
 * ([near]) or any other ([far]), a key of the word not typed ([missed]), one typed it has not
 * ([extra]), two side by side the other way round ([swapped]); one of a doubled letter missed or
 * one doubled that is not, helo for hello, helllo ([repeated]). Past [limit] the keys are not the
 * word's. A word longer than the keys is offered as typed so far, at [complete] more, from keys
 * that cost no more than [completeLimit] to be its start (nothing short of [completeFrom] keys, for
 * which only the start they spell is any word's).
 */
data class TypingCosts(
    val near: Float = 1.0f,
    val far: Float = 3.0f,
    val missed: Float = 2.0f,
    val extra: Float = 2.0f,
    val swapped: Float = 1.5f,
    val repeated: Float = 1.0f,
    val limit: Float = 4.0f,
    val complete: Float = 0.0f,
    val completeLimit: Float = 1.5f,
    val completeFrom: Int = 3,
)

/**
 * The words of [data] for the keys typed: those they spell, those they start, and those a few
 * slips from them on the keyboard ([TypingCosts]), each scored by the model after the words before
 * it, less what typing it so cost.
 *
 * The words are searched as a trie of their keys, which [EnglishData]'s key order is: each prefix a
 * run of it, its children found by binary search. A row of edit costs (Damerau's, with the swap) is
 * carried down; a prefix past the limit ends the search there. A prefix the keys spell whole (at
 * no more than the completion limit, and more cheaply than its parent's) offers the words it
 * starts, and is searched no further: those words cost no less below it.
 */
class EnglishDecoder(private val data: EnglishData, private val costs: TypingCosts = TypingCosts()) {

    /**
     * A word offered: its id, its model score after the context, what typing it so cost, and
     * whether it is longer than the keys typed (a completion).
     */
    class Suggestion(val word: Int, val lm: Float, val cost: Float, val completes: Boolean) {
        val score: Float get() = lm - cost
    }

    /** The context of the next word after [words] (the nearest last): ids of the last two, a word the data lacks as none. */
    fun context(words: List<String>): Long {
        val ids = words.takeLast(2).map { data.id(it) }
        val prev = ids.lastOrNull() ?: NgramModel.NO_WORD
        // a word the data lacks starts afresh, as such a word broke the pages' runs
        val prev2 = if (ids.size == 2 && prev != NgramModel.NO_WORD) ids[0] else NgramModel.NO_WORD
        return data.model.context(prev2, prev)
    }

    /** The [limit] best words for [keys] (letters, lower case: [EnglishData.key]) after [context], best first. */
    fun suggest(keys: String, context: Long, limit: Int): List<Suggestion> {
        if (keys.isEmpty() || keys.length > MAX_KEYS) return emptyList()
        val found = Search(keys, context).run()
        return found.values.sortedWith(compareByDescending<Suggestion> { it.score }.thenBy { it.word }).take(limit)
    }

    /** The [limit] likeliest words after [context], best first: what may come next. */
    fun predict(context: Long, limit: Int): List<Suggestion> {
        val best = ArrayList<Suggestion>()
        data.model.forEachAfter(context) { word, score ->
            if (word == data.start || word == data.unknown) return@forEachAfter
            if (best.size == limit && score <= best.last().lm) return@forEachAfter
            val s = Suggestion(word, score, 0f, completes = true)
            val at = best.binarySearch { b -> if (b.lm > score) -1 else 1 }.let { -(it + 1) }
            best.add(at, s)
            if (best.size > limit) best.removeAt(best.size - 1)
        }
        return best
    }

    private inner class Search(private val keys: String, private val context: Long) {
        private val n = keys.length
        private val rows = Array(MAX_KEYS + n + 2) { FloatArray(n + 1) }
        private val path = CharArray(MAX_KEYS + n + 2)
        private val found = HashMap<Int, Suggestion>()
        private val completeLimit = if (n < costs.completeFrom) 0f else costs.completeLimit

        fun run(): Map<Int, Suggestion> {
            val first = rows[0]
            first[0] = 0f
            for (j in 1..n) first[j] = first[j - 1] + costs.extra
            visit(0, 0, data.keyed)
            return found
        }

        private fun visit(depth: Int, from: Int, to: Int) {
            val row = rows[depth]
            var pos = from
            // the words whose keys end here come first in the run
            while (pos < to && data.keyAt(pos).length == depth) {
                if (row[n] <= costs.limit) add(data.keyedWord(pos), row[n], completes = false)
                pos++
            }
            if (depth > 0 && completesHere(row, rows[depth - 1])) {
                complete(pos, to, row[n] + costs.complete)
                return
            }
            if (depth >= path.size - 1) return
            while (pos < to) {
                val c = data.keyAt(pos)[depth]
                val end = runEnd(pos, to, depth, c)
                if (step(depth, c)) visit(depth + 1, pos, end)
                pos = end
            }
        }

        // whether the keys spell the prefix well enough for its words to be offered: more cheaply than
        // its parent, and best here, not where they spell a shorter one fairly (se for see)
        private fun completesHere(row: FloatArray, parent: FloatArray): Boolean =
            row[n] <= completeLimit && parent[n] > row[n] && row[n] <= row.min()

        // the row for the prefix with [c] after it; false where every cost is past the limit
        private fun step(depth: Int, c: Char): Boolean {
            path[depth] = c
            val prev = rows[depth]
            val next = rows[depth + 1]
            // c after the same letter: missed, it is one of a doubled letter
            val missed = if (depth >= 1 && path[depth - 1] == c) costs.repeated else costs.missed
            next[0] = prev[0] + missed
            var least = next[0]
            for (j in 1..n) {
                val t = keys[j - 1]
                val extra = if (j >= 2 && keys[j - 2] == t) costs.repeated else costs.extra
                var v = prev[j - 1] + substitution(c, t)
                v = minOf(v, prev[j] + missed, next[j - 1] + extra)
                if (swapped(depth, j, c)) v = minOf(v, rows[depth - 1][j - 2] + costs.swapped)
                next[j] = v
                if (v < least) least = v
            }
            return least <= costs.limit
        }

        // the keys typed up to [j] end in the prefix's last two, [c] and the one before it, the other way round
        private fun swapped(depth: Int, j: Int, c: Char): Boolean =
            depth >= 1 && j >= 2 && c == keys[j - 2] && path[depth - 1] == keys[j - 1]

        private fun substitution(meant: Char, typed: Char): Float = when {
            meant == typed -> 0f
            typed in KeyNeighbours.of(meant) -> costs.near
            else -> costs.far
        }

        private fun complete(from: Int, to: Int, cost: Float) {
            // a long run (a key or two typed): its commoner words alone, as ids are by frequency
            val frequentOnly = to - from > MAX_SCAN
            for (pos in from until to) {
                val word = data.keyedWord(pos)
                if (frequentOnly && word >= FREQUENT) continue
                add(word, cost, completes = true)
            }
        }

        private fun add(word: Int, cost: Float, completes: Boolean) {
            val old = found[word]
            if (old != null && old.cost <= cost) return
            found[word] = Suggestion(word, data.model.scoreAfter(context, word), cost, completes)
        }

        // the end of the run from [from] whose keys have [c] at [depth]: the run is sorted, and every key in it is longer than depth
        private fun runEnd(from: Int, to: Int, depth: Int, c: Char): Int {
            var lo = from
            var hi = to
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (data.keyAt(mid)[depth] <= c) lo = mid + 1 else hi = mid
            }
            return lo
        }
    }

    companion object {
        /** Keys past this are no word the data has: nothing is searched for them. */
        const val MAX_KEYS = EnglishText.MAX_WORD

        // a run of completions longer than this is scored for its commoner words alone
        private const val MAX_SCAN = 4000

        // the words below this id: the commonest, as EnglishVocabulary numbers them
        private const val FREQUENT = 30_000
    }
}
