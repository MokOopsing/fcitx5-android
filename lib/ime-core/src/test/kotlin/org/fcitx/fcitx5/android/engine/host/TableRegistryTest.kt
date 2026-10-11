/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.host

import org.fcitx.fcitx5.android.engine.store.RecordStore
import org.fcitx.fcitx5.android.engine.table.TableUser.TableLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.Executor

/** The user's words as [TableRegistry] codes them for a table, below what a session makes of them. */
class TableRegistryTest : EnginesFixture() {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun whatAnAddedTableForgetsAsItLoadsIsKeptIfItsLogReportsTroubleAsItForgets() {
        val dir = folder.newFolder("engine")
        val tables = dir.resolve(Engines.USER_TABLES).apply { mkdirs() }
        tables.resolve("my.user").outputStream().use { out ->
            out.write(TableLog.FORMAT.header() + TableLog.saved("abcd", "你好"))
            // phrases enough that a compaction is due as the log opens
            repeat((RecordStore.DEFAULT_COMPACT_AT / FILLER).toInt() + 1) { out.write(TableLog.saved("abcd", "$it" + "x".repeat(FILLER))) }
        }
        // which cannot be written: the forget appended next hears of it
        tables.resolve("my.user.compacting").mkdir()
        val forgotten = tables.resolve("my.user${Engines.FORGOTTEN}").apply { writeText("你好\n") }
        val table = Engines.UserTable("[Table]\nFile=table/my.main.dict\n", { "1" }, { "键码=abcd\n码长=4\n[数据]\nab 你\ncd 好\n".reader().buffered() })
        val now = Executor { it.run() }
        val errors = ArrayList<IOException>()
        val assets = EngineAssets(::load, {}, null)
        val lexicon = UserLexicon(null, {}, { null }, { null }, assets, now, forgetInTables = {}, dropSessions = {})
        val registry = TableRegistry(::load, dir, { errors += it }, { table }, now, assets, lexicon)
        val user = registry.addedTable("my").user
        registry.stores().forEach { it.close() }
        assertFalse("你好" in user.savedTexts())
        assertEquals(1, errors.size)
        // what it forgot may not have reached the log: forgotten again at the next load
        assertEquals("你好\n", forgotten.readText())
    }

    @Test
    fun aWordAddedInAnotherReadingOfOneListedIsNotCodedTwice() {
        val additions = Engines.Additions("", "d") { sequenceOf("你好 ni'hao 0") }
        val text = "键码=abcd\n码长=4\n[组词规则]\ne2=p11+p12+p21+p22\n[数据]\nab 你\ncd 好\n"
        val table = Engines.UserTable("[Table]\nFile=table/my.main.dict\n", { "1" }, { text.reader().buffered() })
        val now = Executor { it.run() }
        val assets = EngineAssets(::load, {}, null)
        val lexicon = UserLexicon(null, {}, { null }, { additions }, assets, now, forgetInTables = {}, dropSessions = {})
        val registry = TableRegistry(::load, null, {}, { table }, now, assets, lexicon)
        val shared = registry.shared(registry.addedTable("my"), "my")
        assertEquals(listOf("abcd" to "你好"), shared.words("abcd"))
        assertTrue(lexicon.addWord("好你", "hao ni"))
        assertTrue(lexicon.addWord("你好", "li hao"))
        assertEquals(listOf("cdab" to "好你"), shared.words("cdab"))
        // once is the registry's own doing, nothing the user sees: a session shows a text once however often
        // it is offered (EnginesTest's ...OfferedOnce). Twice would be the word coded again and ranked twice on each key
        assertEquals(listOf("abcd" to "你好"), shared.words("abcd"))
    }

    @Test
    fun aTableThatLearnsHasTheModelsWordsIndexedInTheBackgroundAsItOpens() {
        // what learnSaved looks a saved phrase up in: not made on the input thread at the first save. Told
        // apart from what a table that learns nothing hands the background too, however much that is
        val (learning, notLearning) = listOf("", "Learning=False\n").map { conf ->
            val later = ArrayList<Runnable>()
            val background = Executor { later += it }
            val table = Engines.UserTable("[Table]\nFile=table/my.main.dict\n$conf", { "1" }, { "键码=abcd\n码长=4\n[数据]\nab 你\ncd 好\n".reader().buffered() })
            val assets = EngineAssets(::load, {}, null)
            val lexicon = UserLexicon(null, {}, { null }, { null }, assets, background, forgetInTables = {}, dropSessions = {})
            val registry = TableRegistry(::load, null, {}, { table }, background, assets, lexicon)
            registry.shared(registry.addedTable("my"), "my")
            val handed = later.size
            later.forEach { it.run() }
            handed
        }
        assertEquals(notLearning + 1, learning)
    }

    private companion object {
        // the characters of a phrase that fills most of a record
        const val FILLER = 60_000
    }
}
