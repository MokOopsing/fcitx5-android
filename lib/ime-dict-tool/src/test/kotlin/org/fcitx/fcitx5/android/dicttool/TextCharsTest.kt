/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.dicttool

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextCharsTest {

    @Test
    fun hanIsTheUnifiedIdeographsAndExtensionA() {
        assertTrue(listOf('一', '鿿', '㐀', '䶿', '搭').all(TextChars::isHan))
        // NewWords packs a char as its distance from just below Extension A: none below it may count
        assertFalse(listOf('㏿', '，', '。', 'a').any(TextChars::isHan))
    }
}
