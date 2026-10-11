/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.FrameLayout
import org.fcitx.fcitx5.android.FcitxApplication
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.data.theme.ThemePreset
import org.fcitx.fcitx5.android.engine.host.Engines
import org.fcitx.fcitx5.android.ui.main.settings.PlaceholderStrings
import org.fcitx.fcitx5.android.ui.main.settings.placeholder
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * What TalkBack hears as the keyboard window ([SpaceKeyInputMethod]) shows one layout after another.
 * Going back from 九键 to the 26 keys, these were told 九键 first, in the window, so that the change
 * from it would be announced on them too: the user heard the input method they were leaving, then
 * the one they went to, at every switch between the two.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = FcitxApplication::class)
class KeyboardWindowTest {

    /** The window's frame, keeping what its keyboards announce. */
    private class Frame(context: Context) : FrameLayout(context) {
        val announced = ArrayList<String>()

        override fun requestSendAccessibilityEvent(child: View, event: AccessibilityEvent): Boolean {
            if (event.eventType == AccessibilityEvent.TYPE_ANNOUNCEMENT) announced += event.text.joinToString()
            return true
        }
    }

    // under the app, whose preferences the keys read, with placeholder strings, as NumberKeyboardTest
    private val context = PlaceholderStrings(RuntimeEnvironment.getApplication())
    private val frame = Frame(context)
    private val spaceKeys = SpaceKeyInputMethod()

    @Before
    fun talkBackOn() {
        shadowOf(context.getSystemService(AccessibilityManager::class.java)).setEnabled(true)
    }

    // as KeyboardWindow's onImeUpdate and showLayout: the layout shown taken out, the next attached
    private fun show(keyboard: BaseKeyboard, inputMethod: String) {
        spaceKeys.current = InputMethodEntry(inputMethod, inputMethod, "", "", "", "zh", "", false)
        frame.removeAllViews()
        spaceKeys.attach(keyboard) { frame.addView(keyboard) }
    }

    @Test
    fun goingBackToALayoutAnnouncesOnlyTheInputMethodGoneTo() {
        val letters = TextKeyboard(context, ThemePreset.PixelDark)
        show(letters, Engines.PINYIN)
        assertEquals("the window appearing, which TalkBack says itself", emptyList<String>(), frame.announced)
        show(T9Keyboard(context, ThemePreset.PixelDark), Engines.T9)
        show(letters, Engines.PINYIN)
        assertEquals(listOf(placeholder(R.string.im_t9), placeholder(R.string.im_pinyin)), frame.announced)
    }

    @Test
    fun aChangeUnderTheNumbersIsAnnouncedOnTheLetters() {
        val letters = TextKeyboard(context, ThemePreset.PixelDark)
        val numbers = NumberKeyboard(context, ThemePreset.PixelDark)
        show(letters, Engines.PINYIN)
        show(numbers, Engines.PINYIN)
        spaceKeys.current = InputMethodEntry(Engines.SHUANGPIN, Engines.SHUANGPIN, "", "", "", "zh", "", false)
        spaceKeys.show(numbers)
        show(letters, Engines.SHUANGPIN)
        assertEquals(listOf(placeholder(R.string.im_shuangpin)), frame.announced)
    }
}
