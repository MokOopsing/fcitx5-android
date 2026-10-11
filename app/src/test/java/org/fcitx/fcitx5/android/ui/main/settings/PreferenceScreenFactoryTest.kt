/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings

import androidx.preference.PreferenceManager
import io.mockk.mockk
import org.fcitx.fcitx5.android.FcitxApplication
import org.fcitx.fcitx5.android.core.RawConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** A settings page made of fcitx's description of it: one that does not parse gives a page with no options, not a crash. */
@RunWith(RobolectricTestRunner::class)
// the real application, which initialises the AppPrefs the factory reads
@Config(application = FcitxApplication::class)
class PreferenceScreenFactoryTest {

    /** A page of one option, Enabled, described by [fields]. */
    private fun page(vararg fields: Pair<String, String>): RawConfig {
        val option = RawConfig("Enabled", fields.map { (name, value) -> RawConfig(name, value) }.toTypedArray())
        return RawConfig(
            arrayOf(
                RawConfig("cfg", arrayOf(RawConfig("Enabled", true))),
                RawConfig("desc", arrayOf(RawConfig("Page", arrayOf(option)))),
            )
        )
    }

    private fun optionsOf(raw: RawConfig): Int {
        val ctx = RuntimeEnvironment.getApplication()
        return PreferenceScreenFactory.create(PreferenceManager(ctx), mockk(relaxed = true), raw) {}.preferenceCount
    }

    @Test
    fun aDescriptionThatDoesNotParseGivesAPageWithNoOptions() {
        assertEquals(1, optionsOf(page("Type" to "Boolean")))
        // an Integer whose default is no number: ConfigDescriptor's Left, which was thrown
        assertEquals(0, optionsOf(page("Type" to "Integer", "DefaultValue" to "x")))
    }
}
