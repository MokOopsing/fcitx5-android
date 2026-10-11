/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.session

import org.fcitx.fcitx5.android.engine.data.PinyinData
import org.fcitx.fcitx5.android.engine.data.PinyinDataBuilder
import org.fcitx.fcitx5.android.engine.phrase.CustomPhrases
import org.fcitx.fcitx5.android.engine.phrase.PhraseBook
import org.fcitx.fcitx5.android.engine.pinyin.PinyinSegmenter
import org.fcitx.fcitx5.android.engine.session.Action.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.ByteBuffer

/** [PinyinSession] when a key it does not read ends the input. */
class PinyinSessionLeaveTest {

    // 你 and 好 alone: nihao reads only as 你好, whole
    private val data = PinyinData.load(
        ByteBuffer.wrap(
            PinyinDataBuilder()
                .unigram("<unk>", -7f, 0f)
                .unigram("你", -2f, 0f)
                .unigram("好", -2.5f, 0f)
                .entry("你", syl("ni"))
                .entry("好", syl("hao"))
                .build().toByteArray(),
        ),
    )

    private fun Session.type(keys: String): Snapshot = keys.map { apply(Key(it)) }.last()

    @Test
    fun aKeyNotReadKeepsWhatTheFirstCandidateLeavesAsTyped() {
        // the one reading of it all put second by a phrase: the first, 你, reads only ni
        val session = PinyinSession(data, PinyinSegmenter(), phraseBook = PhraseBook(CustomPhrases.parse("nihao,2=你好")))
        assertEquals(listOf("你", "你好"), session.type("nihao").candidates)
        val left = session.apply(Key(','))
        assertEquals("你hao", left.commit)
        assertFalse(left.handled)
        assertEquals("", left.preedit)
    }
}
