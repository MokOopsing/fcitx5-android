/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** A theme is decoded along with what was read of it; by the caller's [Json], not the default one. */
class NostalgicSerializerTest {

    @Serializable
    data class Point(val x: Int)

    private val keys = NostalgicSerializer(Point.serializer()) { it.jsonObject.keys }

    @Test
    fun theValueComesWithWhatWasReadOfTheElement() {
        assertEquals(Point(1) to setOf("x"), Json.decodeFromString(keys, """{"x":1}"""))
    }

    @Test
    fun theValueIsDecodedByTheCallersJson() {
        val lenient = Json { ignoreUnknownKeys = true }
        assertEquals(Point(1) to setOf("x", "y"), lenient.decodeFromString(keys, """{"x":1,"y":2}"""))
    }
}
