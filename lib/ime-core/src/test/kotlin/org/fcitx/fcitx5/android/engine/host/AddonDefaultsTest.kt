/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddonDefaultsTest {

    @Test
    fun punctuationAfterALetterOrDigitIsChineseToo() {
        assertEquals("HalfWidthPuncAfterLetterOrNumber=False\n", AddonDefaults.punctuation(""))
        assertEquals(
            "HalfWidthPuncAfterLetterOrNumber=False\nTypePairedPunctuationsTogether=True\n\n[Hotkey]\n0=Control+period\n",
            AddonDefaults.punctuation("HalfWidthPuncAfterLetterOrNumber=True\nTypePairedPunctuationsTogether=True\n\n[Hotkey]\n0=Control+period\n"),
        )
        assertEquals(
            "HalfWidthPuncAfterLetterOrNumber=False\n[Hotkey]\n0=Control+period\n",
            AddonDefaults.punctuation("[Hotkey]\n0=Control+period\n"),
        )
    }

    @Test
    fun aConfigThatHasItIsLeftAlone() {
        assertNull(AddonDefaults.punctuation("TypePairedPunctuationsTogether=False\nHalfWidthPuncAfterLetterOrNumber=False\n"))
    }
}
