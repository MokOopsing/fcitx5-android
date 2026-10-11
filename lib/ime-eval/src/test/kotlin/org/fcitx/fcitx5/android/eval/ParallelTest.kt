/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.eval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Collections

class ParallelTest {

    @Test
    fun resultsComeInTheItemsOrderWhateverTheThreads() {
        val items = (0 until 10).toList()
        for (threads in listOf(1, 3, 4, 10, 50)) {
            assertEquals("$threads", items.map { it * it }, dealt(items, threads, { Unit }) { hand -> hand.map { it * it } })
        }
        assertEquals(emptyList<Int>(), dealt(emptyList<Int>(), 4, { Unit }) { hand -> hand })
    }

    @Test
    fun eachThreadHasItsOwnWorker() {
        val workers = Collections.synchronizedList(ArrayList<MutableList<Int>>())
        val seen = dealt((0 until 7).toList(), 3, { ArrayList<Int>().also { workers += it } }) { hand ->
            hand.map { add(it); toList() }
        }
        assertEquals(3, workers.size)
        // each item reached one worker only, whichever, and that worker had seen no other's
        assertEquals((0 until 7).toList(), workers.flatten().sorted())
        seen.forEachIndexed { item, upTo -> assertEquals(upTo, workers.single { item in it }.take(upTo.size)) }
    }

    @Test
    fun aWorkersFailureIsThrownAsItself() {
        assertThrows(IllegalStateException::class.java) {
            dealt((0 until 4).toList(), 2, { Unit }) { hand -> hand.map { check(it != 3); it } }
        }
        assertThrows(IllegalStateException::class.java) {
            dealt((0 until 4).toList(), 2, { Unit }) { hand -> hand.drop(1) }
        }
    }
}
