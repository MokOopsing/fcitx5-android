/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.english

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * English words in lower case, each with a weight, the more common the higher: what [EnglishSession]
 * suggests from. Read from fcitx's spell dictionary (`en_dict.fscd`) until a list of our own:
 * `FSCD0000`, the count, then per word a little-endian 16-bit weight and the word, 0-ended.
 */
class EnglishWords private constructor(private val words: Array<String>, private val weights: IntArray) {

    val size: Int get() = words.size

    /** [word]'s weight, -1 if it is none. */
    fun weight(word: String): Int = words.binarySearch(word).let { if (it >= 0) weights[it] else -1 }

    /** At most [limit] words longer than [prefix] that start with it, the commonest first. */
    fun completions(prefix: String, limit: Int): List<String> {
        if (prefix.isEmpty()) return emptyList()
        var at = words.binarySearch(prefix).let { if (it >= 0) it + 1 else -it - 1 }
        val found = ArrayList<Int>()
        while (at < words.size && words[at].startsWith(prefix)) found += at++
        return found.sortedByDescending { weights[it] }.take(limit).map { words[it] }
    }

    /**
     * At most [limit] words one edit from [word] (a letter left out, one more, one other, two
     * swapped), the commonest first: what was likely meant by a word that is none.
     */
    fun corrections(word: String, limit: Int): List<String> {
        val edits = HashSet<String>()
        for (i in 0..word.length) {
            if (i < word.length) edits += word.removeRange(i, i + 1)
            if (i + 1 < word.length) edits += word.substring(0, i) + word[i + 1] + word[i] + word.substring(i + 2)
            for (c in 'a'..'z') {
                edits += word.substring(0, i) + c + word.substring(i)
                if (i < word.length) edits += word.substring(0, i) + c + word.substring(i + 1)
            }
        }
        edits -= word
        return edits.filter { weight(it) >= 0 }.sortedByDescending(::weight).take(limit)
    }

    companion object {
        private const val MAGIC = "FSCD0000"

        /** The words of a spell dictionary; empty if [data] is none. */
        fun load(data: ByteBuffer): EnglishWords {
            val b = data.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(MAGIC.length)
            if (b.remaining() < magic.size + Int.SIZE_BYTES) return of(emptyMap())
            b.get(magic)
            if (String(magic, Charsets.US_ASCII) != MAGIC) return of(emptyMap())
            val count = b.int
            val read = HashMap<String, Int>(count * 2)
            val bytes = ByteArray(Byte.MAX_VALUE.toInt() * 2)
            while (read.size < count && b.remaining() > Short.SIZE_BYTES) {
                val weight = b.short.toInt() and 0xffff
                var n = 0
                while (b.hasRemaining()) {
                    val c = b.get()
                    if (c.toInt() == 0) break
                    if (n < bytes.size) bytes[n++] = c
                }
                if (n > 0) read.merge(String(bytes, 0, n, Charsets.UTF_8).lowercase(), weight, ::maxOf)
            }
            return of(read)
        }

        /** [weights] by word, the words in lower case: for tests, and for [load]. */
        fun of(weights: Map<String, Int>): EnglishWords {
            val sorted = weights.keys.sorted().toTypedArray()
            return EnglishWords(sorted, IntArray(sorted.size) { weights.getValue(sorted[it]) })
        }
    }
}
