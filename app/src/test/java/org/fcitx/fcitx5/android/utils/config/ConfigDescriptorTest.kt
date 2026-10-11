/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.utils.config

import arrow.core.Either
import org.fcitx.fcitx5.android.core.RawConfig
import org.fcitx.fcitx5.android.utils.config.ConfigDescriptor.Companion.ParseException
import org.fcitx.fcitx5.android.utils.config.ConfigDescriptor.ConfigList.ConfigListValue
import org.junit.Assert.assertEquals
import org.junit.Test

/** fcitx describes each option of a settings page; a description that does not parse is an error, not a crash. */
class ConfigDescriptorTest {

    private fun option(type: String, vararg fields: Pair<String, String>) = RawConfig(
        "Size",
        arrayOf(RawConfig("Type", type)) + fields.map { (name, value) -> RawConfig(name, value) }
    )

    @Test
    fun anIntOptionIsReadWithItsDefaultAndBounds() {
        val raw = option("Integer", "DefaultValue" to "3", "IntMin" to "1", "IntMax" to "9")
        assertEquals(
            Either.Right(ConfigDescriptor.ConfigInt("Size", defaultValue = 3, intMax = 9, intMin = 1)),
            ConfigDescriptor.parse(raw)
        )
    }

    @Test
    fun aNonNumericIntIsABadDescription() {
        for (field in listOf("DefaultValue", "IntMin", "IntMax")) {
            val raw = option("Integer", field to "x")
            assertEquals(field, Either.Left(ParseException.BadFormDesc(raw)), ConfigDescriptor.parse(raw))
        }
    }

    @Test
    fun aNonNumericIntInAListIsABadDescription() {
        fun list(vararg values: String) = RawConfig(
            "Sizes",
            arrayOf(
                RawConfig("Type", "List|Integer"),
                RawConfig("DefaultValue", values.mapIndexed { i, v -> RawConfig("$i", v) }.toTypedArray())
            )
        )
        val good = ConfigDescriptor.parse(list("1", "2")).getOrNull() as ConfigDescriptor.ConfigList
        assertEquals(listOf(ConfigListValue.IntValue(1), ConfigListValue.IntValue(2)), good.defaultValue)
        val bad = list("1", "x")
        assertEquals(Either.Left(ParseException.BadFormDesc(bad)), ConfigDescriptor.parse(bad))
    }
}
