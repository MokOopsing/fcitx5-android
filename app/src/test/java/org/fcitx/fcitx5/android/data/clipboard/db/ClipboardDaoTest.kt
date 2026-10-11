/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.clipboard.db

import androidx.room.Room
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * A purge takes only the rows its undo snackbar showed, so a row another delete marked in the
 * meantime can still be undone; entries past the history limit, which no undo offers back, go
 * outright rather than waiting, marked, for a purge.
 */
@RunWith(RobolectricTestRunner::class)
class ClipboardDaoTest {

    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ClipboardDatabase::class.java)
        .build()
    private val dao = db.clipboardDao()

    @After
    fun close() = db.close()

    private suspend fun add(text: String, timestamp: Long = 1000L, pinned: Boolean = false) =
        dao.get(dao.insert(ClipboardEntry(text = text, timestamp = timestamp, pinned = pinned)))!!.id

    @Test
    fun aPurgeTakesOnlyTheMarkedRowsItIsGiven() = runTest {
        val shown = add("a")
        val markedSince = add("b")
        val kept = add("c")
        dao.markAsDeleted(shown, markedSince)
        dao.purgeMarked(shown, kept)
        dao.undoDelete(shown, markedSince)
        assertNull(dao.get(shown))
        assertNotNull(dao.get(markedSince))
        assertNotNull(dao.get(kept))
    }

    @Test
    fun aPurgeOfNoIdsTakesNoneAndOneOfAllMarkedTakesEvery() = runTest {
        val marked = add("a")
        val kept = add("b")
        dao.markAsDeleted(marked)
        dao.purgeMarked()
        dao.undoDelete(marked)
        assertNotNull(dao.get(marked))
        dao.markAsDeleted(marked)
        dao.purgeAllMarked()
        dao.undoDelete(marked)
        assertNull(dao.get(marked))
        assertNotNull(dao.get(kept))
    }

    @Test
    fun theLastKeptGoesByWhenLastCopied() = runTest {
        // copied at 100, 200, 300 and 500, and the second again at 450: room for 3 keeps it, the third and the fourth
        add("a", timestamp = 100L)
        val copiedAgain = add("b", timestamp = 200L)
        add("c", timestamp = 300L)
        add("d", timestamp = 500L)
        dao.updateTime(copiedAgain, 450L)
        add("pinned", timestamp = 900L, pinned = true)
        dao.markAsDeleted(add("deleted", timestamp = 800L))
        assertEquals(300L, dao.unpinnedTimestampAt(2))
        // room for 5: none is outdated
        assertNull(dao.unpinnedTimestampAt(4))
    }

    @Test
    fun anOutdatedEntryGoesOutrightAndOneAwaitingItsUndoStays() = runTest {
        val outdated = add("old", timestamp = 100L)
        val awaitingUndo = add("deleted", timestamp = 200L)
        val pinned = add("pinned", timestamp = 50L, pinned = true)
        val recent = add("new", timestamp = 1000L)
        dao.markAsDeleted(awaitingUndo)
        dao.deleteUnpinnedEarlierThan(500L)
        dao.undoDelete(outdated, awaitingUndo)
        assertNull(dao.get(outdated))
        assertNotNull(dao.get(awaitingUndo))
        assertNotNull(dao.get(pinned))
        assertNotNull(dao.get(recent))
    }
}
