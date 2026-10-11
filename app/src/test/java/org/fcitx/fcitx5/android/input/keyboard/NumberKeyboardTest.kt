/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.view.View
import org.fcitx.fcitx5.android.FcitxApplication
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.ThemePreset
import org.fcitx.fcitx5.android.ui.main.settings.PlaceholderStrings
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The number keyboard builds, its space an image key (MiniSpaceKey): a text key's accessor of it
 * threw ClassCastException at its first read. Under the app, whose preferences the keys read; the
 * keys' descriptions are placeholders, as the tests have none of the app's strings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = FcitxApplication::class)
class NumberKeyboardTest {

    @Test
    fun theSpaceIsTheKeyItIsBuiltAs() {
        val keyboard = NumberKeyboard(PlaceholderStrings(RuntimeEnvironment.getApplication()), ThemePreset.PixelDark)
        val space = keyboard.findViewById<View>(R.id.button_mini_space)
        assertTrue("$space", space is ImageKeyView)
    }
}
