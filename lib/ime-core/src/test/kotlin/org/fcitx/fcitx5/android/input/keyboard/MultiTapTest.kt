/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MultiTapTest {

    @Test
    fun theSameKeyAgainSoonIsItsNextLetterRoundToTheFirst() {
        val tap = MultiTap(600)
        assertNull(tap.press('4', "ghi", 0))
        assertEquals('h', tap.press('4', "ghi", 100))
        assertEquals('i', tap.press('4', "ghi", 200))
        assertEquals('g', tap.press('4', "ghi", 300))
        assertEquals('h', tap.press('4', "ghi", 400))
    }

    @Test
    fun aPauseAnotherKeyOrSomethingElseStartsAgain() {
        val tap = MultiTap(600)
        tap.press('4', "ghi", 0)
        // measured from the last press, the timeout itself still soon
        assertEquals('h', tap.press('4', "ghi", 600))
        assertNull(tap.press('4', "ghi", 1201))
        assertNull(tap.press('7', "pqrs", 1300))
        assertNull(tap.press('4', "ghi", 1400))
        tap.reset()
        assertNull(tap.press('4', "ghi", 1500))
        // a time gone back (another clock) is no press again
        assertNull(tap.press('4', "ghi", 1000))
    }

    @Test
    fun peekNotesNothingAndOffPressesAreKeysOfTheirOwn() {
        val tap = MultiTap(600)
        tap.press('9', "wxyz", 0)
        assertEquals('x', tap.peek('9', "wxyz", 100))
        assertEquals('x', tap.press('9', "wxyz", 100))
        val off = MultiTap(0)
        off.press('4', "ghi", 0)
        assertNull(off.press('4', "ghi", 1))
    }
}
