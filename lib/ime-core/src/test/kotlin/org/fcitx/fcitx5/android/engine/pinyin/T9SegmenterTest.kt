/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.pinyin

import org.fcitx.fcitx5.android.engine.pinyin.SyllableGraph.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9SegmenterTest {

    // as the keyboard builds it; ime-eval's t9 scheme reads 简拼 with abbreviations too
    private val segmenter = T9Segmenter(abbreviations = false)

    /** The syllables of the edges from [at] to [to] of [kind], spelt. */
    private fun SyllableGraph.read(at: Int, to: Int, kind: Kind = Kind.SYLLABLE): Set<String> =
        edges(at).filter { to(it) == to && kind(it) == kind }
            .flatMap { e -> (0 until matches(e).size).map { Syllables.spelling(matches(e).syllable(it)) } }.toSet()

    /** As [read], each marked with its flags as [SyllableMatches] prints them: `zhong~`. */
    private fun SyllableGraph.marked(at: Int, to: Int, kind: Kind = Kind.SYLLABLE): Set<String> =
        edges(at).filter { to(it) == to && kind(it) == kind }.flatMap { matches(it).toString().split(' ') }.toSet()

    @Test
    fun digitsAreEverySyllableTheyType() {
        assertEquals("64426", T9Segmenter.digits("nihao"))
        assertEquals("58", T9Segmenter.digits("lv"))
        assertEquals("64'", T9Segmenter.digits("ni'"))
        // every letter on one key, the one that types it
        assertEquals(('a'..'z').joinToString(""), ('2'..'9').joinToString("") { T9Segmenter.letters(it) })
        for (d in '2'..'9') T9Segmenter.letters(d).forEach { assertEquals(d, T9Segmenter.digit(it)) }
        val g = segmenter.segment("64426")
        assertTrue(setOf("ni", "mi").all { it in g.read(0, 2) })
        assertTrue("hao" in g.read(2, 5))
        assertTrue("o" in g.read(0, 1))
        // the last digits on their way to a longer syllable
        assertTrue(setOf("nian", "ming").all { it in segmenter.segment("64").read(0, 2, Kind.EXTENDED) })
    }

    @Test
    fun anInitialAloneOnlyWhereNothingElseIsReadUnlessAbbreviationsAre() {
        // 44: no syllable is g or h then g, h or i, so the 4 is an initial
        val strict = T9Segmenter(abbreviations = false).segment("4426")
        assertTrue(setOf("ga", "ha").all { it in strict.read(0, 1, Kind.INITIAL) })
        // 呣 and 嗯 (6) and 儿 (7) keep no initial off: 79 is q, s … then w, x …
        assertTrue("qu" in T9Segmenter(abbreviations = false).segment("7948").read(0, 1, Kind.INITIAL))
        // 64 reads ni: no initial n there
        assertTrue(T9Segmenter(abbreviations = false).segment("6426").read(0, 1, Kind.INITIAL).isEmpty())
        // with them, beside a syllable no longer than the initial too: 74 is si, and sh in 745 时间
        val abbreviating = T9Segmenter()
        assertTrue("si" in abbreviating.segment("745").read(0, 2))
        assertTrue("shi" in abbreviating.segment("745").read(0, 2, Kind.INITIAL))
        assertTrue(segmenter.segment("745").read(0, 2, Kind.INITIAL).isEmpty())
        // but not beside a longer one (744 is shi, 6426 nian), nor at the end, where its start is read
        assertTrue(abbreviating.segment("7448").read(0, 2, Kind.INITIAL).isEmpty())
        assertTrue(abbreviating.segment("6426").read(0, 1, Kind.INITIAL).isEmpty())
        assertTrue(abbreviating.segment("74").read(0, 2, Kind.INITIAL).isEmpty())
    }

    @Test
    fun aFuzzyPairReadsTheKeysOfTheOtherSpelling() {
        val fuzzy = T9Segmenter(setOf(Fuzzy.Z_ZH, Fuzzy.L_N, Fuzzy.IN_ING), abbreviations = false)
        // z is 9 and zh 94: each reads the other's syllables, as an initial too
        assertTrue("zhong~" in fuzzy.segment("9664").marked(0, 4))
        assertTrue("zong~" in fuzzy.segment("94664").marked(0, 5))
        assertTrue("zhong~…" in fuzzy.segment("95").marked(0, 1, Kind.INITIAL))
        // l is 5 and n 6
        assertTrue("nan~" in fuzzy.segment("526").marked(0, 3))
        assertTrue("lan~…" in fuzzy.segment("65").marked(0, 1, Kind.INITIAL))
        // and a final pair: 546 is jing too
        assertTrue("jing~" in fuzzy.segment("546").marked(0, 3))
        // none of it without the pairs
        assertFalse("zhong" in segmenter.segment("9664").read(0, 4))
        assertFalse("lan" in segmenter.segment("65").read(0, 1, Kind.INITIAL))
    }

    @Test
    fun aSyllableOfOneLetterKeepsNoInitialOffItsDigitNorReadsWhatItStartsAsExtended() {
        val strict = T9Segmenter(abbreviations = false)
        // 2 is 啊 and 6 哦 too: b and n stand for their syllables all the same, 北京 and 南京
        assertTrue("bei" in strict.segment("25").read(0, 1, Kind.INITIAL))
        assertTrue("nan" in strict.segment("65").read(0, 1, Kind.INITIAL))
        assertTrue("a" in strict.segment("25").read(0, 1))
        // typed last, the start of a syllable still being typed, not a whole one read on
        val six = segmenter.segment("6")
        assertTrue(setOf("men", "ni", "ou").all { it in six.read(0, 1, Kind.PARTIAL) })
        assertTrue(six.read(0, 1, Kind.EXTENDED).isEmpty())
        assertTrue("ba" in segmenter.segment("2").read(0, 1, Kind.PARTIAL))
    }

    @Test
    fun uAloneIsNoSpellingOfOuUnderTheFuzzyPair() {
        val ou = T9Segmenter(setOf(Fuzzy.U_OU), abbreviations = false)
        // 8 types no syllable, so its t still stands for its syllables: 86 他们
        val g = ou.segment("86")
        assertTrue(g.read(0, 1).isEmpty())
        assertTrue("ta" in g.read(0, 1, Kind.INITIAL))
        // after an initial the pair holds: 68 is mou too
        assertTrue(setOf("mu", "mou").all { it in ou.segment("68").read(0, 2) })
    }

    @Test
    fun aLetterTypedAsItselfKeepsTheSyllablesWithItThere() {
        // 7 then 4 4 fast: s, p, q or r, then h
        val sh = segmenter.segment("7H")
        assertTrue(sh.read(0, 2).isEmpty())
        val starts = sh.read(0, 2, Kind.EXTENDED) + sh.read(0, 2, Kind.PARTIAL)
        assertTrue(setOf("shi", "sha", "shang").all { it in starts })
        assertTrue(starts.none { it[1] != 'h' })
        // the digit before it still any of its letters, the one after too
        assertEquals(setOf("ni", "ng"), segmenter.segment("N4").read(0, 2))
        assertEquals(setOf("ni", "mi"), segmenter.segment("6I").read(0, 2))
        assertTrue("hao" in segmenter.segment("6IH26").read(2, 5))
        assertTrue(segmenter.segment("6IG26").read(2, 5).none { it == "hao" })
        // at the end with no syllable whole left, the starts are only on their way (PARTIAL)
        assertTrue(sh.read(0, 2, Kind.EXTENDED).isEmpty())
        // üe typed lue as well as lve: 略
        assertTrue("lve" in segmenter.segment("5U3").read(0, 3))
        assertTrue("lve" in segmenter.segment("5V3").read(0, 3))
        // a letter no syllable has there leaves nothing to read
        assertTrue(segmenter.segment("6K").read(0, 2).isEmpty())
        assertTrue(T9Segmenter.isKey('H') && T9Segmenter.isKey('2') && !T9Segmenter.isKey('h') && !T9Segmenter.isKey('1'))
    }

    @Test
    fun aSyllableTakenIsReadAsItselfAlone() {
        val g = segmenter.segment("ni'426")
        assertEquals(setOf("ni"), g.read(0, 3))
        assertTrue("hao" in g.read(3, 6))
        // a letter taken is an initial, m too, which is 呣 alone
        assertTrue("hao" in segmenter.segment("h'26").read(0, 2, Kind.INITIAL))
        assertTrue("ma" in segmenter.segment("m'2").read(0, 2, Kind.INITIAL))
        assertTrue(segmenter.segment("m'2").read(0, 2).isEmpty())
        assertFalse(segmenter.reads('a'))
        assertFalse(segmenter.typesLetters)
    }
}
