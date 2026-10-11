/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input

import android.view.Gravity
import android.view.View.MeasureSpec
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.math.roundToInt

/** The baseline a parent aligns by is the one the text is drawn on, padding counted once. */
@RunWith(RobolectricTestRunner::class)
class AutoScaleTextViewTest {

    @Test
    fun theBaselineCountsTheTopPaddingOnce() {
        val view = AutoScaleTextView(RuntimeEnvironment.getApplication()).apply {
            gravity = Gravity.TOP
            setPadding(0, 10, 0, 0)
            text = "你好"
        }
        view.measure(
            MeasureSpec.makeMeasureSpec(1000, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(200, MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 1000, 200)
        // drawn at the top of the content, its ascent below the padding
        assertEquals((10 - view.paint.fontMetrics.top).roundToInt(), view.baseline)
    }
}
