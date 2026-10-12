/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.lattice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoarseWordsTest {

    @Test
    fun theListIsItsWordsAloneNoCommentsNorBlanks() {
        val list = CoarseWords.LIST
        assertTrue("小穴" in list && "fuck" in list)
        assertTrue(list.none { it.isBlank() || it.startsWith("#") || it != it.trim() })
        assertEquals(list.distinct(), list)
    }

    @Test
    fun wordsTheVocabularyLacksAreLeftOut() {
        val coarse = CoarseWords.of({ if (it == "b") 3 else -1 }, listOf("a", "b"))
        assertTrue(coarse.has(3))
        assertFalse(coarse.has(0))
        assertFalse(coarse.has(-1))
        assertFalse(CoarseWords.NONE.has(3))
    }
}
