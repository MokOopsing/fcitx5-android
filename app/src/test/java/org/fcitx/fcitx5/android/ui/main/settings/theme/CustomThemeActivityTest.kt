/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.theme

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * A picked photo whose provider failed the read threw out of the copy, uncaught, and closed the
 * app with the keyboard; now the copy says so, and leaves no part-copy behind.
 */
class CustomThemeActivityTest {

    @get:Rule
    val folder = TemporaryFolder()

    private class Failing : InputStream() {
        var closed = false

        override fun read(): Int = throw IOException("provider gone")

        override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException("provider gone")

        override fun close() {
            closed = true
        }
    }

    @Test
    fun aReadThatFailsIsReportedAndLeavesNoCopy() = runTest {
        val input = Failing()
        val copy = folder.root.resolve("src.tmp")
        assertFalse(copyImage(input, copy).await())
        assertFalse(copy.exists())
        assertTrue(input.closed)
    }

    @Test
    fun aReadablePhotoIsCopiedWhole() = runTest {
        val bytes = ByteArray(3 * DEFAULT_BUFFER_SIZE + 5) { it.toByte() }
        val copy = folder.root.resolve("src.tmp")
        assertTrue(copyImage(ByteArrayInputStream(bytes), copy).await())
        assertArrayEquals(bytes, copy.readBytes())
    }
}
