/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.table.dict

import org.fcitx.fcitx5.android.utils.withLowercaseExtension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Which files are offered as tables to import: as a file manager names them, in any case. */
class DictionaryTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun aKeptTablesExtensionIsLowercaseAndAnOutsideNameIsLowercasedFirst() {
        assertEquals(Dictionary.Type.Text, Dictionary.Type.fromFileName("wubi.txt"))
        assertEquals(Dictionary.Type.Text, Dictionary.Type.fromFileName(withLowercaseExtension("WUBI.TXT")))
        assertEquals(Dictionary.Type.LibIME, Dictionary.Type.fromFileName(withLowercaseExtension("X.DICT")))
        assertNull(Dictionary.Type.fromFileName("WUBI.TXT"))
        assertNull(Dictionary.Type.fromFileName("wubi.conf"))
    }

    // null for a name no reader takes, as the type of new says, rather than the reader's exception
    @Test
    fun aKeptTableIsOpenedOnlyUnderTheLowercaseExtensionItsReaderTakes() {
        assertTrue(Dictionary.new(folder.newFile("wubi.txt")) is TextDictionary)
        assertTrue(Dictionary.new(folder.newFile("legacy.dict")) is LibIMEDictionary)
        assertNull(Dictionary.new(folder.newFile("CANGJIE.TXT")))
        assertNull(Dictionary.new(folder.newFile("X.DICT")))
    }
}
