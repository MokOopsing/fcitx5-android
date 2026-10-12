/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class LeftPreeditTest {

    private val field = "我说ni hao"
    private fun before(n: Int) = field.takeLast(n)

    @Test
    fun theTextPutBackJustAfterIsTakenOut() {
        val left = LeftPreedit()
        left.dropped("ni hao", now = 1000)
        assertEquals(6, left.putBack(::before, 8, 8, now = 1500))
    }

    @Test
    fun onlyOnce() {
        val left = LeftPreedit()
        left.dropped("ni hao", now = 1000)
        left.putBack({ "" }, 0, 0, now = 1100)
        assertEquals(0, left.putBack(::before, 8, 8, now = 1200))
    }

    @Test
    fun notOtherText() {
        val left = LeftPreedit()
        left.dropped("zai jian", now = 1000)
        assertEquals(0, left.putBack(::before, 8, 8, now = 1500))
    }

    @Test
    fun notLater() {
        val left = LeftPreedit()
        left.dropped("ni hao", now = 1000)
        assertEquals(0, left.putBack(::before, 8, 8, now = 1000 + LeftPreedit.WITHIN + 1))
    }

    @Test
    fun notOverASelection() {
        val left = LeftPreedit()
        left.dropped("ni hao", now = 1000)
        assertEquals(0, left.putBack(::before, 2, 8, now = 1500))
    }

    @Test
    fun nothingDropped() {
        assertEquals(0, LeftPreedit().putBack(::before, 8, 8, now = 1500))
    }
}
