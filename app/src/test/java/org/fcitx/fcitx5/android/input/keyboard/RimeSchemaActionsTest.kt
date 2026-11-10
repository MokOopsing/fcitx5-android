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
            action("luna_pinyin", id = 1, shortText = "Luna"),
            action("fcitx-rime-separator"),
            action("another-separator", isSeparator = true),
            action("fcitx-rime-next-schema"),
            action("fcitx-rime-deploy"),
            action("fcitx-rime-sync"),
            action("terra_pinyin", id = 2, shortText = "Terra")
        ).rimeSchemaChoices(currentSchemaName = null)

        assertEquals(listOf("luna_pinyin", "terra_pinyin"), choices.map { it.name })
    }

    @Test
    fun uniquelyMatchingDisplayNameMovesCurrentChoiceToFirstAndKeepsOtherOrder() {
        val choices = arrayOf(
            action("ascii", id = 1),
            action("schema-a", id = 2, shortText = "Schema A"),
            action("schema-current", id = 3, shortText = "Current Schema"),
            action("schema-b", id = 4, shortText = "Schema B")
        ).rimeSchemaChoices(currentSchemaName = "Current Schema")

        assertEquals(listOf("schema-current", "schema-a", "schema-b"), choices.map { it.name })
    }

    @Test
    fun keepsRemainingOrderIfCurrentSchemaNameDoesNotMatch() {
        val choices = arrayOf(
            action("ascii", id = 1),
            action("schema-a", id = 2, shortText = "Schema A"),
            action("schema-b", id = 3, shortText = "Schema B")
        ).rimeSchemaChoices(currentSchemaName = "Unknown")

        assertEquals(listOf("schema-a", "schema-b"), choices.map { it.name })
    }

    @Test
    fun keepsRemainingOrderIfCurrentSchemaNameIsNotUnique() {
        val choices = arrayOf(
            action("ascii", id = 1),
            action("schema-a", id = 2, shortText = "Same"),
            action("schema-b", id = 3, shortText = "Same")
        ).rimeSchemaChoices(currentSchemaName = "Same")

        assertEquals(listOf("schema-a", "schema-b"), choices.map { it.name })
    }

    private fun action(
        name: String,
        id: Int = 0,
        isSeparator: Boolean = false,
        shortText: String = name
    ) = Action(
        id = id,
        isSeparator = isSeparator,
        isCheckable = false,
        isChecked = false,
        name = name,
        icon = "",
        shortText = shortText,
        longText = "",
        menu = null
    )
}
