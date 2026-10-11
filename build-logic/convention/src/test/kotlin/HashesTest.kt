/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The downloads' pinned SHA-256s and the data descriptor's are in this one format: lowercase hex. */
class HashesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun aFilesSha256IsItsDigestInLowercaseHex() {
        val file = tmp.newFile().apply { writeText("abc") }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Of(file))
    }

    @Test
    fun bytesHashAsAFileOfThemDoes() {
        assertEquals(sha256Of(tmp.newFile().apply { writeText("abc") }), sha256Of("abc".toByteArray()))
    }

    @Test
    fun hexKeepsLeadingZeros() {
        assertEquals("000fa0ff", hex(byteArrayOf(0, 15, -96, -1)))
    }
}
