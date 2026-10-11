/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.dicttool

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MixerTest {

    @Test
    fun aTrigramOfTheBaseWithoutItsContextBigramIsAnErrorOfTheInput() {
        // 好 你 is no bigram of the model
        val text = ReadersTest.TINY_ARPA.trimIndent().replace("你 好 你", "好 你 好")
        val base = ArpaModel.read(text.reader().buffered(), "lm")
        val counts = ChatCounts(base)
        val e = assertThrows(IllegalArgumentException::class.java) { Mixer(base, counts, KneserNey(counts), 0.5, 1, 1).mix() }
        assertTrue(e.message, e.message!!.contains("\"好 你 好\""))
    }
}
