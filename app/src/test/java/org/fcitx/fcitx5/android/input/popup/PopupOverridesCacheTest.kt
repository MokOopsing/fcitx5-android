/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.popup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Every long press and swipe reads the user's edits; they are parsed only when the text changed. */
class PopupOverridesCacheTest {

    @Test
    fun theTextIsParsedAgainOnlyWhenItChanged() {
        var text = ""
        val cache = PopupOverridesCache { text }
        assertSame(PopupOverrides.Empty, cache.current)
        text = "e é è"
        val edited = cache.current
        assertEquals(listOf("é", "è"), edited["e"])
        assertSame(edited, cache.current)
        text = "e ê"
        assertEquals(listOf("ê"), cache.current["e"])
    }
}
