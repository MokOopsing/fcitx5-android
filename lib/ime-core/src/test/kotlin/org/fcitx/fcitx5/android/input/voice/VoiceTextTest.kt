/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceTextTest {

    private fun check(expected: String, raw: String) = assertEquals(raw, expected, VoiceText.clean(raw))

    @Test
    fun spelledLettersAreOneAbbreviation() {
        // \b back fails this only on Java up to 18 (CI's 17), and éA B below on 19+
        check("订了CEP的票", "订了 C E P 的票")
        check("an AB test", "an A B test")
    }

    @Test
    fun aLetterGluedToAWordIsNotSpelled() {
        check("3C E", "3C E")
        check("CE Pa", "C E Pa")
        check("x_A B", "x_A B")
        check("éA B", "éA B")
    }
}
