/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.dicttool

import org.fcitx.fcitx5.android.engine.data.PinyinData
import org.fcitx.fcitx5.android.engine.data.PinyinDataBuilder
import org.fcitx.fcitx5.android.engine.pinyin.Syllables
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer

class ReadingsTest {

    /** Data of the model's [scores] and a dictionary of [entries], `word pin'yin [weight]` each. */
    private fun readings(scores: Map<String, Float>, vararg entries: String): Readings {
        val builder = PinyinDataBuilder().unigram("<unk>", -7f, 0f)
        for ((word, score) in scores) builder.unigram(word, score, 0f)
        for (entry in entries) {
            val f = entry.split(' ')
            builder.entry(f[0], f[1].split('\'').map { Syllables.id(it) }.toIntArray(), f.getOrNull(2)?.toFloat() ?: 0f)
        }
        return Readings(PinyinData.load(ByteBuffer.wrap(builder.build().toByteArray())))
    }

    @Test
    fun aRunIsReadAsTheFewestWordsOfTheDictionaryInIt() {
        // 长 alone is likelier chang, in 长大 zhang; 阿谁's two readings weigh the same, and 谁 alone is shui
        val r = readings(emptyMap(), "长 chang 0", "长 zhang -1", "大 da", "长大 zhang'da", "阿 a", "谁 shui", "阿谁 a'shei", "阿谁 a'shui")
        assertEquals("zhang'da'shui", r.of("长大谁"))
        assertEquals("a'shui", r.of("阿谁"))
        assertEquals("chang'shui", r.of("长谁"))
        // a character the dictionary has no reading of
        assertNull(r.of("长嗯"))
    }

    @Test
    fun betweenCutsOfAsManyWordsTheLikelierReadingsThenTheCommonerWordsWin() {
        val words = arrayOf("成 cheng", "长 chang", "长 zhang -1", "短 duan", "成长 cheng'zhang", "长短 chang'duan")
        val scores = mapOf("成" to -3f, "短" to -3f)
        // 成长 短 or 成 长短: two words either way, of readings as likely
        assertEquals("cheng'zhang'duan", readings(scores + mapOf("成长" to -2f, "长短" to -4f), *words).of("成长短"))
        assertEquals("cheng'chang'duan", readings(scores + mapOf("成长" to -4f, "长短" to -2f), *words).of("成长短"))
        // 长短 now a rarer reading of it: the weight goes before the model's word scores
        val rarer = words.map { if (it.startsWith("长短")) "$it -1" else it }.toTypedArray()
        assertEquals("cheng'zhang'duan", readings(scores + mapOf("成长" to -4f, "长短" to -2f), *rarer).of("成长短"))
    }
}
