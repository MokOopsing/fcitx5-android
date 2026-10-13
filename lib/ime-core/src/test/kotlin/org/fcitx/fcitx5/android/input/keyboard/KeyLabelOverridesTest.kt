/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyLabelOverridesTest {

    @Test
    fun labelsBelongOnlyToTheirInputMethod() {
        val labels = KeyLabelOverrides.Empty.with(
            KeyLabelOverrides.Scope("engine-cangjie"), "a", "日"
        )

        assertEquals("日", labels.labelsFor("engine-cangjie")["a"])
        assertNull(labels.labelsFor("engine-wubi")["a"])
        assertNull(labels.labelsFor("engine-cangjie")["s"])
    }

    @Test
    fun editsSerializeAndParse() {
        val labels = KeyLabelOverrides.Empty
            .with(KeyLabelOverrides.Scope("engine-cangjie"), "a", "日")
            .with(KeyLabelOverrides.Scope("engine-cangjie", "cangjie5"), "s", "尸")
            .with(KeyLabelOverrides.Scope("keyboard-us"), "a", "α")

        assertTrue(labels.serialize().lineSequence().any {
            it == "engine-cangjie\time\t-\ta\t日"
        })
        assertEquals(labels, KeyLabelOverrides.parse(labels.serialize()))
    }

    @Test
    fun readsPreviousFourFieldTabRecords() {
        val labels = KeyLabelOverrides.parse(
            "rime\t-\ta\tA\nrime\tLuna Pinyin\ts\t尸"
        )

        assertEquals("A", labels.labelsFor("rime")["a"])
        assertEquals("尸", labels.labelsFor("rime", "Luna Pinyin")["s"])
    }

    @Test
    fun malformedRecordsAreIgnored() {
        val labels = KeyLabelOverrides.parse(
            "engine-cangjie a 日\nengine-cangjie aa 木\nengine-cangjie b 两 个\nbad"
        )

        assertEquals(mapOf("a" to "日"), labels.labelsFor(KeyLabelOverrides.Scope("engine-cangjie")))
    }

    @Test
    fun submodeLabelsOverrideInputMethodLabelsAndFallBackPerKey() {
        val labels = KeyLabelOverrides.Empty
            .with(KeyLabelOverrides.Scope("rime"), "a", "A")
            .with(KeyLabelOverrides.Scope("rime"), "s", "S")
            .with(KeyLabelOverrides.Scope("rime", "Luna Pinyin"), "a", "月")

        assertEquals(mapOf("a" to "月", "s" to "S"), labels.labelsFor("rime", "Luna Pinyin"))
        assertEquals(mapOf("a" to "A", "s" to "S"), labels.labelsFor("rime", "luna_pinyin"))
    }

    @Test
    fun clearingAKeyOrScopeLeavesOtherScopesAlone() {
        val baseScope = KeyLabelOverrides.Scope("rime")
        val schemaScope = KeyLabelOverrides.Scope("rime", "Luna Pinyin")
        val otherScope = KeyLabelOverrides.Scope("engine-wubi")
        val labels = KeyLabelOverrides.Empty
            .with(baseScope, "a", "A")
            .with(schemaScope, "a", "月")
            .with(otherScope, "a", "工")

        val withoutKey = labels.without(schemaScope, "a")
        assertNull(withoutKey.labelsFor(schemaScope)["a"])
        assertEquals("A", withoutKey.labelsFor("rime", "Luna Pinyin")["a"])
        assertEquals("工", withoutKey.labelsFor(otherScope)["a"])

        val withoutScope = withoutKey.without(baseScope)
        assertTrue(withoutScope.labelsFor(baseScope).isEmpty())
        assertEquals("工", withoutScope.labelsFor(otherScope)["a"])
        assertFalse(withoutScope.isEmpty)
    }

    @Test
    fun labelsCannotBreakTheLineBasedFormat() {
        assertFalse(KeyLabelOverrides.isValidLabel("日 木"))
        assertFalse(KeyLabelOverrides.isValidLabel(""))
        assertTrue(KeyLabelOverrides.isValidLabel("日月"))
    }

    @Test
    fun submodeNamesWithSpacesSurviveStorage() {
        val labels = KeyLabelOverrides.Empty.with(
            KeyLabelOverrides.Scope("rime", "Luna Pinyin"), "a", "月"
        )

        assertEquals(labels, KeyLabelOverrides.parse(labels.serialize()))
    }
}
