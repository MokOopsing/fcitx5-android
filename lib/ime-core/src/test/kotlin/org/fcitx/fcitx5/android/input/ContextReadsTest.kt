/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A context read the service queued is made only if it is still the last one asked for, and never
 * in a field other than the one it was asked in: a password's, read once the input left it, would
 * pass the password's text to the engine.
 */
class ContextReadsTest {

    private val reads = ContextReads()

    @Test
    fun ofABurstOnlyTheLastReadIsMade() {
        val first = reads.ask()
        val second = reads.ask()
        assertFalse(reads.isLatest(first))
        assertTrue(reads.isLatest(second))
        // a read made at once, as the input starts, still drops one queued before it
        reads.ask()
        assertFalse(reads.isLatest(second))
    }

    @Test
    fun aReadAskedForBeforeTheInputStartedOrFinishedIsNotMade() {
        val queued = reads.ask()
        reads.forget()
        assertFalse("finished", reads.isLatest(queued))
        val next = reads.ask()
        reads.forget()
        assertFalse("started again", reads.isLatest(next))
        assertTrue(reads.isLatest(reads.ask()))
    }
}
