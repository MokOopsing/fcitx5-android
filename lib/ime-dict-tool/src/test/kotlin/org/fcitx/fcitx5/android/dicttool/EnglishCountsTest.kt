/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.dicttool

import org.fcitx.fcitx5.android.engine.english.EnglishText
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishCountsTest {

    private val pages = listOf("I am here.", "I go there.", "The cat is here.", "You and I are here.")

    // nothing comes before <s>: no continuation count, so its bigrams count as seen, as KenLM has them
    @Test
    fun aSentencesFirstWordIsAsLikelyAsItIsSeenFirst() {
        val vocabulary = EnglishVocabulary()
        pages.forEach { page -> EnglishText.runs(page, vocabulary::add) }
        val words = vocabulary.words(100)
        val counts = EnglishCounts(words)
        pages.forEach { page -> EnglishText.runs(page, counts::add) }
        val model = KneserNey(counts)
        val start = words.id(EnglishText.START, first = false)
        val i = words.id("I", first = false)
        // first in two sentences of four: near half, far more than its share of what words follow
        assertTrue(model.p2(start, i) > 0.3)
        assertTrue(model.p2(start, i) > 2 * model.p1(i))
        assertTrue(model.p2(start, i) > model.p2(start, words.id("You", first = false)))
    }
}
