/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

/**
 * Shift turned on by itself where a sentence starts, as Gboard has it in English, and off again
 * where the cursor no longer is at one (a letter deleted back). Shift the user turned on, or caps
 * lock, it leaves as it is.
 */
class AutoCaps {

    enum class Caps { NONE, ONCE, LOCK }

    // Shift-once is on because of a sentence start, not the user
    private var turnedOn = false

    /**
     * What Shift is to be, being [current], where the editor [wants] a capital next (what
     * `InputConnection.getCursorCapsMode` says of the field's own caps flags, and only where the
     * user has auto caps on).
     */
    fun next(current: Caps, wants: Boolean): Caps = when {
        current == Caps.NONE && wants -> Caps.ONCE.also { turnedOn = true }
        current == Caps.ONCE && turnedOn && !wants -> Caps.NONE.also { turnedOn = false }
        else -> current
    }

    /** Shift changed by other means: a letter used it up, or the user pressed it. */
    fun changed() {
        turnedOn = false
    }
}
