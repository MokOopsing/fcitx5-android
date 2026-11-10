/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import org.fcitx.fcitx5.android.core.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class RimeSchemaActionsTest {

    @Test
    fun choicesExcludeFirstActionSeparatorsAndRimeManagementActions() {
        val choices = arrayOf(
            action("unknown-ascii-toggle"),
            action("luna_pinyin", id = 1),
            action("fcitx-rime-separator"),
            action("another-separator", isSeparator = true),
            action("fcitx-rime-next-schema"),
            action("fcitx-rime-deploy"),
            action("fcitx-rime-sync"),
            action("terra_pinyin", id = 2)
        ).rimeSchemaChoices()

        assertEquals(listOf("luna_pinyin", "terra_pinyin"), choices.map { it.name })
    }

    @Test
    fun currentChoiceIsMovedToFirstAndOtherChoicesKeepTheirOrder() {
        val choices = arrayOf(
            action("ascii", id = 1),
            action("schema-a", id = 2),
            action("schema-current", id = 3, checked = true),
            action("schema-b", id = 4)
        ).rimeSchemaChoices()

        assertEquals(listOf("schema-current", "schema-a", "schema-b"), choices.map { it.name })
    }

    @Test
    fun keepsRemainingOrderIfNoCurrentChoiceIsChecked() {
        val choices = arrayOf(
            action("ascii", id = 1),
            action("schema-a", id = 2),
            action("schema-b", id = 3)
        ).rimeSchemaChoices()

        assertEquals(listOf("schema-a", "schema-b"), choices.map { it.name })
    }

    private fun action(
        name: String,
        id: Int = 0,
        isSeparator: Boolean = false,
        checked: Boolean = false
    ) = Action(
        id = id,
        isSeparator = isSeparator,
        isCheckable = checked,
        isChecked = checked,
        name = name,
        icon = "",
        shortText = name,
        longText = name,
        menu = null
    )
}
