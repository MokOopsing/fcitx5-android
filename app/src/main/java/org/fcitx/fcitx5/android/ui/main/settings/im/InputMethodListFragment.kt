/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.im

import android.view.View
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.ui.common.BaseDynamicListUi
import org.fcitx.fcitx5.android.ui.common.DynamicListUi
import org.fcitx.fcitx5.android.ui.main.MainViewModel.ButtonMode
import org.fcitx.fcitx5.android.ui.main.settings.ProgressFragment
import org.fcitx.fcitx5.android.ui.main.settings.SettingsRoute
import org.fcitx.fcitx5.android.utils.navigateWithAnim

class InputMethodListFragment : ProgressFragment() {

    override suspend fun initialize(): View {
        val initialEnabled = fcitx.runOnReady { enabledIme().toList() }
        val ui = requireContext().DynamicListUi(
            mode = BaseDynamicListUi.Mode.Immutable(),
            initialEntries = initialEnabled,
            enableOrder = false,
            initSettingsButton = { entry ->
                setOnClickListener {
                    navigateWithAnim(
                        SettingsRoute.InputMethodConfig(entry.displayName, entry.uniqueName),
                    )
                }
            },
            show = { it.displayName }
        )
        ui.setViewModel(viewModel)
        viewModel.toolbarButton.value = ButtonMode.NONE
        return ui.root
    }
}