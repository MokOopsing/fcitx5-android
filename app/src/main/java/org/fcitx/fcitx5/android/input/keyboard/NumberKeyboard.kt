/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.popup.PopupAction
import org.fcitx.fcitx5.android.input.keyboard.KeyLayoutParam
import splitties.views.imageResource

@SuppressLint("ViewConstructor")
class NumberKeyboard(
    context: Context,
    theme: Theme,
) : BaseKeyboardFlex(context, theme, Layout) {

    /*class PunctuationKey(p: String, percentWidth: Float, variant: Appearance.Variant) : KeyDef(
        Appearance.Text(
            keyCodeString = p,
            displayText = p,
            textSize = 23f,
            percentWidth = percentWidth,
            variant = variant
        ),
        setOf(
            Behavior.Press(KeyAction.CommitAction(p))
        )
    )*/

    companion object {
        const val Name = "Number"

        val Layout: List<KeyDef> = listOf(
            // 第一行（row = 0）
            NumPadKey("+", 0xffab, 23f, 0.15f, KeyDef.Appearance.Variant.Alternative, layoutParam = KeyLayoutParam(0, 0)),
            NumPadKey("1", 0xffb1, 30f, 0f, layoutParam = KeyLayoutParam(0, 1)),
            NumPadKey("2", 0xffb2, 30f, 0f, layoutParam = KeyLayoutParam(0, 2)),
            NumPadKey("3", 0xffb3, 30f, 0f, layoutParam = KeyLayoutParam(0, 3)),
            NumPadKey("/", 0xffaf, 23f, 0.15f, KeyDef.Appearance.Variant.Alternative, layoutParam = KeyLayoutParam(0, 4)),

            // 第二行（row = 1）
            NumPadKey("-", 0xffad, 23f, 0.15f, KeyDef.Appearance.Variant.Alternative, layoutParam = KeyLayoutParam(1, 0)),
            NumPadKey("4", 0xffb4, 30f, 0f, layoutParam = KeyLayoutParam(1, 1)),
            NumPadKey("5", 0xffb5, 30f, 0f, layoutParam = KeyLayoutParam(1, 2)),
            NumPadKey("6", 0xffb6, 30f, 0f, layoutParam = KeyLayoutParam(1, 3)),
            MiniSpaceKey(layoutParam = KeyLayoutParam(1, 4)),

            // 第三行（row = 2）
            NumPadKey("*", 0xffaa, 23f, 0.15f, KeyDef.Appearance.Variant.Alternative, layoutParam = KeyLayoutParam(2, 0)),
            NumPadKey("7", 0xffb7, 30f, 0f, layoutParam = KeyLayoutParam(2, 1)),
            NumPadKey("8", 0xffb8, 30f, 0f, layoutParam = KeyLayoutParam(2, 2)),
            NumPadKey("9", 0xffb9, 30f, 0f, layoutParam = KeyLayoutParam(2, 3)),
            BackspaceKey(layoutParam = KeyLayoutParam(2, 4)),

            // 第四行（row = 3）
            LayoutSwitchKey("ABC", TextKeyboard.Name, layoutParam = KeyLayoutParam(3, 0)),
            NumPadKey(",", 0xffac, 23f, 0.1f, KeyDef.Appearance.Variant.Alternative, layoutParam = KeyLayoutParam(3, 1)),
            LayoutSwitchKey("!?#", PickerWindow.Key.Symbol.name, 0.13333f, KeyDef.Appearance.Variant.AltForeground, layoutParam = KeyLayoutParam(3, 2)),
            NumPadKey("0", 0xffb0, 30f, 0.23334f, layoutParam = KeyLayoutParam(3, 3)),
            NumPadKey("=", 0xffbd, 23f, 0.13333f, KeyDef.Appearance.Variant.AltForeground, layoutParam = KeyLayoutParam(3, 4)),
            NumPadKey(".", 0xffae, 23f, 0.1f, KeyDef.Appearance.Variant.Alternative, layoutParam = KeyLayoutParam(3, 5)),
            ReturnKey(layoutParam = KeyLayoutParam(3, 6))
        )
    }

    val backspace: ImageKeyView by lazy { findViewById(R.id.button_backspace) }
    val space: TextKeyView by lazy { findViewById(R.id.button_mini_space) }
    val `return`: ImageKeyView by lazy { findViewById(R.id.button_return) }

    override fun onReturnDrawableUpdate(returnDrawable: Int) {
        `return`.img.imageResource = returnDrawable
    }

    @SuppressLint("MissingSuperCall")
    override fun onPopupAction(action: PopupAction) {
        // leave empty on purpose to disable popup in NumberKeyboard
    }

}
