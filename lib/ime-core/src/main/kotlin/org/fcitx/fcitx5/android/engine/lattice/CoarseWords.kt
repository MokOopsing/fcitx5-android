/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.lattice

import java.util.BitSet

/**
 * Words no one wants offered unasked: sexual slang, porn's words, hard insults (the list
 * `coarse.txt` beside this class). The models learned them from the web, where they are common:
 * 小穴 was predicted after 妈妈的, and came before 小雪 for `xiaoxue`, 射精 first for `shejing`. They
 * are never predicted, and when typed they are [PENALTY] lower, below a word of the same reading as
 * common, still there to be typed; English never corrects to one.
 */
class CoarseWords private constructor(private val ids: BitSet) {

    /** Whether [word], an id of the vocabulary this was made for, is one of the list's. */
    fun has(word: Int): Boolean = word >= 0 && ids[word]

    companion object {
        /**
         * Added to a coarse word's score when typed, as [Penalties] are: some 30 times less
         * likely, which puts 小穴 below 小雪 and 射精 below 蛇精, while one with no word of its
         * reading near it (傻逼) still comes first.
         */
        const val PENALTY = -1.5f

        val NONE = CoarseWords(BitSet())

        /** The list, a word a line, `#` starting a comment. */
        val LIST: List<String> by lazy {
            val stream = requireNotNull(CoarseWords::class.java.getResourceAsStream("coarse.txt")) { "coarse.txt" }
            stream.bufferedReader().useLines { lines ->
                lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
            }
        }

        /** The list's words that [find] has an id for (none: -1). */
        fun of(find: (String) -> Int, words: List<String> = LIST): CoarseWords {
            val ids = BitSet()
            for (word in words) find(word).takeIf { it >= 0 }?.let { ids.set(it) }
            return CoarseWords(ids)
        }
    }
}
