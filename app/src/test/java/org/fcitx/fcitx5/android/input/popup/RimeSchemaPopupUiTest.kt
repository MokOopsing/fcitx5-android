/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.popup

import android.graphics.Rect
import android.view.ViewGroup
import android.widget.TextView
import org.fcitx.fcitx5.android.data.theme.ThemePreset
import org.fcitx.fcitx5.android.input.keyboard.KeyAction
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import splitties.dimensions.dp
import kotlin.math.ceil

@RunWith(RobolectricTestRunner::class)
class RimeSchemaPopupUiTest {

    private val emojiPickerAction = KeyAction.PickerSwitchAction(PickerWindow.Key.Emoji)
    private val items = listOf(
        item(10, "luna_pinyin"),
        item(11, "terra_pinyin"),
        RimeSchemaPopupItem("Emoji", emojiPickerAction)
    )

    private fun popup(onSelect: (KeyAction) -> Unit = {}): RimeSchemaPopupUi =
        RimeSchemaPopupUi(
            ctx = RuntimeEnvironment.getApplication(),
            theme = ThemePreset.MaterialLight,
            outerBounds = Rect(0, 0, 500, 700),
            triggerBounds = Rect(230, 640, 270, 690),
            items = items,
            selectedIndex = 1,
            onSelect = onSelect
        )

    @Test
    fun releasingWithoutSlidingKeepsTheMenuOpen() {
        assertNull(popup().onTrigger())
    }

    @Test
    fun slidingSelectsTheRowUnderTheFinger() {
        val ui = popup()

        ui.onChangeFocus(10f, 10f)

        assertEquals(KeyAction.RimeSchemaAction(10), ui.onTrigger())
    }

    @Test
    fun tappingAListItemSelectsIt() {
        var selected: KeyAction? = null
        val ui = popup { selected = it }
        val rows = ui.root.getChildAt(0) as ViewGroup

        assertTrue((rows.getChildAt(0) as TextView).performClick())
        assertEquals(KeyAction.RimeSchemaAction(10), selected)
    }

    @Test
    fun emojiPickerIsLastAndCanBeClickedOrSwipeSelected() {
        var clicked: KeyAction? = null
        val ui = popup { clicked = it }
        val rows = ui.root.getChildAt(0) as ViewGroup

        assertEquals("Emoji", (rows.getChildAt(rows.childCount - 1) as TextView).text)
        assertTrue((rows.getChildAt(rows.childCount - 1) as TextView).performClick())
        assertEquals(emojiPickerAction, clicked)

        ui.onChangeFocus(10f, 2 * RuntimeEnvironment.getApplication().dp(48) + 10f)
        assertEquals(emojiPickerAction, ui.onTrigger())
    }

    @Test
    fun theListIsAttachedToTheGlobeInsteadOfTheScreenCenter() {
        val ui = popup()

        assertEquals(-ui.root.layoutParams.height, ui.offsetY)
        assertEquals(
            ui.triggerBounds.centerX(),
            ui.triggerBounds.left + ui.offsetX + ui.root.layoutParams.width / 2
        )
        assertTrue(ui.triggerBounds.top + ui.offsetY >= 0)
    }

    @Test
    fun widthFitsTheWidestLabelAndItsPadding() {
        val ui = popup()
        val rows = ui.root.getChildAt(0) as ViewGroup
        val label = (rows.getChildAt(1) as TextView).text.toString()
        val expectedWidth = ceil((rows.getChildAt(1) as TextView).paint.measureText(label)).toInt() +
            RuntimeEnvironment.getApplication().dp(32)

        assertEquals(expectedWidth, ui.root.layoutParams.width)
        assertTrue(ui.root.layoutParams.width < RuntimeEnvironment.getApplication().dp(280))
    }

    @Test
    fun adaptiveWidthKeepsSpaceAtBothScreenEdges() {
        val context = RuntimeEnvironment.getApplication()
        val longItems = listOf(item(12, "a".repeat(100)))
        listOf(
            Rect(0, 640, 40, 690),
            Rect(460, 640, 500, 690)
        ).forEach { trigger ->
            val ui = RimeSchemaPopupUi(
                context,
                ThemePreset.MaterialLight,
                Rect(0, 0, 500, 700),
                trigger,
                longItems,
                0
            ) {}
            val left = ui.triggerBounds.left + ui.offsetX

            assertTrue(left >= context.dp(12))
            assertTrue(left + ui.root.layoutParams.width <= 500 - context.dp(12))
            assertTrue(ui.root.layoutParams.width <= 500 - 2 * context.dp(12))
        }
    }

    private fun item(id: Int, label: String) =
        RimeSchemaPopupItem(label, KeyAction.RimeSchemaAction(id))
}
