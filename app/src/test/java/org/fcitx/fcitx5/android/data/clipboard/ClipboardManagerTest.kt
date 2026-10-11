/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.clipboard

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipboardManagerTest {

    // copied last at 500, 450 and 300, newest first, as ClipboardDao.unpinnedTimestampAt has them
    private val unpinned = listOf(500L, 450L, 300L)

    @Test
    fun theLastKeptIsTheLimitthNewest() = runTest {
        assertEquals(300L, lastKept(3, 900L) { unpinned.getOrNull(it) })
        // room for one more: none is outdated
        assertNull(lastKept(4, 900L) { unpinned.getOrNull(it) })
    }

    @Test
    fun noneIsKeptWithNoRoomForOne() = runTest {
        // everything before now goes, the store not asked
        assertEquals(900L, lastKept(0, 900L) { throw AssertionError("asked for entry $it") })
        assertEquals(900L, lastKept(-1, 900L) { throw AssertionError("asked for entry $it") })
    }
}
