/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.search

import android.content.Context
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.FcitxApplication
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.ui.main.settings.PlaceholderStrings
import org.fcitx.fcitx5.android.ui.main.settings.SettingsRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class SettingsIndexTest {

    private fun context(): Context = PlaceholderStrings(RuntimeEnvironment.getApplication())

    @Test
    fun inputMethodSearchOpensTheRimeList() {
        val entries = SettingsIndex.build(context())
        assertEquals(1, entries.count { it.target.route == SettingsRoute.InputMethodList })
        assertTrue(entries.none { it.target.route is SettingsRoute.InputMethodConfig })
    }

    @Test
    fun preeditAndKeyLabelsAreIndexedUnderTheirSettingsPages() {
        val ctx = context()
        val entries = SettingsIndex.build(ctx)
        val themeAndSize = ctx.getString(R.string.theme_and_size)
        val sizeAndKeys = ctx.getString(R.string.configure_look)
        val keyboard = ctx.getString(R.string.virtual_keyboard)
        val keys = ctx.getString(R.string.section_keys)

        val keyLabels = entries.single { it.title == ctx.getString(R.string.key_labels) }
        assertEquals(listOf(keyboard, keys), keyLabels.path)
        assertEquals(SettingsRoute.KeyLabelOverrides, keyLabels.target.route)

        val preeditOverrides = entries.single {
            it.title == ctx.getString(R.string.preedit_font_size_overrides)
        }
        assertEquals(
            listOf(themeAndSize, sizeAndKeys, ctx.getString(R.string.section_preedit)),
            preeditOverrides.path
        )
    }

    /**
     * A result points at its setting by the key ([SettingsIndex.Target.key]), as the highlight finds
     * it: a setting with none, or one whose key another on its page has too, would open its page
     * and point at nothing, or at the other. The page itself is the one result with none.
     */
    @Test
    @Config(application = FcitxApplication::class)
    fun eachPageIsFoundOnceAndEachSettingOnItByAKeyOfItsOwn() {
        for ((route, on) in SettingsIndex.build(context()).groupBy { it.target.route }) {
            assertEquals("$route", 1, on.count { it.target.key == null })
            val keys = on.mapNotNull { it.target.key }
            assertEquals("$route: $keys", keys.size, keys.toSet().size)
        }
    }

    /**
     * Every setting's preference is made and attached to be read: one that saved its default as it
     * did would pin each default the user never touched, at every search.
     */
    @Test
    @Config(application = FcitxApplication::class)
    fun buildingTheIndexSavesNothing() {
        // where a preference attached with no data store saves, and where AppPrefs (and a level) does
        val stores = listOf(
            PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication()),
            AppPrefs.getInstance().keyboard.keyboardHeightPercent.sharedPreferences,
        )
        val before = stores.map { it.all.toMap() }
        assertTrue(SettingsIndex.build(context()).isNotEmpty())
        assertEquals(before, stores.map { it.all.toMap() })
    }
}
