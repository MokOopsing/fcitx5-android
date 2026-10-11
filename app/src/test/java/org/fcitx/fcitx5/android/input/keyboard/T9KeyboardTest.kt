/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.app.Activity
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import org.fcitx.fcitx5.android.FcitxApplication
import org.fcitx.fcitx5.android.core.EngineBridge
import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.data.theme.ThemePreset
import org.fcitx.fcitx5.android.engine.host.Keyboard
import org.fcitx.fcitx5.android.ui.main.settings.PlaceholderStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * The nine keys' column keeps its items from one key to the next and reads what it shows when one
 * is picked: a syllable goes with its list's id, for the engine to drop if a key typed since
 * replaced that list; punctuation is committed as it is. A click released on a list the column has
 * replaced since is dropped, as removing the items dropped it.
 */
// Robolectric, as LayoutSwitchKeyTest: reading T9Keyboard's companion initialises the View class
@RunWith(RobolectricTestRunner::class)
class T9KeyboardTest {

    @Test
    fun whileTypingTheColumnOffersTheSyllablesOfItsList() {
        val state = EngineBridge.T9State(composing = true, syllables = listOf("ni", "mi", "o"), id = 3)
        assertEquals(listOf("ni", "mi", "o"), T9Keyboard.labels(state))
        // fcitx's key for a Unicode character: 0x01000000 plus its code point
        assertEquals(
            KeyAction.SymAction(KeySym(0x01000000 + Keyboard.syllableKey(1, 3).code), KeyStates.Virtual),
            T9Keyboard.pick(state, 1)
        )
    }

    @Test
    fun aDigitPressedAgainSoonIsItsNextLetterInPlaceOfTheLast() {
        val tap = MultiTap(600)
        val keys = KeyActionListener.Source.Keyboard
        val four = KeyAction.FcitxKeyAction("4")
        assertEquals(four, T9Keyboard.typed(four, keys, tap, 0))
        assertEquals(letter('h', again = true), T9Keyboard.typed(four, keys, tap, 100))
        // something else between: a key of its own again
        val back = KeyAction.SymAction(KeySym(0xff08))
        assertEquals(back, T9Keyboard.typed(back, keys, tap, 150))
        assertEquals(four, T9Keyboard.typed(four, keys, tap, 200))
        // off: every press a key
        val off = MultiTap(0)
        assertEquals(four, T9Keyboard.typed(four, keys, off, 0))
        assertEquals(four, T9Keyboard.typed(four, keys, off, 1))
    }

    @Test
    fun theLongPressTypesALetterAsItselfAfterTheLastOrCommitsTheDigit() {
        val tap = MultiTap(600)
        val popup = KeyActionListener.Source.Popup
        assertEquals(letter('g', again = false), T9Keyboard.typed(KeyAction.FcitxKeyAction("g"), popup, tap, 0))
        assertEquals(KeyAction.CommitAction("4"), T9Keyboard.typed(KeyAction.FcitxKeyAction("4"), popup, tap, 10))
        // and the next press is a key of its own
        assertEquals(KeyAction.FcitxKeyAction("4"), T9Keyboard.typed(KeyAction.FcitxKeyAction("4"), KeyActionListener.Source.Keyboard, tap, 20))
    }

    private fun letter(c: Char, again: Boolean) =
        KeyAction.SymAction(KeySym(0x01000000 + Keyboard.letterKey(c, again)), KeyStates.Virtual)

    @Test
    fun withNothingToOfferTheColumnCommitsPunctuation() {
        listOf(EngineBridge.T9State(), EngineBridge.T9State(composing = true)).forEach { state ->
            assertEquals("，", T9Keyboard.labels(state).first())
            assertEquals(KeyAction.CommitAction("，"), T9Keyboard.pick(state, 0))
            assertEquals(KeyAction.CommitAction("@"), T9Keyboard.pick(state, T9Keyboard.labels(state).lastIndex))
        }
    }

    /** Item [i] of the column, which is the ScrollView among the keyboard's children. */
    private fun T9Keyboard.columnItem(i: Int): View {
        val column = (0 until childCount).map { getChildAt(it) }.filterIsInstance<ScrollView>().single()
        return (column.getChildAt(0) as ViewGroup).getChildAt(i)
    }

    private fun tap(view: View) {
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            MotionEvent.obtain(0L, 0L, action, 1f, 1f, 0).also {
                view.dispatchTouchEvent(it)
                it.recycle()
            }
        }
    }

    // under the app, whose preferences the keys read, with placeholder strings, as NumberKeyboardTest
    @Test
    @Config(application = FcitxApplication::class)
    fun aClickReleasedOnAListSinceReplacedIsDropped() {
        val keyboard = T9Keyboard(PlaceholderStrings(RuntimeEnvironment.getApplication()), ThemePreset.PixelDark)
        val picked = ArrayList<KeyAction>()
        keyboard.keyActionListener = KeyActionListener { action, _ -> picked += action }
        // in a window, where View posts the click of a released item to the looper, as on a phone
        Robolectric.buildActivity(Activity::class.java).setup().get().setContentView(keyboard)
        val looper = shadowOf(Looper.getMainLooper())
        looper.idleFor(1, TimeUnit.SECONDS)
        assertTrue(keyboard.isAttachedToWindow)
        val twelve = EngineBridge.T9State(composing = true, syllables = List(12) { "ni" }, id = 1)
        keyboard.show(twelve)
        // punctuation has no item 10: picked from the list shown when it ran, the click threw
        tap(keyboard.columnItem(10))
        keyboard.show(EngineBridge.T9State())
        looper.idle()
        // nor did the user see the syllables of a list that came after their release
        tap(keyboard.columnItem(0))
        val next = twelve.copy(id = 2)
        keyboard.show(next)
        looper.idle()
        assertEquals(emptyList<KeyAction>(), picked)
        // a click on the list shown picks from it
        tap(keyboard.columnItem(0))
        looper.idle()
        assertEquals(listOf(T9Keyboard.pick(next, 0)), picked)
    }
}
