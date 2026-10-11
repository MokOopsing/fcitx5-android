/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.table

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexCountsTest {

    private fun IndexCounts.all() = HashMap<Int, Int>().also { all -> forEach { index, n -> all[index] = n } }

    @Test
    fun countsAddUpByIndexAndNoneIsZero() {
        val counts = IndexCounts()
        assertEquals(0, counts[7])
        counts.add(7, 2)
        counts.add(7, 1)
        counts.add(0, 1)
        assertEquals(3, counts[7])
        assertEquals(1, counts[0])
        assertEquals(0, counts[8])
        assertThrows(IllegalArgumentException::class.java) { counts.add(-1, 1) }
    }

    @Test
    fun aRemovedCountIsNoneAndNotVisitedUntilAddedAgain() {
        val counts = IndexCounts()
        counts.add(5, 4)
        assertTrue(counts.remove(5))
        assertFalse(counts.remove(5))
        assertFalse(counts.remove(6))
        assertEquals(0, counts[5])
        assertEquals(emptyMap<Int, Int>(), counts.all())
        counts.add(5, 1)
        assertEquals(mapOf(5 to 1), counts.all())
    }

    /** Far past the first slots, of indices alike in their low bits: all kept as it grows, the removed not among them. */
    @Test
    fun manyIndicesAreAllKeptAsTheTableGrows() {
        val counts = IndexCounts()
        counts.add(1 shl 20, 9)
        counts.remove(1 shl 20)
        for (i in 0 until 1000) counts.add(i shl 10, i + 1)
        for (i in 0 until 1000) assertEquals(i + 1, counts[i shl 10])
        assertEquals(0, counts[1 shl 20])
        assertEquals((0 until 1000).associate { (it shl 10) to it + 1 }, counts.all())
    }

    /** Indices counted and forgotten in turn: their slots are taken again, the table not doubled at each fill. */
    @Test
    fun forgottenIndicesMakeRoomRatherThanGrowTheTable() {
        val counts = IndexCounts()
        val capacity = counts.capacity
        for (i in 0 until 10_000) {
            counts.add(i, 1)
            counts.remove(i)
        }
        assertEquals(capacity, counts.capacity)
        counts.add(3, 2)
        assertEquals(mapOf(3 to 2), counts.all())
        // what is still counted grows it as before
        for (i in 0 until 1000) counts.add(i, 1)
        assertTrue(counts.capacity > capacity)
        assertEquals(3, counts[3])
    }
}
