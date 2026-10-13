/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.theme

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceCategory
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceFragment
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.ui.main.settings.SettingsRoute
import org.fcitx.fcitx5.android.utils.navigateWithAnim

class ThemeSettingsFragment : ManagedPreferenceFragment(ThemeManager.prefs) {

    private val followSystemDayNightTheme = ThemeManager.prefs.followSystemDayNightTheme

    private var resumed = false

    private lateinit var switchPreference: SwitchPreferenceCompat

    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        val dayNight = checkNotNull(
            screen.findPreference<PreferenceCategory>(
                ManagedPreferenceCategory.sectionKey(R.string.section_day_night)
            )
        )
        check(screen.removePreference(dayNight))

        AppPrefs.getInstance().keyboardAppearance.createUi(screen)

        checkNotNull(
            screen.findPreference<PreferenceCategory>(
                ManagedPreferenceCategory.sectionKey(R.string.section_preedit)
            )
        ).addDestination(
            R.string.preedit_font_size_overrides,
            R.string.preedit_font_size_overrides_summary,
            SettingsRoute.PreeditFontSizeOverrides
        )

        screen.addPreference(dayNight)
    }

    private fun PreferenceCategory.addDestination(
        title: Int,
        summary: Int,
        route: SettingsRoute
    ) {
        addPreference(Preference(requireContext()).apply {
            isIconSpaceReserved = false
            isSingleLineTitle = false
            setTitle(title)
            setSummary(summary)
            setOnPreferenceClickListener {
                navigateWithAnim(route)
                true
            }
        })
    }

    // sync SwitchPreferenceCompat's state when `followSystemDayNightTheme` changed in ThemeListFragment
    private val listener = ManagedPreference.OnChangeListener<Boolean> { _, v ->
        if (resumed) return@OnChangeListener
        switchPreference.isChecked = v
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        followSystemDayNightTheme.registerOnChangeListener(listener)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        switchPreference = findPreference(followSystemDayNightTheme.key)!!
    }

    override fun onResume() {
        super.onResume()
        resumed = true
    }

    override fun onPause() {
        super.onPause()
        resumed = false
    }

    override fun onDestroy() {
        followSystemDayNightTheme.unregisterOnChangeListener(listener)
        super.onDestroy()
    }
}
