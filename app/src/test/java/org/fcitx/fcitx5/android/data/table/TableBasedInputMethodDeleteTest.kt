/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.table

import org.fcitx.fcitx5.android.FcitxApplication
import org.fcitx.fcitx5.android.core.EngineBridge
import org.fcitx.fcitx5.android.core.RawConfig
import org.fcitx.fcitx5.android.data.table.dict.TextDictionary
import org.fcitx.fcitx5.android.engine.host.Engines
import org.fcitx.fcitx5.android.utils.Ini
import org.fcitx.fcitx5.android.utils.appContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Where a table the user deletes takes its files from: the app's own directories, the engine's
 * user data and fcitx's home, where the engine reads them. Under the real application, which
 * has those directories; apart from [TableBasedInputMethodTest], which needs none.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = FcitxApplication::class)
class TableBasedInputMethodDeleteTest {

    @Test
    fun aTableDeletedTakesItsFilesFromTheAppsDirectoriesAndIsNoLongerOpen() {
        val home = EngineBridge.fcitxHome()
        val conf = File(home, "data/inputmethod/my.conf")
        val tableFile = File(home, "data/table/my.txt")
        // what its page set, as EngineBridge.userTable reads it
        val settings = File(home, "config/table/my.conf")
        // under the engine's user directory, EngineBridge.userDir
        val engine = Engines.addedTableFiles("my").map { File(appContext.filesDir, "engine/$it") }
        val files = listOf(conf, tableFile, settings) + engine
        files.forEach {
            it.parentFile!!.mkdirs()
            it.writeText("")
        }
        val im = TableBasedInputMethod(conf, Ini(RawConfig()))
        im.table = TextDictionary(tableFile)
        im.delete()
        assertEquals(emptyList<File>(), files.filter { it.exists() })
        assertNull(im.table)
    }
}
