/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import org.fcitx.fcitx5.android.input.keyboard.AutoCaps.Caps
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoCapsTest {

    @Test
    fun aSentenceStartTurnsShiftOnAndLeavingItTurnsItOff() {
        val caps = AutoCaps()
        assertEquals(Caps.ONCE, caps.next(Caps.NONE, wants = true))
        assertEquals(Caps.ONCE, caps.next(Caps.ONCE, wants = true))
        // a letter deleted back: no sentence start
        assertEquals(Caps.NONE, caps.next(Caps.ONCE, wants = false))
        assertEquals(Caps.NONE, caps.next(Caps.NONE, wants = false))
    }

    @Test
    fun shiftTheUserTurnedOnStays() {
        val caps = AutoCaps()
        assertEquals(Caps.ONCE, caps.next(Caps.ONCE, wants = false))
        assertEquals(Caps.LOCK, caps.next(Caps.LOCK, wants = false))
        assertEquals(Caps.LOCK, caps.next(Caps.LOCK, wants = true))
    }

    @Test
    fun shiftUsedUpThenPressedAgainIsTheUsers() {
        val caps = AutoCaps()
        caps.next(Caps.NONE, wants = true)
        // a capital typed, and Shift pressed for the next one
        caps.changed()
        assertEquals(Caps.ONCE, caps.next(Caps.ONCE, wants = false))
    }
}
