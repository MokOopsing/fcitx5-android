/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** A step below 1, documented as taken for 1, was kept, and the seek bar divided by it. */
@RunWith(RobolectricTestRunner::class)
class DialogSeekBarPreferenceTest {

    private fun pref() = DialogSeekBarPreference(RuntimeEnvironment.getApplication())

    @Test
    fun aStepBelowOneIsOne() {
        assertEquals(1, pref().apply { step = 0 }.step)
        assertEquals(1, pref().apply { step = -5 }.step)
    }

    @Test
    fun aStepOfOneOrMoreIsKept() {
        assertEquals(1, pref().step)
        assertEquals(5, pref().apply { step = 5 }.step)
    }
}
