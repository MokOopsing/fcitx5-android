/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.dicttool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class ArpaModelTest {

    @Test
    fun numbersAreWrittenToSixSignificantDigitsAndWordsAsTheyAre() {
        val model = ArpaModel(
            listOf("<unk>", "%s", "好"),
            doubleArrayOf(-5.0, -1.23456789, -12.5),
            doubleArrayOf(0.0, -0.000012345678, 0.0),
            LongIndex().apply { add(NgramKey.of(1, 2)) },
            DoubleColumn().apply { this[0] = -0.3 },
            DoubleColumn().apply { this[0] = -0.1 },
            LongIndex().apply { add(NgramKey.of(1, 2, 1)) },
            DoubleColumn().apply { this[0] = -0.7 },
        )
        val expected = """
            \data\
            ngram 1=3
            ngram 2=1
            ngram 3=1

            \1-grams:
            -5.00000	<unk>	0.00000
            -1.23457	%s	-1.23457e-05
            -12.5000	好	0.00000

            \2-grams:
            -0.300000	%s 好	-0.100000

            \3-grams:
            -0.700000	%s 好 %s

            \end\
        """.trimIndent() + "\n"
        assertEquals(expected, StringBuilder().also { model.write(it) }.toString())
    }

    /** A Formatter keeps a failed write to itself: write throws it, so a model cut short cannot pass for whole. */
    @Test
    fun aWriteThatFailsIsThrown() {
        val model = ArpaModel(
            listOf("<unk>", "好"), doubleArrayOf(-5.0, -1.0), doubleArrayOf(0.0, 0.0),
            LongIndex(), DoubleColumn(), DoubleColumn(), LongIndex(), DoubleColumn(),
        )
        // fails on the word, which only the Formatter writes
        val full = object : Appendable {
            override fun append(csq: CharSequence?): Appendable = apply { if (csq?.contains('好') == true) throw IOException("disk full") }
            override fun append(csq: CharSequence?, start: Int, end: Int): Appendable = append(csq?.subSequence(start, end))
            override fun append(c: Char): Appendable = this
        }
        assertEquals("disk full", assertThrows(IOException::class.java) { model.write(full) }.message)
    }
}
