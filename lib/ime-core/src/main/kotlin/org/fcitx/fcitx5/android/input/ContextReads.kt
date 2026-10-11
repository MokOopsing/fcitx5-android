/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input

/**
 * The reads of the engine's context the service asks for, some made only once fcitx gets to them:
 * of those, only the last asked for is made, so that of a burst of cursor reports the last alone
 * waits on the editor. None asked for before the input started or finished ([forget]) is made:
 * it was asked in another field, a password maybe, which [FieldPolicy] no longer knows of by then.
 */
class ContextReads {

    private var asked = 0

    /** A read asked for now; [isLatest] of what this returns says, when its turn comes, whether to make it. */
    fun ask(): Int = ++asked

    /** Whether [read], from [ask], is still the last read asked for, in the field it was asked in. */
    fun isLatest(read: Int): Boolean = read == asked

    /** The input started or finished: no read asked for before is made. */
    fun forget() {
        asked++
    }
}
