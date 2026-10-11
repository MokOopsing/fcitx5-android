/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.eval

import org.fcitx.fcitx5.android.engine.data.PinyinData
import org.fcitx.fcitx5.android.engine.data.PinyinDataBuilder
import org.fcitx.fcitx5.android.engine.pinyin.Syllables
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.util.Locale

class PredictRunTest {

    @Test
    fun anOfferIsAHitWhenWhatCameNextStartsWithIt() {
        val samples = listOf(Continuation("我", "们走吧"), Continuation("你", "好"), Continuation("他", "说"))
        val offers = listOf(listOf("们走", "们"), listOf("们", "", "x", "y", "z", "好"), emptyList())
        // the first: top1, 2 chars saved; the second: 好 is sixth, past what is shown; the empty offer never counts
        assertEquals(PredictRun.Score(3, 1, 1, 2), PredictRun.score(samples, offers))
    }

    @Test
    fun aSetIsContextTabNextWithComments() {
        assertEquals(
            listOf(Continuation("我先去", "走了"), Continuation("", "你好")),
            PredictRun.parse(sequenceOf("# header", "我先去\t走了", "", "\t你好")),
        )
        assertThrows(IllegalArgumentException::class.java) { PredictRun.parse(sequenceOf("no tab")) }
        assertThrows(IllegalArgumentException::class.java) { PredictRun.parse(sequenceOf("我\t")) }
        // the line as the file numbers it, its blank and comment lines counted
        val e = assertThrows(IllegalArgumentException::class.java) { PredictRun.parse(sequenceOf("# header", "", "no tab")) }
        assertTrue(e.message, e.message!!.startsWith("line 3:"))
    }

    @Test
    fun theModelsFollowersAreOfferedMostProbableFirst() {
        val data = PinyinData.load(
            ByteBuffer.wrap(
                PinyinDataBuilder()
                    .unigram("<unk>", -7f, 0f)
                    .unigram("你", -2f, 0f)
                    .unigram("好", -2.5f, 0f)
                    .unigram("们", -3f, 0f)
                    .bigram("你", "好", -0.5f, 0f)
                    .bigram("你", "们", -1f, 0f)
                    .entry("你", intArrayOf(Syllables.id("ni")))
                    .entry("好", intArrayOf(Syllables.id("hao")))
                    .entry("们", intArrayOf(Syllables.id("men")))
                    .build().toByteArray(),
            ),
        )
        val run = PredictRun(data)
        assertEquals(listOf("好", "们"), run.offers("你"))
        // nothing to go on from: no text, or text ending in what the model has no word for
        assertEquals(emptyList<String>(), run.offers(""))
        assertEquals(emptyList<String>(), run.offers("你 "))
    }

    @Test
    fun theScoreReadsTheSameWhateverTheLocale() {
        val format = Locale.getDefault(Locale.Category.FORMAT)
        try {
            // a German host wrote 0,75
            Locale.setDefault(Locale.Category.FORMAT, Locale.GERMANY)
            assertEquals("samples 4  top1 25.0%  top5 50.0%  chars saved per sample 0.75", PredictRun.Score(4, 1, 2, 3).toString())
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, format)
        }
        // no samples, no rates: not NaN
        assertEquals("samples 0  top1 -  top5 -  chars saved per sample -", PredictRun.Score(0, 0, 0, 0).toString())
    }
}
