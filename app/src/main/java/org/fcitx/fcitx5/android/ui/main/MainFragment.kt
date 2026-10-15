/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.MenuProvider
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.InputMethodEntry
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
import org.fcitx.fcitx5.android.utils.toast

class MainFragment : PaddingPreferenceFragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private var inputMethodEntry: InputMethodEntry? = null
    private var inputMethodPreference: Preference? = null
    private var inputMethodIcon: Drawable? = null
    private var inputMethodIconText = ""

    override fun onViewCreated(
        view: View, savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        // AboutMenuProvider is tied to viewLifecycleOwner, so the search, help and about menu items
        // are automatically shown when this Fragment is visible and removed when it's not
        requireActivity().addMenuProvider(
            AboutMenuProvider(), viewLifecycleOwner, Lifecycle.State.STARTED
        )
        viewLifecycleOwner.lifecycleScope.launch {
            val entry = loadInputMethodEntry()
            inputMethodEntry = entry
            inputMethodPreference?.title = entry?.displayName ?: getString(R.string.input_methods)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            updateInputMethodIcon(viewModel.fcitx.runOnReady { currentIme() })
            viewModel.fcitx.runImmediately { eventFlow }.collect { event ->
                if (event is FcitxEvent.IMChangeEvent) {
                    updateInputMethodIcon(event.data)
                }
            }
        }
    }

    private fun updateInputMethodIcon(ime: InputMethodEntry) {
        inputMethodIconText = ime.displayName.take(1)
        inputMethodIcon?.invalidateSelf()
    }

    private suspend fun loadInputMethodEntry(): InputMethodEntry? =
        viewModel.fcitx.runOnReady {
            enabledIme().firstOrNull() ?: availableIme().firstOrNull()
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
        route: SettingsRoute,
        @StringRes summary: Int? = null
    ) {
        addPreference(title, summary, icon = icon) {
            navigateWithAnim(route)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // grouped by what the user wants to adjust, not by which layer (fcitx or Android) owns the
        // setting; what a phone's user seldom needs (a physical keyboard's candidate window) is
        // under Advanced, and fcitx's global options are not shown at all
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply {
            addCategory(R.string.home_section_input) {
                inputMethodPreference = Preference(context).apply {
                    setup(getString(R.string.input_methods)) {
                        viewLifecycleOwner.lifecycleScope.launch {
                            val entry = inputMethodEntry ?: loadInputMethodEntry()
                                .also { inputMethodEntry = it }
                            if (entry == null) {
                                requireContext().toast(R.string.preedit_font_size_no_input_methods)
                            } else {
                                navigateWithAnim(
                                    SettingsRoute.InputMethodConfig(entry.displayName, entry.uniqueName)
                                )
                            }
                        }
                    }
                    val iconSize = (24 * resources.displayMetrics.density).toInt()
                    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = context.styledColor(android.R.attr.colorControlNormal)
                        textAlign = Paint.Align.CENTER
                        textSize = 20 * resources.displayMetrics.scaledDensity
                    }
                    icon = object : Drawable() {
                        override fun draw(canvas: Canvas) {
                            val metrics = textPaint.fontMetrics
                            canvas.drawText(
                                inputMethodIconText,
                                bounds.exactCenterX(),
                                bounds.exactCenterY() - (metrics.ascent + metrics.descent) / 2,
                                textPaint
                            )
                        }

                        override fun setAlpha(alpha: Int) {
                            textPaint.alpha = alpha
                        }

                        override fun setColorFilter(colorFilter: ColorFilter?) {
                            textPaint.colorFilter = colorFilter
                        }

                        @Suppress("DEPRECATION")
                        override fun getOpacity() = PixelFormat.TRANSLUCENT

                        override fun getIntrinsicWidth() = iconSize
                        override fun getIntrinsicHeight() = iconSize
                    }
                    isIconSpaceReserved = true
                    inputMethodIcon = icon
                }.also(::addPreference)
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
