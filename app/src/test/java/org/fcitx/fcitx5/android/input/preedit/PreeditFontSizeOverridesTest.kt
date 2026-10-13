/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.preedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PreeditFontSizeOverridesTest {

    @Test
    fun submodeOverrideTakesPrecedenceOverInputMethodAndDefault() {
        val overrides = PreeditFontSizeOverrides.Empty
            .with(PreeditFontSizeOverrides.Scope("rime"), 20)
            .with(PreeditFontSizeOverrides.Scope("rime", "cangjie"), 32)

        assertEquals(32, overrides.fontSizeFor("rime", "cangjie", 16))
        assertEquals(20, overrides.fontSizeFor("rime", "pinyin", 16))
        assertEquals(16, overrides.fontSizeFor("other", null, 16))
    }

    @Test
    fun serializationRoundTripsAndInvalidRecordsAreIgnored() {
        val original = PreeditFontSizeOverrides.Empty
            .with(PreeditFontSizeOverrides.Scope("rime"), 20)
            .with(PreeditFontSizeOverrides.Scope("rime", "cangjie"), 32)

        val parsed = PreeditFontSizeOverrides.parse(
            "${original.serialize()}\nbroken\tline\nrime\tbad\t100\n\tmode\t24"
        )

        assertEquals(original.serialize(), parsed.serialize())
    }

    @Test
    fun removingOneScopeLeavesOtherOverrides() {
        val method = PreeditFontSizeOverrides.Scope("rime")
        val schema = PreeditFontSizeOverrides.Scope("rime", "cangjie")
        val overrides = PreeditFontSizeOverrides.Empty.with(method, 20).with(schema, 32)

        assertEquals(20, overrides.without(schema).fontSizeFor("rime", "cangjie", 16))
        assertEquals("", PreeditFontSizeOverrides.Empty.with(method, 20).without(method).serialize())
    }

    @Test
    fun withRejectsOutOfRangeSizes() {
        assertThrows(IllegalArgumentException::class.java) {
            PreeditFontSizeOverrides.Empty.with(PreeditFontSizeOverrides.Scope("rime"), 7)
        }
    }
}
