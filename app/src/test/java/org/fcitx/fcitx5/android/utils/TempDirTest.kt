/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.utils

import org.fcitx.fcitx5.android.FcitxApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Imports unpack into a temporary directory deleted after them: two at once must not share one. */
@RunWith(RobolectricTestRunner::class)
@Config(application = FcitxApplication::class)
class TempDirTest {

    @Test
    fun twoAtOnceHaveTheirOwnAndTheFirstToEndLeavesTheOthersFiles() {
        withTempDir { outer ->
            outer.resolve("a").writeText("a")
            val inner = withTempDir {
                assertNotEquals(outer, it)
                it
            }
            assertFalse(inner.exists())
            assertEquals("a", outer.resolve("a").readText())
        }
    }

    @Test
    fun theDirectoryIsMadeEmptyAndDeletedWhenTheBlockFails() {
        var dir: File? = null
        assertThrows(IllegalStateException::class.java) {
            withTempDir {
                dir = it
                assertTrue(it.isDirectory)
                assertEquals(emptyList<String>(), it.list()?.toList())
                error("import failed")
            }
        }
        assertFalse(dir!!.exists())
    }
}
