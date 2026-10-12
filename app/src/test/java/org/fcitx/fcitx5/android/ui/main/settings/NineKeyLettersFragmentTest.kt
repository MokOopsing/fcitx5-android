/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings

import org.fcitx.fcitx5.android.engine.pinyin.NineKeyLetters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class NineKeyLettersFragmentTest {

    @Test
    fun aKeyEditedKeepsTheOthersAsTheyWere() {
        val edited = NineKeyLetters.STANDARD.with('9', "WXYZL")
        assertEquals("wxyzl", edited.letters('9'))
        assertEquals("jkl", edited.letters('5'))
        // put back as it was: as on a phone again
        assertSame(NineKeyLetters.STANDARD, edited.with('9', "wxyz"))
    }
}
