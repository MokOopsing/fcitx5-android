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
    fun choicesExcludeSeparatorsAndRimeManagementActions() {
        val choices = arrayOf(
            action("luna_pinyin"),
            action("fcitx-rime-separator"),
            action("another-separator", isSeparator = true),
            action("fcitx-rime-next-schema"),
            action("fcitx-rime-deploy"),
            action("fcitx-rime-sync"),
            action("terra_pinyin")
        ).rimeSchemaChoices()

        assertEquals(listOf("luna_pinyin", "terra_pinyin"), choices.map { it.name })
    }

    private fun action(name: String, isSeparator: Boolean = false) = Action(
        id = 0,
        isSeparator = isSeparator,
        isCheckable = false,
        isChecked = false,
        name = name,
        icon = "",
        shortText = name,
        longText = name,
        menu = null
    )
}
