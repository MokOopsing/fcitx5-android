/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.session

import org.fcitx.fcitx5.android.engine.pinyin.PinyinSegmenter
import org.fcitx.fcitx5.android.engine.pinyin.Syllables
import org.fcitx.fcitx5.android.engine.session.Action.Forget
import org.fcitx.fcitx5.android.engine.session.Action.Key
import org.fcitx.fcitx5.android.engine.session.Action.Reset
import org.fcitx.fcitx5.android.engine.session.Action.Select
import org.fcitx.fcitx5.android.engine.user.UserModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the user committed, typed short: 简拼 finds a word they use as typing it whole does. */
class PinyinSessionLearnedTest {

    private val data = sessionTestData()
    // a user who has typed for a while: one commit is a small share of all they typed, as it is
    // on a phone, so its lift alone does not put a word first
    private val user = UserModel(data.dictionary, data.vocabulary).apply {
        addCount(UserModel.Entry("的", intArrayOf(Syllables.id("de"))), TYPED)
    }
    private val session = PinyinSession(data, PinyinSegmenter(), pageSize = 20, prediction = false, user = user)

    private fun type(keys: String): Snapshot = keys.map { session.apply(Key(it)) }.last()

    private fun pick(keys: String, vararg texts: String): String {
        var shown = type(keys)
        for (text in texts) {
            val i = shown.candidates.indexOf(text)
            assertTrue("$text in ${shown.candidates}", i >= 0)
            shown = session.apply(Select(i))
        }
        return shown.commit.also { session.apply(Reset) }
    }

    private fun first(keys: String) = type(keys).candidates.first().also { session.apply(Reset) }

    private companion object {
        const val TYPED = 1000f
    }

    @Test
    fun aWordPutTogetherTwiceIsFirstForItsInitialsToo() {
        assertEquals("你好", first("nh"))
        assertEquals("拟好", pick("nihao", "拟", "好"))
        // once may be a slip
        assertEquals("你好", first("nh"))
        pick("nihao", "拟", "好")
        assertEquals("拟好", first("nh"))
    }

    @Test
    fun theWordFirstAgainTakesItBack() {
        repeat(2) { pick("nihao", "拟", "好") }
        assertEquals("拟好", first("nh"))
        repeat(3) { pick("nh", "你好") }
        assertEquals("你好", first("nh"))
    }

    // its initial alone would cut sentences into the characters typed most
    @Test
    fun aCharacterIsNotSpared() {
        val first = first("n")
        repeat(2) { pick("ni", "拟") }
        assertNotEquals("拟", first("n"))
        assertEquals(first, first("n"))
    }

    @Test
    fun aWordForgottenIsSparedNoLonger() {
        repeat(2) { pick("nihao", "拟", "好") }
        val shown = type("nh")
        assertEquals("拟好", shown.candidates.first())
        session.apply(Forget(0))
        session.apply(Reset)
        assertEquals("你好", first("nh"))
    }
}
