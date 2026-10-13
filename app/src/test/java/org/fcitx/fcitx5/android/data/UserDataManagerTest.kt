/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data

import androidx.room.Room
import kotlinx.coroutines.test.runTest
import org.fcitx.fcitx5.android.BuildConfig
import org.fcitx.fcitx5.android.FcitxApplication
import org.fcitx.fcitx5.android.data.clipboard.db.ClipboardDatabase
import org.fcitx.fcitx5.android.data.clipboard.db.ClipboardEntry
import org.fcitx.fcitx5.android.data.clipboard.importedClipboardMarker
import org.fcitx.fcitx5.android.utils.appContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A backup takes the user's data to another install, or from the app before the renaming, whole
 * or not at all, and without what an app marked sensitive on the clipboard (or without the
 * clipboard's history, when its copy cannot be cleaned). Its settings that have no UI are put
 * back to their defaults at the start after its import.
 */
@RunWith(RobolectricTestRunner::class)
// the real application: the manager's directories come from appContext
@Config(application = FcitxApplication::class)
class UserDataManagerTest {

    @get:Rule
    val folder = TemporaryFolder()

    // an install's directories, made as they are needed: not the app's, which it writes as the test runs
    private fun install(name: String) = File(folder.root, name).let {
        UserDataManager.Dirs(
            File(it, "shared_prefs"), File(it, "databases"), File(it, "external"), File(it, "recently_used")
        )
    }

    private fun metadata(packageName: String) =
        """{"packageName":"$packageName","versionCode":1,"versionName":"1","exportTime":0}""".encodeToByteArray()

    private fun zip(packageName: String, vararg entries: Pair<String, String>) = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("metadata.json"))
            zip.write(metadata(packageName))
            for ((name, text) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.encodeToByteArray())
            }
        }
    }.toByteArray().inputStream()

    // named as the app's: the package Robolectric runs it as need not be the one the import checks for
    private fun exported(dirs: UserDataManager.Dirs) = ByteArrayOutputStream().also { bytes ->
        val export = ByteArrayOutputStream().also { UserDataManager.export(it, 0L, dirs).getOrThrow() }
        ZipInputStream(export.toByteArray().inputStream()).use { from ->
            ZipOutputStream(bytes).use { zip ->
                generateSequence { from.nextEntry }.forEach {
                    zip.putNextEntry(ZipEntry(it.name))
                    if (it.name == "metadata.json") zip.write(metadata(BuildConfig.APPLICATION_ID)) else from.copyTo(zip)
                }
            }
        }
    }.toByteArray().inputStream()

    private val prefs = UserDataOrigin.preferencesFile(BuildConfig.APPLICATION_ID)

    // this build type's id before the renaming
    private val legacy = LEGACY_APPLICATION_ID_ROOT + BuildConfig.APPLICATION_ID.removePrefix(BuildConfig.APPLICATION_ID_ROOT)

    @Test
    fun anImportLeavesTheNextStartToResetTheSettingsWithoutAUi() {
        val marker = importedUserDataMarker(appContext)
        marker.delete()
        UserDataManager.import(zip(BuildConfig.APPLICATION_ID)).getOrThrow()
        assertTrue(marker.exists())
    }

    @Test
    fun whatIsExportedIsImportedIntoAFreshInstallAsItWas() {
        val old = install("old")
        File(old.sharedPrefs.apply { mkdirs() }, prefs).writeText("<map />")
        File(old.external, "engine").mkdirs()
        File(old.external, "engine/pinyin.log").writeText("legacy")
        File(old.external, "data/rime").mkdirs()
        File(old.external, "data/rime/user.yaml").writeText("rime")
        val fresh = install("fresh")
        UserDataManager.import(exported(old), fresh).getOrThrow()
        assertEquals("<map />", File(fresh.sharedPrefs, prefs).readText())
        assertFalse(File(fresh.external, "engine").exists())
        assertEquals("rime", File(fresh.external, "data/rime/user.yaml").readText())
    }

    @Test
    fun whatAnAppMarkedSensitiveOnTheClipboardIsLeftOutOfTheBackup() = runTest {
        val old = install("old").apply { dataBases.mkdirs() }
        Room.databaseBuilder(appContext, ClipboardDatabase::class.java, File(old.dataBases, "clbdb").path).build().run {
            clipboardDao().insert(ClipboardEntry(text = "kept"))
            clipboardDao().insert(ClipboardEntry(text = "hunter2", sensitive = true))
            close()
        }
        val fresh = install("fresh")
        // of the database the import replaces: the backup brings none
        File(fresh.dataBases.apply { mkdirs() }, "clbdb-wal").writeText("stale")
        UserDataManager.import(exported(old), fresh).getOrThrow()
        assertFalse(File(fresh.dataBases, "clbdb-wal").exists())
        // out of the file, not only out of the table
        assertTrue(fresh.dataBases.walkTopDown().filter { it.isFile }.none { "hunter2" in it.readText(Charsets.ISO_8859_1) })
        val db = Room.databaseBuilder(appContext, ClipboardDatabase::class.java, File(fresh.dataBases, "clbdb").path).build()
        val texts = mutableListOf<String>()
        db.openHelper.readableDatabase.query("SELECT text FROM ${ClipboardEntry.TABLE_NAME}").use {
            while (it.moveToNext()) texts += it.getString(0)
        }
        db.close()
        assertEquals(listOf("kept"), texts)
    }

    @Test
    fun aClipboardCopyThatCannotBeCleanedIsLeftOutOfTheBackupAlone() {
        val old = install("old").apply { dataBases.mkdirs() }
        File(old.sharedPrefs.apply { mkdirs() }, prefs).writeText("<map />")
        // as a copy torn by the clipboard writing to it reads: SQLite cannot clean it
        File(old.dataBases, "clbdb").writeText("not a database")
        val backup = ByteArrayOutputStream().also { UserDataManager.export(it, 0L, old).getOrThrow() }
        val names = ZipInputStream(backup.toByteArray().inputStream()).use { from -> generateSequence { from.nextEntry }.map { it.name }.toList() }
        assertTrue(names.toString(), "shared_prefs/$prefs" in names)
        assertTrue(names.toString(), names.none { it.startsWith("databases/clbdb") })
    }

    @Test
    fun anImportDropsTheJournalsOfWhatItReplacesButKeepsTheOnesTheBackupBrings() {
        val phone = install("phone").apply { dataBases.mkdirs() }
        File(phone.dataBases, "clbdb-wal").writeText("stale")
        File(phone.dataBases, "clbdb-shm").writeText("stale")
        // as a backup from before the clipboard's database was cleaned has it: the files as they were
        val backup = zip(BuildConfig.APPLICATION_ID, "databases/clbdb" to "db", "databases/clbdb-wal" to "its log")
        UserDataManager.import(backup, phone).getOrThrow()
        assertEquals("its log", File(phone.dataBases, "clbdb-wal").readText())
        assertFalse(File(phone.dataBases, "clbdb-shm").exists())
    }

    @Test
    fun anImportThatFailsCopyingInTheDatabasesLeavesTheirJournals() {
        val phone = install("phone").apply { dataBases.mkdirs() }
        File(phone.dataBases, "clbdb-wal").writeText("open")
        // where the backup's clbdb goes, a directory the copy cannot delete to put it there
        val stuck = File(phone.dataBases, "clbdb/stuck").apply { mkdirs() }
        File(stuck, "x").writeText("")
        stuck.setWritable(false)
        try {
            // as root, or where a directory's mode does not keep what is in it, nothing fails
            assumeFalse(File(stuck, "x").delete())
            assertTrue(UserDataManager.import(zip(BuildConfig.APPLICATION_ID, "databases/clbdb" to "db"), phone).isFailure)
            assertEquals("open", File(phone.dataBases, "clbdb-wal").readText())
            // what is at clbdb may be the user's still: the clipboard is not told it is the backup's
            assertFalse(importedClipboardMarker(phone.dataBases).exists())
        } finally {
            // else the folder cannot be cleaned up
            stuck.setWritable(true)
        }
    }

    // the clipboard's file the backup's: what Room rejects of it the next start, or the reopen after a failure from
    // here, may start over, where the user's own is kept
    @Test
    fun anImportTellsTheClipboardOnlyOfADatabaseItPutIn() {
        val phone = install("phone")
        UserDataManager.import(zip(BuildConfig.APPLICATION_ID, "databases/clbdb" to "db"), phone).getOrThrow()
        assertTrue(importedClipboardMarker(phone.dataBases).isFile)
        // none in the backup: the file the user's still
        val other = install("other")
        UserDataManager.import(zip(BuildConfig.APPLICATION_ID, "databases/other" to "db"), other).getOrThrow()
        assertFalse(importedClipboardMarker(other.dataBases).exists())
    }

    // a marker a backup brings, hand-built say: only an import's own copy marks the file the backup's, where the next
    // open would else start over a user's file that a start had kept. One an earlier import left marks that backup's
    // file, which a start kept for what may pass: an import that brings no clipboard database leaves both
    @Test
    fun onlyAnImportsOwnCopyMarksTheClipboardsFile() {
        val phone = install("phone")
        UserDataManager.import(zip(BuildConfig.APPLICATION_ID, "databases/clbdb.imported" to ""), phone).getOrThrow()
        assertFalse(importedClipboardMarker(phone.dataBases).exists())
        val earlier = install("earlier")
        val clbdb = File(earlier.dataBases.apply { mkdirs() }, "clbdb").apply { writeText("the earlier backup's") }
        val kept = importedClipboardMarker(earlier.dataBases).apply { createNewFile() }
        UserDataManager.import(zip(BuildConfig.APPLICATION_ID, "databases/other" to "db"), earlier).getOrThrow()
        assertEquals("the earlier backup's", clbdb.readText())
        assertTrue(kept.isFile)
    }

    // a wrong zip, say: nothing copied, the user's own file left theirs, for the reopen to keep as a start does
    @Test
    fun anImportThatFailsBeforeTheCopyTellsTheClipboardNothing() {
        val phone = install("phone")
        val backup = zip("another.app", "databases/clbdb" to "db")
        assertTrue(UserDataManager.import(backup, phone).isFailure)
        assertFalse(importedClipboardMarker(phone.dataBases).exists())
    }

    @Test
    fun aBackupFromBeforeTheRenamingBringsItsSettingsUnderTheNewName() {
        val fresh = install("fresh")
        UserDataManager.import(zip(legacy, "shared_prefs/${UserDataOrigin.preferencesFile(legacy)}" to "<map />"), fresh)
            .getOrThrow()
        assertEquals(listOf(prefs), fresh.sharedPrefs.list()!!.toList())
        assertEquals("<map />", File(fresh.sharedPrefs, prefs).readText())
    }

    @Test
    fun aBackupWhoseSettingsCannotTakeTheNewNameIsNotImported() {
        val marker = importedUserDataMarker(appContext).apply { delete() }
        val backup = zip(
            legacy,
            "shared_prefs/${UserDataOrigin.preferencesFile(legacy)}" to "<map />",
            // a directory where they would go: the rename fails
            "shared_prefs/$prefs/x" to "",
        )
        assertTrue(UserDataManager.import(backup, install("fresh")).isFailure)
        assertFalse(marker.exists())
        assertFalse(File(folder.root, "fresh").exists())
    }

    @Test
    fun anImportIgnoresLegacyEngineData() {
        val phone = install("phone")
        val backup = zip(
            BuildConfig.APPLICATION_ID,
            "external/engine/pinyin.log" to "learned",
            "external/engine/lists" to "a file",
            "external/data/rime/user.yaml" to "rime",
        )
        UserDataManager.import(backup, phone).getOrThrow()
        assertFalse(File(phone.external, "engine").exists())
        assertEquals("rime", File(phone.external, "data/rime/user.yaml").readText())
    }
}
