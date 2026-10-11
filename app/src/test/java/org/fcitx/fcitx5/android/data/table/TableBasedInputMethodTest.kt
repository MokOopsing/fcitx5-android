/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.table

import org.fcitx.fcitx5.android.engine.host.Engines
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** What goes with a table the user deletes, lest a table imported later by its name find it. */
class TableBasedInputMethodTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun aTableDeletedTakesWhatItsPageSetAndWhatTheEngineMadeOfIt() {
        val home = folder.newFolder("home")
        val engineDir = folder.newFolder("engine")
        val conf = File(home, "data/inputmethod/my.conf")
        val tableFile = File(home, "data/table/my.txt")
        val settings = File(home, "config/table/my.conf")
        val another = File(home, "config/table/other.conf")
        val engine = Engines.addedTableFiles("my").map { File(engineDir, it) }
        val files = listOf(conf, tableFile, settings, another) + engine
        files.forEach {
            it.parentFile!!.mkdirs()
            it.writeText("")
        }
        deleteTable(conf, tableFile, engineDir, home)
        assertEquals(listOf(another), files.filter { it.exists() })
    }
}
