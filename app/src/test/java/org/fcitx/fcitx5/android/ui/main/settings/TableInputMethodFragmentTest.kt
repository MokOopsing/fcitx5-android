/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A table's files are taken by their names in any case, as its dictionary is: WUBI.CONF with WUBI.TXT too. What the import keeps them as is TableManagerTest's. */
class TableInputMethodFragmentTest {

    @Test
    fun aTablesZipIsTakenInAnyCase() {
        listOf("wubi.zip", "WUBI.ZIP").forEach { assertTrue(it, isTableZip(it)) }
        assertFalse(isTableZip("wubi.zip.txt"))
    }

    @Test
    fun aTablesConfOrItsTemplateIsTakenInAnyCase() {
        listOf("wubi.conf", "WUBI.CONF", "wubi.conf.in", "WUBI.CONF.IN").forEach { assertTrue(it, isTableConf(it)) }
        listOf("wubi.txt", "wubi.in", "wubi.conf.txt").forEach { assertFalse(it, isTableConf(it)) }
    }
}
