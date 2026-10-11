/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.dicttool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JsonStringsTest {

    @Test
    fun aUnicodeEscapeIsFourAsciiHexDigits() {
        assertEquals(listOf("A你\u00ff"), JsonStrings.parse("""["\u0041\u4F60\u00ff"]"""))
        // a sign or Arabic-Indic digits, which Integer.parseInt takes
        for (bad in listOf("-001", "+041", "\u0660\u0660\u0664\u0661")) {
            assertThrows(bad, IllegalArgumentException::class.java) { JsonStrings.parse("[\"\\u$bad\"]") }
        }
    }
}
