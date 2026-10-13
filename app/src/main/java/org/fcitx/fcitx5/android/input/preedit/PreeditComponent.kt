/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.preedit

import android.view.View
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceProvider
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.dependency.context
import org.fcitx.fcitx5.android.input.dependency.theme
import org.mechdancer.dependency.Dependent
import org.mechdancer.dependency.UniqueComponent
import org.mechdancer.dependency.manager.ManagedHandler
import org.mechdancer.dependency.manager.managedHandler
import org.mechdancer.dependency.DynamicScope
import splitties.dimensions.dp
import splitties.views.horizontalPadding

class PreeditComponent : UniqueComponent<PreeditComponent>(), Dependent, InputBroadcastReceiver,
    ManagedHandler by managedHandler() {

    private val context by manager.context()
    private val theme by manager.theme()
    private val prefs = AppPrefs.getInstance()
    private val uiLazy = lazy {
        val keyBorder = ThemeManager.prefs.keyBorder.getValue()
        val bkgColor =
            if (!keyBorder && theme is Theme.Builtin) theme.barColor else theme.backgroundColor
        PreeditUi(context, theme, setupTextView = {
            horizontalPadding = dp(8)
        }).apply {
            setBackgroundColorAndCornerRadii(
                bkgColor,
                prefs.keyboardAppearance.preeditCornerTopLeft.getValue(),
                prefs.keyboardAppearance.preeditCornerTopRight.getValue(),
                prefs.keyboardAppearance.preeditCornerBottomRight.getValue(),
                prefs.keyboardAppearance.preeditCornerBottomLeft.getValue()
            )
            root.alpha = 0.8f
            root.visibility = View.INVISIBLE
        }
    }

    val ui by uiLazy

    private var inputMethod: InputMethodEntry? = null
    private var inputPanel = FcitxEvent.InputPanelEvent.Data()

    private val appearancePrefsListener = ManagedPreferenceProvider.OnChangeListener {
        updateFontSize()
    }
    private val overrideListener = ManagedPreference.OnChangeListener<String> { _, _ ->
        updateFontSize()
    }

    override fun onScopeSetupFinished(scope: DynamicScope) {
        prefs.keyboardAppearance.registerOnChangeListener(appearancePrefsListener)
        prefs.internal.preeditFontSizeOverrides.registerOnChangeListener(overrideListener)
    }

    override fun onInputPanelUpdate(data: FcitxEvent.InputPanelEvent.Data) {
        inputPanel = data
        ui.update(data)
        ui.root.visibility = if (ui.visible) View.VISIBLE else View.INVISIBLE
    }

    override fun onImeUpdate(ime: InputMethodEntry) {
        inputMethod = ime
        updateFontSize()
    }

    private fun updateFontSize() {
        if (!uiLazy.isInitialized()) return
        val ime = inputMethod
        val defaultSize = prefs.keyboardAppearance.preeditFontSize.getValue()
        val size = ime?.let {
            PreeditFontSizeOverrides.parse(prefs.internal.preeditFontSizeOverrides.getValue())
                .fontSizeFor(it.uniqueName, it.subMode.name.takeIf(String::isNotEmpty), defaultSize)
        } ?: defaultSize
        ui.setFontSize(size)
        val keyBorder = ThemeManager.prefs.keyBorder.getValue()
        val backgroundColor =
            if (!keyBorder && theme is Theme.Builtin) theme.barColor else theme.backgroundColor
        ui.setBackgroundColorAndCornerRadii(
            backgroundColor,
            prefs.keyboardAppearance.preeditCornerTopLeft.getValue(),
            prefs.keyboardAppearance.preeditCornerTopRight.getValue(),
            prefs.keyboardAppearance.preeditCornerBottomRight.getValue(),
            prefs.keyboardAppearance.preeditCornerBottomLeft.getValue()
        )
        ui.update(inputPanel)
    }
}
