/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Undoing the undo of a batch added put its items back one by one, each drawn, told and given a
 * Snackbar of its own: thousands of them held the main thread again.
 */
class BaseDynamicListUiTest {

    @Test
    fun theEndOfTheListIsPutBackInOne() {
        // b and c added to a, then taken off again
        assertEquals(listOf("b", "c"), removedTail(1, listOf(1 to "b", 2 to "c")))
        assertEquals(listOf("a"), removedTail(0, listOf(0 to "a")))
    }

    @Test
    fun anythingElseIsPutBackWhereEachWas() {
        // not the end: the list has grown since
        assertNull(removedTail(3, listOf(1 to "b", 2 to "c")))
        // not one run: a gap between them
        assertNull(removedTail(1, listOf(1 to "b", 3 to "d")))
        assertNull(removedTail(0, emptyList<Pair<Int, String>>()))
    }
}
