/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.prefs

import android.content.Context
import androidx.core.content.edit
import org.fcitx.fcitx5.android.FcitxApplication
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.data.theme.ThemePrefs
import org.fcitx.fcitx5.android.input.keyboard.LangSwitchBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A setting moved out of the settings page kept what an older version or a backup set, with
 * nothing left to change it back; one still controlled by another settings page must keep it.
 */
@RunWith(RobolectricTestRunner::class)
class AppPrefsTest {

    private val sp = RuntimeEnvironment.getApplication().getSharedPreferences("app-prefs-test", Context.MODE_PRIVATE)

    @Before
    fun empty() {
        sp.edit { clear() }
    }

    @Test
    fun settingsWithoutAUiGoBackToTheirDefaultsOnceAndAgainAfterAnImport() {
        sp.edit {
            putBoolean("ignore_system_cursor", true)
            putString("lang_switch_key_behavior", LangSwitchBehavior.ToggleActivate.name)
            putInt("candidates_window_radius", 12)
        }
        val prefs = AppPrefs(sp)
        prefs.resetHiddenSettings()
        assertFalse(prefs.advanced.ignoreSystemCursor.getValue())
        assertEquals(LangSwitchBehavior.SwitchRimeSchema, prefs.keyboard.langSwitchKeyBehavior.getValue())
        assertEquals(0, prefs.candidates.windowRadius.getValue())
        sp.edit { putBoolean("ignore_system_cursor", true) }
        prefs.resetHiddenSettings()
        assertTrue("once", prefs.advanced.ignoreSystemCursor.getValue())
        prefs.resetHiddenSettings(again = true)
        assertFalse(prefs.advanced.ignoreSystemCursor.getValue())
    }

    // ThemePrefs, which exposes the keyboard's orientation controls, is read through ThemeManager
    @Test
    @Config(application = FcitxApplication::class)
    fun whatTheSettingsPageStillSetsIsKept() {
        val prefs = AppPrefs(sp)
        val categories = listOf(prefs.keyboard, prefs.candidates, prefs.clipboard, prefs.symbols, prefs.advanced)
        // AppPrefs' keys alone: ThemeManager.prefs is registered with the application's AppPrefs, not this one.
        // ThemePrefsTest and aVisibleThemePreferenceKeepsItsValueAcrossHiddenResets check that theme controls survive resets
        val own = categories.flatMapTo(HashSet()) { it.managedPreferences.keys }
        // what every level sets, one off its default: the long press, the vibration, the sound
        val levels = (categories + ThemeManager.prefs)
            .flatMap { it.managedPreferencesUi }
            .filterIsInstance<ManagedPreferenceUi.Levels>()
            .flatMap { it.keys }
            .filter { it.first in own }
            .associate { (key, default) -> key to default + 1 }
        assertTrue(levels.keys.contains("keyboard_long_press_delay"))
        assertFalse(levels.containsKey("keyboard_height_percent"))
        val orientationSpecificSize = mapOf(
            "keyboard_height_percent" to 61,
            "keyboard_height_percent_landscape" to 67,
            "keyboard_side_padding" to 13,
            "keyboard_side_padding_landscape" to 37,
            "keyboard_bottom_padding" to 17,
            "keyboard_bottom_padding_landscape" to 9,
        )
        sp.edit {
            levels.forEach { (key, value) -> putInt(key, value) }
            orientationSpecificSize.forEach { (key, value) -> putInt(key, value) }
            putBoolean("popup_on_key_press", false)
        }
        prefs.resetHiddenSettings()
        assertEquals(levels, levels.mapValues { sp.getInt(it.key, -1) })
        assertEquals(orientationSpecificSize, orientationSpecificSize.mapValues { sp.getInt(it.key, -1) })
        assertFalse("shown", prefs.keyboard.popupOnKeyPress.getValue())
    }

    @Test
    @Config(application = FcitxApplication::class)
    fun aVisibleThemePreferenceKeepsItsValueAcrossHiddenResets() {
        sp.edit {
            putBoolean("key_ripple_effect", true)
            putInt("key_radius", 9)
        }
        val prefs = AppPrefs(sp)
        prefs.resetHiddenSettings()
        val theme = prefs.registerProvider(::ThemePrefs)
        assertTrue(theme.keyRippleEffect.getValue())
        assertEquals(9, theme.keyRadius.getValue())
        val later = AppPrefs(sp)
        later.resetHiddenSettings()
        val laterTheme = later.registerProvider(::ThemePrefs)
        assertTrue(laterTheme.keyRippleEffect.getValue())
        assertEquals(9, laterTheme.keyRadius.getValue())
    }
}
