/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.table

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import org.fcitx.fcitx5.android.FcitxApplication
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Where the tables imported and replaced are kept and under what names, and that a failed import
 * or replacement leaves the files as they were. Under the real application: the manager and its
 * error messages go through appContext.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = FcitxApplication::class)
class TableManagerTest {

    // the manager's own, made once for the class: each test's application has another external dir
    private val confs get() = TableManager.inputMethodDir
    private val tables get() = TableManager.tableDicDir

    private val table = "键码=ab\n码长=4\n[数据]\na 工\n"
    private val unreadable = "[数据]\nabc\n"

    private fun names(dir: File) = dir.list()!!.sorted()

    @Before
    fun empty() {
        listOf(confs, tables).forEach { dir -> dir.listFiles()?.forEach { it.deleteRecursively() } }
    }

    // fcitx's ini parser reads and writes a conf over JNI, which a JVM test has not
    private fun inputMethod(confFile: File, tableFile: String = ""): TableBasedInputMethod {
        var kept = tableFile
        return mockk(relaxed = true) {
            every { file } returns confFile
            every { tableFileName } answers { kept }
            every { tableFileName = any() } answers { kept = firstArg() }
        }
    }

    private fun importTable(confName: String, dictName: String, dict: String = table) =
        TableManager.importFromConfAndDict(confName, "".byteInputStream(), dictName, dict.byteInputStream())

    private fun replaceTable(im: TableBasedInputMethod, dictName: String) =
        TableManager.replaceTableDict(im, dictName, table.byteInputStream())

    @Test
    fun aTableIsNamedAfterItsInputMethodWhateverItsDictionaryFileIsCalled() {
        mockkObject(TableBasedInputMethod.Companion) {
            every { TableBasedInputMethod.new(any()) } answers { inputMethod(firstArg()) }
            assertEquals("my-table.txt", importTable("My Table.conf", "DB.MAIN.TXT").getOrThrow().tableFileName)
            // the same dictionary file, for another input method
            assertEquals("other.txt", importTable("Other.conf", "db.main.txt").getOrThrow().tableFileName)
        }
        assertEquals(listOf("My Table.conf", "Other.conf"), names(confs))
        assertEquals(listOf("my-table.txt", "other.txt"), names(tables))
        assertEquals(table, File(tables, "other.txt").readText())
    }

    /** A file manager's WUBI.CONF, taken as wubi.conf is: the input method WUBI, so a wubi.conf after it is one already there. */
    @Test
    fun aConfNamedInCapitalsIsKeptWithoutItsSuffix() {
        mockkObject(TableBasedInputMethod.Companion) {
            every { TableBasedInputMethod.new(any()) } answers { inputMethod(firstArg()) }
            assertEquals("wubi.txt", importTable("WUBI.CONF", "WUBI.TXT").getOrThrow().tableFileName)
            assertEquals("mydb.txt", importTable("MyDB.Conf.IN", "db.txt").getOrThrow().tableFileName)
            assertTrue(importTable("wubi.conf", "wubi.txt").isFailure)
        }
        assertEquals(listOf("MyDB.conf", "WUBI.conf"), names(confs))
        assertEquals(listOf("mydb.txt", "wubi.txt"), names(tables))
    }

    /** As for a pinyin dictionary: a provider's names, which can be any, write nothing out of the import's temp dir. */
    @Test
    fun aProvidersNamesAreNoPathsOutOfTheCache() {
        // the temp dir is one in the cache: two up is what holds the cache, the app's own data
        val home = RuntimeEnvironment.getApplication().cacheDir.parentFile!!
        val outside = listOf("outside.conf", "outside.txt").map { File(home, it).apply { writeText("theirs") } }
        try {
            mockkObject(TableBasedInputMethod.Companion) {
                every { TableBasedInputMethod.new(any()) } answers { inputMethod(firstArg()) }
                val im = importTable("../../outside.conf", "../../outside.txt").getOrThrow()
                replaceTable(im, "../../outside.txt").getOrThrow()
            }
            outside.forEach { assertEquals(it.name, "theirs", it.readText()) }
            assertEquals(listOf("outside.conf"), names(confs))
            assertEquals(listOf("outside.txt"), names(tables))
            assertEquals(table, File(tables, "outside.txt").readText())
        } finally {
            outside.forEach { it.delete() }
        }
    }

    @Test
    fun anImportThatFailsLeavesNoConfBehind() {
        mockkObject(TableBasedInputMethod.Companion) {
            every { TableBasedInputMethod.new(any()) } throws RuntimeException("missing [InputMethod] section or 'Name' key")
            assertTrue(importTable("bad.conf", "bad.txt").isFailure)
            every { TableBasedInputMethod.new(any()) } answers { inputMethod(firstArg()) }
            assertTrue(importTable("mine.conf", "mine.txt", unreadable).isFailure)
        }
        assertEquals(emptyList<String>(), names(confs))
        assertEquals(emptyList<String>(), names(tables))
    }

    @Test
    fun anImportWhoseTableNameIsTakenLeavesTheOtherInputMethodsTable() {
        File(tables, "mine.txt").writeText("theirs")
        mockkObject(TableBasedInputMethod.Companion) {
            every { TableBasedInputMethod.new(any()) } answers { inputMethod(firstArg()) }
            assertTrue(importTable("Mine.conf", "other.txt").isFailure)
        }
        assertEquals(emptyList<String>(), names(confs))
        assertEquals(listOf("mine.txt"), names(tables))
        assertEquals("theirs", File(tables, "mine.txt").readText())
    }

    @Test
    fun aTextTableIsReplacedInPlace() {
        File(tables, "mine.txt").writeText("old")
        val im = inputMethod(File(confs, "mine.conf"), "mine.txt")
        val installed = replaceTable(im, "NEW.TXT").getOrThrow()
        assertEquals(File(tables, "mine.txt"), installed.file)
        assertEquals(table, installed.file.readText())
        assertEquals("mine.txt", im.tableFileName)
        assertEquals(listOf("mine.txt"), names(tables))
    }

    @Test
    fun libimesTableIsReplacedByTextTheConfThenPointsAt() {
        File(tables, "legacy.dict").writeBytes(byteArrayOf(0, 1, 2))
        val im = inputMethod(File(confs, "legacy.conf"), "legacy.dict")
        val installed = replaceTable(im, "NEW.TXT").getOrThrow()
        assertEquals(File(tables, "legacy.txt"), installed.file)
        assertEquals(table, installed.file.readText())
        assertEquals("legacy.txt", im.tableFileName)
        verify { im.save() }
        assertEquals(listOf("legacy.txt"), names(tables))
    }

    @Test
    fun aReplacementUnderAnotherInputMethodsTableNameIsRefused() {
        File(tables, "legacy.dict").writeBytes(byteArrayOf(0, 1, 2))
        File(tables, "legacy.txt").writeText("theirs")
        val im = inputMethod(File(confs, "legacy.conf"), "legacy.dict")
        assertTrue(replaceTable(im, "new.txt").isFailure)
        assertEquals("legacy.dict", im.tableFileName)
        verify(exactly = 0) { im.save() }
        assertEquals(listOf("legacy.dict", "legacy.txt"), names(tables))
        assertArrayEquals(byteArrayOf(0, 1, 2), File(tables, "legacy.dict").readBytes())
        assertEquals("theirs", File(tables, "legacy.txt").readText())
    }
}
