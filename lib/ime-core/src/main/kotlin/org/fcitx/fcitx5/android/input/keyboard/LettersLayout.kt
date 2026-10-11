/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

/**
 * The letters' layout is the input method's: the nine keys (九键) for its own, [text] (the 26 keys)
 * for the rest. [LayoutSwitchPolicy] knows the letters only as [text]; this switches through it
 * with the letters as they are, and says what a change of input method shows.
 *
 * @param keyboards the names of the keyboard layouts that exist
 * @param text the letters layout of every input method without one of its own
 * @param symbolPicker the name remembered when the symbol picker is opened
 */
class LettersLayout(keyboards: Set<String>, private val text: String, symbolPicker: String) {

    private val policy = LayoutSwitchPolicy(keyboards, text, symbolPicker)

    /** The input method's letters layout. */
    var letters = text
        private set

    /** [LayoutSwitchPolicy.switchTo], [current] and the target being the letters as they are. */
    fun switchTo(requested: String, lastSymbol: String, current: String, remember: Boolean): LayoutSwitchPolicy.Outcome {
        val outcome = policy.switchTo(requested, lastSymbol, if (current == letters) text else current, remember)
        val target = outcome.target
        return if (target is LayoutSwitchPolicy.Target.Keyboard && target.name == text) {
            outcome.copy(target = LayoutSwitchPolicy.Target.Keyboard(letters))
        } else {
            outcome
        }
    }

    /**
     * The input method's letters layout became [layout] while [current] shows: the layout to show
     * now, or null to leave [current] as it is. Under another layout the new letters wait for the
     * switch back to them.
     */
    fun inputMethodChanged(layout: String, current: String): String? {
        if (layout == letters) return null
        val showing = current == letters
        letters = layout
        return layout.takeIf { showing }
    }
}
