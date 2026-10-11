/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.bar

import org.junit.Assert.assertEquals
import org.junit.Test

/** What the bar's clipboard suggestion shows of a long clip. */
class KawaiiBarComponentTest {

    @Test
    fun aClipIsCutAfter42CharactersAndNeverInsideAnEmoji() {
        assertEquals("a".repeat(42), clipboardPreview("a".repeat(50)))
        // the 42nd is an emoji, two UTF-16 units
        assertEquals("a".repeat(41) + "😀", clipboardPreview("a".repeat(41) + "😀b"))
        assertEquals("复制", clipboardPreview("复制"))
    }
}
