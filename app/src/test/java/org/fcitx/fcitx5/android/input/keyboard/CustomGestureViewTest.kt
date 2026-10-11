/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.view.MotionEvent
import org.fcitx.fcitx5.android.FcitxApplication
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A double tap is timed by the touches' own clock (their eventTime), not the wall clock, which jumps
 * when the system time is set. Under the app, whose preferences the gesture view reads.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = FcitxApplication::class)
class CustomGestureViewTest {

    private var clicks = 0
    private var doubleTaps = 0

    private fun view() = CustomGestureView(RuntimeEnvironment.getApplication()).apply {
        doubleTapEnabled = true
        onDoubleTapListener = { doubleTaps++ }
        setOnClickListener { clicks++ }
    }

    private fun CustomGestureView.tapAt(downTime: Long) {
        listOf(MotionEvent.ACTION_DOWN to downTime, MotionEvent.ACTION_UP to downTime + 10).forEach { (action, at) ->
            val event = MotionEvent.obtain(downTime, at, action, 5f, 5f, 0)
            onTouchEvent(event)
            event.recycle()
        }
    }

    @Test
    fun twoTapsCloseInTheirEventTimeAreADoubleTap() {
        view().apply {
            tapAt(1_000L)
            tapAt(1_050L)
        }
        assertEquals(1, clicks)
        assertEquals(1, doubleTaps)
    }

    @Test
    fun twoTapsFarApartInTheirEventTimeAreTwoTapsHoweverSoonTheyArrive() {
        view().apply {
            tapAt(1_000L)
            tapAt(11_000L)
        }
        assertEquals(2, clicks)
        assertEquals(0, doubleTaps)
    }
}
