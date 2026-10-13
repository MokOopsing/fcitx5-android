/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceEnum

enum class LangSwitchBehavior(override val stringRes: Int) : ManagedPreferenceEnum {
    ToggleActivate(R.string.space_behavior_activate),
    NextInputMethodApp(R.string.lang_switch_behavior_next_ime_app),
    SimulateShiftKey(R.string.lang_switch_behavior_simulate_shift),
    ToggleRimeAscii(R.string.lang_switch_behavior_toggle_ascii),
    SwitchRimeSchema(R.string.lang_switch_behavior_switch_schema);
}
