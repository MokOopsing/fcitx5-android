/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.dicttool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CountFitTest {

    @Test
    fun aLineThroughTheCountsIsRecoveredAndAppliedToOthers() {
        // log10 P = -7 + 0.5 log10(c + 1), exactly, at counts 9, 99 and 999
        val fit = CountFit.of(listOf(9L to -6.5f, 99L to -6f, 999L to -5.5f))
        assertEquals(-7.0, fit.a, 1e-6)
        assertEquals(0.5, fit.b, 1e-6)
        assertEquals(-5f, fit.prob(9999), 1e-6f)
        assertEquals(-7f, fit.prob(0), 1e-6f)
    }

    @Test
    fun scatterIsFittedByLeastSquares() {
        // two at the same count average out
        val fit = CountFit.of(listOf(9L to -6f, 9L to -7f, 99L to -5.5f, 99L to -6.5f))
        assertEquals(-6.5f, fit.prob(9), 1e-6f)
        assertEquals(-6f, fit.prob(99), 1e-6f)
    }

    @Test
    fun oneCountIsNoLine() {
        assertThrows(IllegalArgumentException::class.java) { CountFit.of(listOf(9L to -6f, 9L to -7f)) }
        assertThrows(IllegalArgumentException::class.java) { CountFit.of(emptyList()) }
        // log10(50) is inexact: summed 100 times, rounding leaves d above 0
        assertThrows(IllegalArgumentException::class.java) { CountFit.of(List(100) { 49L to -6f }) }
    }

    @Test
    fun countsApartThatReadAlikeAreNoLineEither() {
        // 10^17 + 1 is 10^17 as a double: the counts differ, their logs do not, and d is 0
        assertThrows(IllegalArgumentException::class.java) {
            CountFit.of(listOf(100_000_000_000_000_000L to -6f, 100_000_000_000_000_001L to -7f))
        }
    }
}
