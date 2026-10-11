/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.dicttool

import org.fcitx.fcitx5.android.engine.data.PinyinDataBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class PinyinDictReaderTest {

    @Test
    fun aRemovedWordStaysOutThoughTheOtherListsNameIt() {
        val reader = PinyinDictReader(
            PinyinDataBuilder(),
            removed = setOf("好", "般若"),
            readings = mapOf("好" to listOf("hao")),
            misread = mapOf("般若" to listOf("bo're", "ban'ruo")),
        )
        reader.read("好\thao\t0\n般若\tban'ruo\t0\n你\tni\t0\n".reader().buffered(), "dict")
        reader.corrected()
        assertEquals(2, reader.corrections)
        // 你 alone
        assertEquals(1, reader.entries)
    }
}
