/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.status

import android.content.Context
import androidx.annotation.DrawableRes
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.Action

sealed class StatusAreaEntry(
    val label: String,
    @DrawableRes
    val icon: Int,
    val active: Boolean
) {
    class Android(label: String, icon: Int, val type: Type) :
        StatusAreaEntry(label, icon, false) {
        enum class Type {
            InputMethod,
            Keyboard,
            ThemeList
        }
    }

    class Fcitx(val action: Action, label: String, icon: Int, active: Boolean) :
        StatusAreaEntry(label, icon, active)

    companion object {
        private fun drawableFromIconName(icon: String) = when (icon) {
            // fallback
            "" -> 0
            else -> {
                if (icon.endsWith("-inactive")) {
                    R.drawable.ic_baseline_code_off_24
                } else {
                    R.drawable.ic_baseline_code_24
                }
            }
        }

        fun fromAction(context: Context, it: Action): Fcitx {
            val active = it.icon.endsWith("-active") || it.isChecked
            return Fcitx(it, label(context, it.name, active) ?: it.shortText, drawableFromIconName(it.icon), active)
        }

        // fcitx names its actions in its own language, not the app's
        private fun label(context: Context, action: String, active: Boolean) = when (action) {
            "punctuation" -> if (active) R.string.status_punctuation_chinese else R.string.status_punctuation_english
            "fullwidth" -> if (active) R.string.status_full_width else R.string.status_half_width
            "chttrans" -> if (active) R.string.status_traditional else R.string.status_simplified
            else -> null
        }?.let(context::getString)
    }
}