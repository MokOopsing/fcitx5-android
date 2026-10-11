/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.table

/**
 * Counts by a table entry's index, in open addressing: [TableUser] is asked for every entry a
 * code ranks, and a HashMap would box each index. A count of 0 is none; an index removed keeps
 * its slot until the table next fills, as forgetting is seldom, and the room then doubles only if
 * what is still counted needs it.
 */
internal class IndexCounts {
    private var keys = IntArray(INITIAL) { EMPTY }
    private var values = IntArray(INITIAL)
    private var used = 0

    /** The slots it has, taken or not. */
    val capacity: Int get() = keys.size

    /** The count of [index], 0 if it has none. */
    operator fun get(index: Int): Int {
        val i = find(index)
        return if (i < 0) 0 else values[i]
    }

    fun add(index: Int, n: Int) {
        // the slot first: taking it may resize, replacing values
        val i = slotOf(index)
        values[i] += n
    }

    /** Takes [index]'s count to none. @return whether it had one */
    fun remove(index: Int): Boolean {
        val i = find(index)
        if (i < 0 || values[i] == 0) return false
        values[i] = 0
        return true
    }

    /** Each index with a count, and the count. */
    fun forEach(visit: (index: Int, count: Int) -> Unit) {
        for (i in keys.indices) if (keys[i] != EMPTY && values[i] != 0) visit(keys[i], values[i])
    }

    // the slot holding index, or -1
    private fun find(index: Int): Int {
        val mask = keys.size - 1
        var i = slot(index, mask)
        while (true) {
            val k = keys[i]
            if (k == index) return i
            if (k == EMPTY) return -1
            i = (i + 1) and mask
        }
    }

    // the slot of index, taken for it if it has none
    private fun slotOf(index: Int): Int {
        require(index != EMPTY) { "index $index" }
        if ((used + 1) * 4 > keys.size * 3) makeRoom()
        val mask = keys.size - 1
        var i = slot(index, mask)
        while (keys[i] != EMPTY && keys[i] != index) i = (i + 1) and mask
        if (keys[i] == EMPTY) {
            keys[i] = index
            used++
        }
        return i
    }

    // the same room if the removed hold half the slots taken, else twice it
    private fun makeRoom() {
        val counted = values.count { it != 0 }
        resize(if (counted * 2 <= used) keys.size else keys.size * 2)
    }

    // the removed are left out
    private fun resize(capacity: Int) {
        val oldKeys = keys
        val oldValues = values
        keys = IntArray(capacity) { EMPTY }
        values = IntArray(capacity)
        used = 0
        for (i in oldKeys.indices) if (oldKeys[i] != EMPTY && oldValues[i] != 0) add(oldKeys[i], oldValues[i])
    }

    private fun slot(index: Int, mask: Int): Int {
        val h = index * -0x61c8864680b583ebL // the golden ratio: spreads indices differing in low bits
        return (h ushr 32).toInt() and mask
    }

    private companion object {
        const val INITIAL = 64
        const val EMPTY = -1
    }
}
