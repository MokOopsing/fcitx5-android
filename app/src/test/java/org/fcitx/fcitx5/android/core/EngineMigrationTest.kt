/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EngineMigrationTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun File.put(path: String, text: String) = resolve(path).apply { parentFile!!.mkdirs() }.writeText(text)

    @Test
    fun libimesSettingsBecomeTheEngines() {
        val home = folder.root
        home.put("conf/pinyin.conf", "PageSize=5\n")
        // where fcitx's table addon keeps them
        home.put("table/wbx.conf", "[Table]\nAutoSelect=False\n")
        EngineMigration.run(home, folder.root.resolve("data"))
        assertEquals("PageSize=5\n\n[Wubi]\nAutoSelect=False\n", home.resolve("conf/androidengine.conf").readText())
    }

    @Test
    fun aConfigMigratedBeforeTablesHadSettingsGetsThem() {
        val home = folder.root
        home.put("conf/androidengine.conf", "PageSize=5\n")
        home.put("table/zrm.conf", "[Table]\nOrderPolicy=No\n")
        EngineMigration.run(home, folder.root.resolve("data"))
        val once = home.resolve("conf/androidengine.conf").readText()
        assertEquals("PageSize=5\n\n[Ziranma]\nOrderPolicy=No\n", once)
        EngineMigration.run(home, folder.root.resolve("data"))
        assertEquals(once, home.resolve("conf/androidengine.conf").readText())
        assertFalse(home.resolve("conf/androidengine.conf.new").exists())
    }

    @Test
    fun theTablesTheUserImportedBecomeTheEngines() {
        val data = folder.newFolder("data")
        data.put("inputmethod/my.conf", "[InputMethod]\nName=My\nAddon=table\n\n[Table]\nFile=table/my.main.dict\n")
        data.put("inputmethod/other.conf", "[InputMethod]\nName=Other\nAddon=something\n")
        // named as libime's own: the profile names the engine's instead
        data.put("inputmethod/wbx.conf", "[InputMethod]\nName=Mine\nAddon=table\n")
        // not one to read, and not in the way of the rest
        data.resolve("inputmethod/dir.conf").mkdirs()
        EngineMigration.run(folder.newFolder("config"), data)
        assertEquals(
            "[InputMethod]\nName=My\nAddon=androidengine\n\n[Table]\nFile=table/my.main.dict\n",
            data.resolve("inputmethod/my.conf").readText(),
        )
        assertEquals("[InputMethod]\nName=Other\nAddon=something\n", data.resolve("inputmethod/other.conf").readText())
        assertEquals("[InputMethod]\nName=Mine\nAddon=table\n", data.resolve("inputmethod/wbx.conf").readText())
    }

    @Test
    fun whatTheMigrationRewritesIsKeptAsItWasOnce() {
        val config = folder.newFolder("config")
        val data = folder.newFolder("data")
        val table = "[InputMethod]\nName=My\nAddon=table\n\n[Table]\nFile=table/my.main.dict\n"
        data.put("inputmethod/my.conf", table)
        config.put("conf/androidengine.conf", "PageSize=5\n")
        config.put("table/zrm.conf", "[Table]\nOrderPolicy=No\n")
        EngineMigration.run(config, data)
        assertEquals(table, data.resolve(".libime/inputmethod/my.conf").readText())
        assertEquals("PageSize=5\n", config.resolve(".libime/conf/androidengine.conf").readText())
        assertFalse(config.resolve(".libime/conf/androidengine.conf.new").exists())
        // rewritten again: what it was before the first time is what is kept
        config.put("table/wbx.conf", "[Table]\nAutoSelect=False\n")
        EngineMigration.run(config, data)
        assertEquals("PageSize=5\n", config.resolve(".libime/conf/androidengine.conf").readText())
    }

    @Test
    fun aFileThatCannotBeKeptIsNotRewrittenNorIsWhatFollowsIt() {
        val config = folder.newFolder("config")
        val profile = "[Groups/0]\nName=Default\nDefaultIM=pinyin\n\n[Groups/0/Items/0]\nName=pinyin\n"
        config.put("profile", profile)
        config.put("conf/androidengine.conf", "PageSize=5\n")
        config.put("table/zrm.conf", "[Table]\nOrderPolicy=No\n")
        // where the copies go: no directory can be made there, so no copy
        config.put(".libime", "")
        EngineMigration.run(config, folder.newFolder("data"))
        assertArrayEquals(profile.toByteArray(), config.resolve("profile").readBytes())
        assertArrayEquals("PageSize=5\n".toByteArray(), config.resolve("conf/androidengine.conf").readBytes())
        assertFalse(config.resolve("profile.new").exists())
        assertFalse(config.resolve("conf/androidengine.conf.new").exists())
    }

    @Test
    fun aFileTheMigrationMakesHasNothingToKeep() {
        val home = folder.root
        home.put("conf/pinyin.conf", "PageSize=5\n")
        EngineMigration.run(home, folder.root.resolve("data"))
        assertTrue(home.resolve("conf/androidengine.conf").exists())
        assertFalse(home.resolve(".libime/conf/androidengine.conf").exists())
        // only read, never rewritten
        assertFalse(home.resolve(".libime/conf/pinyin.conf").exists())
    }
}
