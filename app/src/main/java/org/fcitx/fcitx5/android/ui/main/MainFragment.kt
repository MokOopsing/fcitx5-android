/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import org.fcitx.fcitx5.android.BuildConfig
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.engine.data.DataAge
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment
import org.fcitx.fcitx5.android.ui.main.settings.SettingsRoute
import org.fcitx.fcitx5.android.utils.Const
import org.fcitx.fcitx5.android.utils.addCategory
import org.fcitx.fcitx5.android.utils.addPreference
import org.fcitx.fcitx5.android.utils.item
import org.fcitx.fcitx5.android.utils.navigateWithAnim
import org.fcitx.fcitx5.android.utils.openUrl
import org.fcitx.fcitx5.android.utils.setup
import org.fcitx.fcitx5.android.utils.styledColor
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MainFragment : PaddingPreferenceFragment() {

    override fun onViewCreated(
        view: View, savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        // AboutMenuProvider is tied to viewLifecycleOwner, so the search, help and about menu items
        // are automatically shown when this Fragment is visible and removed when it's not
        requireActivity().addMenuProvider(
            AboutMenuProvider(), viewLifecycleOwner, Lifecycle.State.STARTED
        )
    }

    private inner class AboutMenuProvider : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            val tint = requireContext().styledColor(android.R.attr.colorControlNormal)
            menu.item(R.string.search_settings, R.drawable.ic_baseline_search_24, tint, showAsAction = true) {
                navigateWithAnim(SettingsRoute.Search)
            }
            menu.item(R.string.help, R.drawable.ic_outline_help_outline_24, tint, showAsAction = true) {
                requireContext().openUrl(Const.helpUrl)
            }
            menu.item(R.string.developer) {
                navigateWithAnim(SettingsRoute.Developer)
            }
            menu.item(R.string.about) {
                navigateWithAnim(SettingsRoute.About)
            }
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean = false
    }

    private fun PreferenceCategory.addDestinationPreference(
        @StringRes title: Int,
        @DrawableRes icon: Int,
        route: SettingsRoute
    ) {
        addPreference(title, icon = icon) {
            navigateWithAnim(route)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // grouped by what the user wants to adjust, not by which layer (fcitx or Android) owns the
        // setting; what a phone's user seldom needs (a physical keyboard's candidate window) is
        // under Advanced, and fcitx's global options are not shown at all
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply {
            // the offline build fetches nothing itself: new words come with a new version
            val dataMade = dataDay(BuildConfig.ENGINE_DATA, BuildConfig.ENGINE_WORDS) ?: BuildConfig.BUILD_TIME
            if (DataAge.isStale(dataMade, System.currentTimeMillis())) {
                addPreference(R.string.data_outdated, R.string.data_outdated_summary) {
                    requireContext().openUrl(Const.releasesUrl)
                }
            }
            addCategory(R.string.home_section_input) {
                addDestinationPreference(
                    R.string.input_methods,
                    R.drawable.ic_baseline_language_24,
                    SettingsRoute.InputMethodList
                )
            }
            addCategory(R.string.home_section_words) {
                // the user dictionary is the first of the three layers on the dictionaries' page:
                // one way in, not a second one here
                addDestinationPreference(
                    R.string.custom_phrases,
                    R.drawable.ic_baseline_text_format_24,
                    SettingsRoute.PinyinCustomPhrase
                )
                addDestinationPreference(
                    R.string.word_packs,
                    R.drawable.ic_baseline_list_alt_24,
                    SettingsRoute.PinyinDict("")
                )
            }
            addCategory(R.string.home_section_keyboard_look) {
                addDestinationPreference(
                    R.string.theme_and_size,
                    R.drawable.ic_baseline_palette_24,
                    SettingsRoute.Theme
                )
                addDestinationPreference(
                    R.string.virtual_keyboard,
                    R.drawable.ic_baseline_keyboard_24,
                    SettingsRoute.VirtualKeyboard
                )
            }
            addCategory(R.string.home_section_clipboard_emoji) {
                addDestinationPreference(
                    R.string.clipboard,
                    R.drawable.ic_clipboard,
                    SettingsRoute.Clipboard
                )
                addDestinationPreference(
                    R.string.emoji_and_symbols,
                    R.drawable.ic_baseline_emoji_symbols_24,
                    SettingsRoute.Symbol
                )
            }
            addCategory(R.string.home_section_other) {
                addPreference(Preference(context).apply {
                    setup(
                        getString(R.string.app_language),
                        AppLanguage.name(context, AppLanguage.current()),
                        R.drawable.ic_baseline_language_24
                    ) {
                        // set here: a pick that leaves the configuration as it was makes no page anew
                        AppLanguage.choose(context) { summary = AppLanguage.name(context, it) }
                    }
                })
                addDestinationPreference(
                    R.string.advanced,
                    R.drawable.ic_baseline_more_horiz_24,
                    SettingsRoute.Advanced
                )
            }
        }
    }
}

// the day in a release's tag, engine-data-20261005-3, or its month, words-202610
private val RELEASE_DAY = Regex("""-(\d{8}|\d{6})(?:-|$)""")

/**
 * The day the newest of the data of [releases] (their tags) was made, in epoch milliseconds; null
 * if no tag has a day. Its age, not the build's: an old tree built today has old data all the
 * same. Each part counts (the model, the word pack), as each is released apart from the others.
 */
internal fun dataDay(vararg releases: String): Long? = releases.mapNotNull(::releaseDay).maxOrNull()

// a month's tag from its first day, the earliest it may have been made
private fun releaseDay(release: String): Long? {
    val day = RELEASE_DAY.find(release)?.groupValues?.get(1) ?: return null
    return SimpleDateFormat(if (day.length == 8) "yyyyMMdd" else "yyyyMM", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
        isLenient = false
    }.parse(day, ParsePosition(0))?.time
}
