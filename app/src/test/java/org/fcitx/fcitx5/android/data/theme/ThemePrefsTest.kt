/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.theme

import android.content.Context
import androidx.core.content.edit
import org.fcitx.fcitx5.android.FcitxApplication
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The theme page's controls use the keyboard and theme preferences. */
@RunWith(RobolectricTestRunner::class)
// the real application: the keyboard's size is AppPrefs', and a theme preference reaches the theme manager
@Config(application = FcitxApplication::class)
class ThemePrefsTest {

    private val sp = RuntimeEnvironment.getApplication().getSharedPreferences("theme-prefs-test", Context.MODE_PRIVATE)

    @Before
    fun empty() {
        sp.edit { clear() }
    }

    private fun ThemePrefs.level(key: String) =
        managedPreferencesUi.filterIsInstance<ManagedPreferenceUi.Levels>().single { it.key == key }.current()

    @Test
    fun remainingLevelListsShowTheLevelOfWhatTheirPreferencesHold() {
        val prefs = ThemePrefs(sp)
        // untouched: standard spacing and rounded keys
        val lists = listOf("key_spacing_level", "key_radius_level")
        assertEquals(listOf(1, 1), lists.map { prefs.level(it) })
        prefs.keyRadius.setValue(10)
        listOf(prefs.keyHorizontalMargin, prefs.keyHorizontalMarginLandscape).forEach { it.setValue(5) }
        prefs.keyVerticalMargin.setValue(9)
        prefs.keyVerticalMarginLandscape.setValue(6)
        assertEquals(2, prefs.level("key_radius_level"))
        assertEquals(2, prefs.level("key_spacing_level"))
    }

    @Test
    fun themePreferencesHaveVisibleControls() {
        val prefs = ThemePrefs(sp)
        val ui = prefs.managedPreferencesUi.associateBy { it.key }
        val booleanSwitches = listOf("key_border_stroke", "key_ripple_effect")
        val otherControls = listOf(
            "key_horizontal_margin", "key_vertical_margin", "key_radius",
            "text_editing_button_radius", "clipboard_entry_radius", "navbar_background",
        )
        booleanSwitches.forEach { key ->
            assertTrue("$key should be a visible switch", ui[key] is ManagedPreferenceUi.Switch)
        }
        otherControls.forEach { key ->
            assertTrue("$key should have a visible control", key in ui)
        }
    }

    @Test
    fun keyboardSizeControlsHaveSeparateOrientationValues() {
        val ui = ThemePrefs(sp).managedPreferencesUi.associateBy { it.key }

        val height = ui["keyboard_height_percent"] as ManagedPreferenceUi.TwinSeekBarInt
        assertEquals(R.string.keyboard_height, height.title)
        assertEquals("keyboard_height_percent_landscape", height.secondaryKey)
        assertEquals("%", height.unit)

        val sidePadding = ui["keyboard_side_padding"] as ManagedPreferenceUi.TwinSeekBarInt
        assertEquals(R.string.keyboard_side_padding, sidePadding.title)
        assertEquals("keyboard_side_padding_landscape", sidePadding.secondaryKey)
        assertEquals("dp", sidePadding.unit)

        val bottomPadding = ui["keyboard_bottom_padding"] as ManagedPreferenceUi.TwinSeekBarInt
        assertEquals(R.string.keyboard_bottom_padding, bottomPadding.title)
        assertEquals("keyboard_bottom_padding_landscape", bottomPadding.secondaryKey)
        assertEquals("dp", bottomPadding.unit)

        assertTrue(ui["keyboard_height_level"] == null)
        assertTrue(ui["keyboard_side_padding_level"] == null)
        assertTrue(ui["keyboard_bottom_padding_level"] == null)
    }
}
