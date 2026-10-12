/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.setup

import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Setup comes back till the keyboard is set up, unless the user skipped it. Shown once was enough to
 * keep it away before: a user who left it for the system's settings, where a tap on the keyboard's
 * name opens the app's own settings over it, came back to the app with nothing to select the keyboard.
 * (The activity needs the app's resources and the pages the app's context, which these tests go
 * without: what a skip does is tested here, the rest on a device.)
 */
@RunWith(RobolectricTestRunner::class)
class SetupActivityTest {

    @Before
    fun reset() {
        SetupActivity.skipped = false
    }

    @Test
    fun skippedItIsNot() {
        SetupActivity.skipped = true
        assertFalse(SetupActivity.shouldShowUp())
    }
}
