/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.graphics.Typeface
import androidx.annotation.DrawableRes
import org.fcitx.fcitx5.android.data.InputFeedbacks

open class KeyDef(
    val appearance: Appearance,
    val behaviors: Set<Behavior>,
    val popup: Array<Popup>? = null
) {
    sealed class Appearance(
        val percentWidth: Float,
        val variant: Variant,
        val border: Border,
        val margin: Boolean,
        val viewId: Int,
        val radiusAdj: Boolean,
        val soundEffect: InputFeedbacks.SoundEffect
    ) {
        enum class Variant {
            Normal, AltForeground, Alternative, Accent
        }

        enum class Border {
            Default, On, Off, Special
        }

        open class Text(
            val keyCodeString: String,
            val displayText: String,
            val textSize: Float,
            /**
             * `Int` constants in [Typeface].
             * Can be `NORMAL`(default), `BOLD`, `ITALIC` or `BOLD_ITALIC`
             */
            val textStyle: Int = Typeface.NORMAL,
            percentWidth: Float = 0.1f,
            variant: Variant = Variant.Normal,
            border: Border = Border.Default,
            margin: Boolean = true,
            viewId: Int = -1,
            radiusAdj: Boolean = false,
            soundEffect: InputFeedbacks.SoundEffect = InputFeedbacks.SoundEffect.Standard
        ) : Appearance(percentWidth, variant, border, margin, viewId, radiusAdj, soundEffect)

        class AltText(
            keyCodeString: String,
            displayText: String,
            val altText: String,
            textSize: Float,
            /**
             * `Int` constants in [Typeface].
             * Can be `NORMAL`(default), `BOLD`, `ITALIC` or `BOLD_ITALIC`
             */
            textStyle: Int = Typeface.NORMAL,
            percentWidth: Float = 0.1f,
            variant: Variant = Variant.Normal,
            border: Border = Border.Default,
            margin: Boolean = true,
            viewId: Int = -1,
            radiusAdj: Boolean = false,
        ) : Text(keyCodeString, displayText, textSize, textStyle, percentWidth, variant, border, margin, viewId, radiusAdj)

        class Image(
            @DrawableRes
            val src: Int,
            percentWidth: Float = 0.1f,
            variant: Variant = Variant.Normal,
            border: Border = Border.Default,
            margin: Boolean = true,
            viewId: Int = -1,
            radiusAdj: Boolean = false,
            soundEffect: InputFeedbacks.SoundEffect = InputFeedbacks.SoundEffect.Standard
        ) : Appearance(percentWidth, variant, border, margin, viewId, radiusAdj, soundEffect)

        class ImageText(
            keyCodeString: String,
            displayText: String,
            textSize: Float,
            /**
             * `Int` constants in [Typeface].
             * Can be `NORMAL`(default), `BOLD`, `ITALIC` or `BOLD_ITALIC`
             */
            textStyle: Int = Typeface.NORMAL,
            @DrawableRes
            val src: Int,
            percentWidth: Float = 0.1f,
            variant: Variant = Variant.Normal,
            border: Border = Border.Default,
            margin: Boolean = true,
            viewId: Int = -1,
            radiusAdj: Boolean = false,
        ) : Text(keyCodeString, displayText, textSize, textStyle, percentWidth, variant, border, margin, viewId, radiusAdj)
    }

    sealed class Behavior {
        class Press(
            val action: KeyAction
        ) : Behavior()

        class LongPress(
            val action: KeyAction
        ) : Behavior()

        class Repeat(
            val action: KeyAction
        ) : Behavior()

        class Swipe(
            val action: KeyAction
        ) : Behavior()

        class DoubleTap(
            val action: KeyAction
        ) : Behavior()
    }

    sealed class Popup {
        open class Preview(val content: String, val labelContent: String) : Popup()

        class AltPreview(content: String, labelContent: String, val alternative: String) : Preview(content, labelContent)

        sealed class Keyboard : Popup() {
            data class Preset(val label: String, val transformPunctuation: Boolean = true) :
                Keyboard()

            class Explicit(val items: Array<String>) : Keyboard()
        }

        class Menu(val items: Array<Item>) : Popup() {
            class Item(val label: String, @DrawableRes val icon: Int, val action: KeyAction)
        }
    }
}