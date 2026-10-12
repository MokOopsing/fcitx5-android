/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.session

import org.fcitx.fcitx5.android.engine.data.PinyinData
import org.fcitx.fcitx5.android.engine.data.PinyinDataBuilder
import org.fcitx.fcitx5.android.engine.pinyin.PinyinSegmenter
import org.fcitx.fcitx5.android.engine.pinyin.Syllables
import org.fcitx.fcitx5.android.engine.session.Action.Key
import org.fcitx.fcitx5.android.engine.user.UserModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

/** 小穴 came first for xiaoxue after 妈妈的, its fourth at a lower score still too high. */
class CoarseCandidatesTest {

    private fun syl(vararg s: String) = s.map { Syllables.id(it) }.toIntArray()

    private val data = PinyinData.load(
        ByteBuffer.wrap(
            PinyinDataBuilder()
                .unigram("<unk>", -7f, 0f)
                // the model's likeliest of the reading, as the web taught it
                .unigram("小穴", -1f, 0f)
                .unigram("小学", -3f, 0f)
                .unigram("小雪", -3.5f, 0f)
                .unigram("小", -2f, 0f)
                .unigram("学", -3f, 0f)
                .unigram("穴", -1f, 0f)
                .entry("小穴", syl("xiao", "xue"))
                .entry("小学", syl("xiao", "xue"))
                .entry("小雪", syl("xiao", "xue"))
                .entry("小", syl("xiao"))
                .entry("学", syl("xue"))
                .entry("穴", syl("xue"))
                .build().toByteArray(),
        ),
    )

    private fun session(user: UserModel? = null) = PinyinSession(data, PinyinSegmenter(), pageSize = 2, user = user, prediction = false)

    private fun Session.type(keys: String): Snapshot = keys.map { apply(Key(it)) }.last()

    @Test
    fun aCandidateWithACoarseWordWaitsPastTheFirstPage() {
        val s = session()
        val first = s.type("xiaoxue")
        assertEquals(listOf("小学", "小雪"), first.candidates)
        assertEquals("小穴", s.apply(Action.NextPage).candidates.first())
    }

    @Test
    fun soDoesOneSpeltByWordsInTurn() {
        // 小 then 穴, each likelier than the word: as many characters, the same text
        val s = session().type("xiaoxue")
        assertTrue(s.candidates.none { it == "小穴" })
    }

    @Test
    fun onceTheUserTypedItItIsTheirs() {
        val user = UserModel(data.dictionary, data.vocabulary)
        user.learn(null, listOf(UserModel.Entry("小穴", syl("xiao", "xue"))))
        assertTrue("小穴" in session(user).type("xiaoxue").candidates)
    }
}
