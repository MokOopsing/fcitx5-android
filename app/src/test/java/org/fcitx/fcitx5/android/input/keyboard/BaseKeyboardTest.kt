/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import org.fcitx.fcitx5.android.FcitxApplication
import org.fcitx.fcitx5.android.core.InputMethodSubMode
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.ThemePreset
import org.fcitx.fcitx5.android.input.keyboard.BaseKeyboard.SwipeChoice
import org.fcitx.fcitx5.android.ui.main.settings.PlaceholderStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Under the vivo keypress workaround the keyboard hands each key a one-finger event of its own
 * ([forKey]). Built with id 0 for every finger, hold to talk followed the wrong one: lifting
 * another finger ended it, and sliding the held one up no longer cancelled. The id reaches the
 * hold through the space's long press ([KeyAction.SpaceLongPressAction]). A finger on a disabled
 * key gives no key the touch, but keeps it the keyboard's, or a second finger reached no key.
 *
 * What a letter's swipe types ([SwipeChoice], [isSwipe]): the key's own, the first of the long
 * press, or nothing where the user turned that long press off; the preview shows the same.
 *
 * A keyboard is built under the app, whose preferences the keys read; the keys' descriptions are
 * placeholders, as the tests have none of the app's strings.
 */
@RunWith(RobolectricTestRunner::class)
class BaseKeyboardTest {

    /** The input view's lifecycle, which the keys' long-press timers run in. */
    private class Owner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this)
    }

    private fun keyboard(actions: MutableList<KeyAction>) =
        TextKeyboard(PlaceholderStrings(RuntimeEnvironment.getApplication()), ThemePreset.PixelDark).apply {
            setViewTreeLifecycleOwner(Owner())
            keyActionListener = KeyActionListener { action, _ -> actions += action }
        }

    /** Fingers [ids] at [points] (x to y) as one event of [action]. */
    private fun touch(action: Int, ids: List<Int>, points: List<Pair<Float, Float>>): MotionEvent {
        val properties = ids.map { pid -> MotionEvent.PointerProperties().apply { id = pid } }
        val coords = points.map { (px, py) ->
            MotionEvent.PointerCoords().apply {
                x = px
                y = py
                pressure = 1f
                size = 1f
            }
        }
        return MotionEvent.obtain(100L, 200L, action, ids.size, properties.toTypedArray(), coords.toTypedArray(), 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    /** The middle of [key] in its keyboard: the key is placed in its row, the row in the keyboard. */
    private fun middleOf(key: View): Pair<Float, Float> {
        val row = key.parent as View
        return (row.left + (key.left + key.right) / 2f) to (row.top + (key.top + key.bottom) / 2f)
    }

    private fun exactly(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

    private fun twoFingers(): MotionEvent {
        val properties = intArrayOf(0, 3).map { pid -> MotionEvent.PointerProperties().apply { id = pid } }
        val coords = listOf(10f to 20f, 110f to 30f).map { (px, py) ->
            MotionEvent.PointerCoords().apply {
                x = px
                y = py
                pressure = 1f
                size = 1f
            }
        }
        return MotionEvent.obtain(
            100L, 200L,
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2, properties.toTypedArray(), coords.toTypedArray(),
            0, 0, 1f, 1f, 0, 0, 0, 0
        )
    }

    @Test
    fun theKeysEventKeepsItsFingersId() {
        val e = twoFingers().forKey(MotionEvent.ACTION_DOWN, 1, Rect(100, 0, 140, 50))
        assertEquals(1, e.pointerCount)
        assertEquals("the second finger's id, not 0", 3, e.getPointerId(0))
        assertEquals(MotionEvent.ACTION_DOWN, e.actionMasked)
    }

    @Test
    fun theKeysEventIsInTheKeysCoordinates() {
        val e = twoFingers().forKey(MotionEvent.ACTION_MOVE, 1, Rect(100, 0, 140, 50))
        assertEquals(10f, e.x, 0f)
        assertEquals(30f, e.y, 0f)
        assertEquals(100L, e.downTime)
        assertEquals(200L, e.eventTime)
    }

    @Test
    fun theFirstFingerKeepsItsIdToo() {
        val e = twoFingers().forKey(MotionEvent.ACTION_UP, 0, Rect(0, 0, 40, 50))
        assertEquals(0, e.getPointerId(0))
        assertEquals(10f, e.x, 0f)
        assertEquals(20f, e.y, 0f)
    }

    /** The finger's properties and coordinates are refilled for each event: one built stays as it was. */
    @Test
    fun aKeysEventStaysItsOwnOnceTheNextIsBuilt() {
        val fingers = twoFingers()
        val first = fingers.forKey(MotionEvent.ACTION_MOVE, 0, Rect(0, 0, 40, 50))
        fingers.forKey(MotionEvent.ACTION_MOVE, 1, Rect(100, 0, 140, 50))
        assertEquals(0, first.getPointerId(0))
        assertEquals(10f, first.x, 0f)
        assertEquals(20f, first.y, 0f)
    }

    @Test
    @Config(application = FcitxApplication::class)
    fun holdingTheSpaceSaysWhichFingerHoldsIt() {
        val actions = mutableListOf<KeyAction>()
        val space = keyboard(actions).space
        space.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN, listOf(3), listOf(5f to 5f)))
        // what the long-press timer calls once the delay is up
        space.performLongClick()
        assertEquals(KeyAction.SpaceLongPressAction(pointer = 3), actions.lastOrNull())
    }

    @Test
    @Config(application = FcitxApplication::class)
    fun underTheVivoWorkaroundAFingerOnADisabledKeyKeepsTheTouchForTheNext() {
        val workaround = AppPrefs.getInstance().advanced.vivoKeypressWorkaround
        val was = workaround.getValue()
        workaround.setValue(true)
        try {
            val keyboard = keyboard(mutableListOf()).apply {
                measure(exactly(1080), exactly(800))
                layout(0, 0, 1080, 800)
            }
            keyboard.space.isEnabled = false
            val onSpace = middleOf(keyboard.space)
            assertTrue("the touch stays the keyboard's", keyboard.onTouchEvent(touch(MotionEvent.ACTION_DOWN, listOf(0), listOf(onSpace))))
            assertFalse(keyboard.space.isPressed)
            val secondDown = MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            keyboard.onTouchEvent(touch(secondDown, listOf(0, 1), listOf(onSpace, middleOf(keyboard.caps))))
            assertTrue("the second finger's key has it", keyboard.caps.isPressed)
        } finally {
            workaround.setValue(was)
        }
    }

    @Test
    fun aSwipeTypesTheKeysOwnTheLongPresssFirstOrNothing() {
        val own = KeyAction.FcitxKeyAction("1")
        assertEquals(own to KeyActionListener.Source.Keyboard, SwipeChoice.of(null).typed(own))
        assertNull("its long press turned off", SwipeChoice.of("").typed(own))
        assertEquals(KeyAction.FcitxKeyAction("é") to KeyActionListener.Source.Popup, SwipeChoice.of("é").typed(own))
        assertEquals(KeyAction.CommitAction("qu") to KeyActionListener.Source.Popup, SwipeChoice.of("qu").typed(own))
    }

    @Test
    fun theSwipesPreviewIsWhatItTypes() {
        assertEquals("1", SwipeChoice.of(null).preview("1"))
        assertEquals("nothing, as nothing is typed", "", SwipeChoice.of("").preview("1"))
        assertEquals("é", SwipeChoice.of("é").preview("1"))
    }

    @Test
    fun onlyAnUpNoOneTookNorTheSystemCancelledIsASwipe() {
        fun up(consumed: Boolean = false, cancelled: Boolean = false) = CustomGestureView.Event(
            CustomGestureView.GestureType.Up, consumed, 0f, 0f, 0, 0, 0, 50, cancelled
        )
        assertTrue(up().isSwipe(SwipeSymbolDirection.Down))
        assertFalse("taken by the long press", up(consumed = true).isSwipe(SwipeSymbolDirection.Down))
        assertFalse("cancelled", up(cancelled = true).isSwipe(SwipeSymbolDirection.Down))
        assertFalse("the other way", up().isSwipe(SwipeSymbolDirection.Up))
        assertFalse("swipes off", up().isSwipe(SwipeSymbolDirection.Disabled))
    }

    @Test
    fun theSpaceShowsTheSubModeToo() {
        assertEquals("拼音", spaceLabel("拼音", InputMethodSubMode()))
        assertEquals("拼音 ascii", spaceLabel("拼音", InputMethodSubMode("ascii", "A", "")))
        assertEquals("拼音 A", spaceLabel("拼音", InputMethodSubMode("", "A", "")))
    }
}
