/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.popup

import android.graphics.Rect
import android.view.ViewGroup
import android.widget.TextView
import org.fcitx.fcitx5.android.core.Action
import org.fcitx.fcitx5.android.data.theme.ThemePreset
import org.fcitx.fcitx5.android.input.keyboard.KeyAction
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

    private val actions = listOf(action(10, "luna_pinyin"), action(11, "terra_pinyin", checked = true))

    private fun popup(onSelect: (Action) -> Unit = {}): RimeSchemaPopupUi =
        RimeSchemaPopupUi(
            ctx = RuntimeEnvironment.getApplication(),
            theme = ThemePreset.MaterialLight,
            outerBounds = Rect(0, 0, 500, 700),
            triggerBounds = Rect(230, 640, 270, 690),
            actions = actions,
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
        var selected: Action? = null
        val ui = popup { selected = it }
        val rows = ui.root.getChildAt(0) as ViewGroup

        assertTrue((rows.getChildAt(0) as TextView).performClick())
        assertEquals(10, selected?.id)
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
        val longActions = listOf(action(12, "a".repeat(100)))
        listOf(
            Rect(0, 640, 40, 690),
            Rect(460, 640, 500, 690)
        ).forEach { trigger ->
            val ui = RimeSchemaPopupUi(
                context,
                ThemePreset.MaterialLight,
                Rect(0, 0, 500, 700),
                trigger,
                longActions,
                0
            ) {}
            val left = ui.triggerBounds.left + ui.offsetX

            assertTrue(left >= context.dp(12))
            assertTrue(left + ui.root.layoutParams.width <= 500 - context.dp(12))
            assertTrue(ui.root.layoutParams.width <= 500 - 2 * context.dp(12))
        }
    }

    private fun action(id: Int, name: String, checked: Boolean = false) = Action(
        id = id,
        isSeparator = false,
        isCheckable = true,
        isChecked = checked,
        name = name,
        icon = "",
        shortText = name,
        longText = name,
        menu = null
    )
}
