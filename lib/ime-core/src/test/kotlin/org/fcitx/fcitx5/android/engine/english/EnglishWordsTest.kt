/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.english

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class EnglishWordsTest {

    // a spell dictionary as fcitx's comp-spell-dict writes it
    private fun fscd(vararg words: Pair<String, Int>): ByteBuffer {
        val out = ByteArrayOutputStream()
        out.write("FSCD0000".toByteArray())
        val n = words.size
        out.write(byteArrayOf(n.toByte(), (n shr 8).toByte(), (n shr 16).toByte(), (n shr 24).toByte()))
        for ((word, weight) in words) {
            out.write(byteArrayOf(weight.toByte(), (weight shr 8).toByte()))
            out.write(word.toByteArray())
            out.write(0)
        }
        return ByteBuffer.wrap(out.toByteArray())
    }

    @Test
    fun aSpellDictionaryReadsAsWordsWithTheirWeights() {
        val words = EnglishWords.load(fscd("the" to 65534, "hello" to 64138, "help" to 60000, "Hel" to 100))
        assertEquals(4, words.size)
        assertEquals(64138, words.weight("hello"))
        // in lower case
        assertEquals(100, words.weight("hel"))
        assertEquals(-1, words.weight("nope"))
        // not one at all: none
        assertEquals(0, EnglishWords.load(ByteBuffer.wrap("nothing".toByteArray())).size)
    }

    @Test
    fun completionsAreTheLongerWordsAPrefixStartsCommonestFirst() {
        val words = EnglishWords.of(mapOf("he" to 9, "hello" to 5, "help" to 7, "helper" to 1, "hat" to 8))
        assertEquals(listOf("help", "hello", "helper"), words.completions("hel", 5))
        assertEquals(listOf("help"), words.completions("hel", 1))
        assertEquals(listOf("hello", "help", "helper"), words.completions("he", 5).sorted())
        assertEquals(emptyList<String>(), words.completions("", 5))
    }

    @Test
    fun correctionsAreWordsOneEditAwayCommonestFirst() {
        val words = EnglishWords.of(mapOf("the" to 9, "then" to 5, "ten" to 4, "teh" to 1, "hte" to 2))
        // swapped, left out, one more, one other
        assertEquals(listOf("the", "ten"), words.corrections("teh", 5))
        assertEquals(listOf("hte"), words.corrections("htx", 5))
        assertEquals(listOf("then", "ten", "teh"), words.corrections("tehn", 5))
        assertEquals(listOf("the"), words.corrections("th", 5).take(1))
    }
}
