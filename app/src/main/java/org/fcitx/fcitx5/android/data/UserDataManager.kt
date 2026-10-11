/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.fcitx.fcitx5.android.BuildConfig
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.clipboard.CLIPBOARD_DATABASE
import org.fcitx.fcitx5.android.data.clipboard.db.ClipboardEntry
import org.fcitx.fcitx5.android.data.clipboard.importedClipboardMarker
import org.fcitx.fcitx5.android.utils.Const
import org.fcitx.fcitx5.android.utils.appContext
import org.fcitx.fcitx5.android.utils.errorRuntime
import org.fcitx.fcitx5.android.utils.extract
import org.fcitx.fcitx5.android.utils.versionCodeCompat
import org.fcitx.fcitx5.android.utils.withTempDir
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object UserDataManager {

    private val json = Json { prettyPrint = true }

    @Serializable
    data class Metadata(
        val packageName: String,
        val versionCode: Long,
        val versionName: String,
        val exportTime: Long
    )

    // fcitx and the clipboard go on writing while this reads: a file renamed or deleted since it was listed is left out
    private fun File.openIfThere() = try {
        inputStream()
    } catch (e: FileNotFoundException) {
        if (exists()) throw e
        null
    }

    private fun writeFileTree(srcDir: File, destPrefix: String, dest: ZipOutputStream, skip: (File) -> Boolean = { false }) {
        dest.putNextEntry(ZipEntry("$destPrefix/"))
        srcDir.walkTopDown().forEach { f ->
            val related = f.relativeTo(srcDir)
            if (related.path != "") {
                if (f.isDirectory) {
                    dest.putNextEntry(ZipEntry("$destPrefix/${related.path}/"))
                } else if (f.isFile && !skip(f)) {
                    f.openIfThere()?.use {
                        dest.putNextEntry(ZipEntry("$destPrefix/${related.path}"))
                        it.copyTo(dest)
                    }
                }
            }
        }
    }

    /** Where the user's data is: the app's ([appDirs]), or a test's. */
    internal class Dirs(
        val sharedPrefs: File,
        val dataBases: File,
        val external: File,
        val recentlyUsed: File,
        // the engine's user store (EngineBridge): what it learnt of the user's words, user tables
        val engine: File
    )

    // internal for ClipboardManagerReopenTest, which leaves the import's clipboard marker where an import into these does;
    // made at each use, as this object outlives a test's app and the next test's app has directories of its own
    internal val appDirs
        get() = Dirs(
            File(appContext.applicationInfo.dataDir, "shared_prefs"),
            File(appContext.applicationInfo.dataDir, "databases"),
            appContext.getExternalFilesDir(null)!!,
            appContext.filesDir.resolve(RecentlyUsed.DIR_NAME),
            appContext.filesDir.resolve("engine")
        )

    // SQLite's files beside a database
    private val journals = listOf("-journal", "-wal", "-shm")

    /**
     * A copy in [dir] of the clipboard's database in [databases], without the entries an app marked
     * sensitive, a password from a password manager say: the backup is a plain zip. Null if there is
     * none, or if the copy cannot be cleaned.
     */
    private fun clipboardWithoutSensitive(databases: File, dir: File): File? {
        // with its journals: what is not yet written into it is there
        for (suffix in listOf("") + journals) {
            val file = File(databases, CLIPBOARD_DATABASE + suffix)
            file.openIfThere()?.use { input -> File(dir, file.name).outputStream().use { input.copyTo(it) } }
        }
        val copy = File(dir, CLIPBOARD_DATABASE).takeIf { it.isFile } ?: return null
        try {
            SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                // out of the write-ahead log, which would keep the entries in a file beside the copy
                db.disableWriteAheadLogging()
                db.execSQL("DELETE FROM ${ClipboardEntry.TABLE_NAME} WHERE sensitive=1")
                // else their text stays in the pages they leave free
                db.execSQL("VACUUM")
            }
        } catch (e: SQLiteException) {
            // copied while the clipboard wrote to it, torn: the backup goes without it, not without the settings and words too
            Timber.w(e, "Clipboard history left out of the backup: its copy cannot be cleaned")
            return null
        }
        return copy
    }

    fun export(dest: OutputStream, timestamp: Long = System.currentTimeMillis()) = export(dest, timestamp, appDirs)

    @OptIn(ExperimentalSerializationApi::class)
    internal fun export(dest: OutputStream, timestamp: Long, dirs: Dirs) = runCatching {
        ZipOutputStream(dest.buffered()).use { zipStream ->
            // shared_prefs
            writeFileTree(dirs.sharedPrefs, "shared_prefs", zipStream)
            // databases, the clipboard's as clipboardWithoutSensitive copies it
            writeFileTree(dirs.dataBases, "databases", zipStream) { it.name.startsWith(CLIPBOARD_DATABASE) }
            withTempDir { dir ->
                clipboardWithoutSensitive(dirs.dataBases, dir)?.let { db ->
                    zipStream.putNextEntry(ZipEntry("databases/${db.name}"))
                    db.inputStream().use { it.copyTo(zipStream) }
                }
            }
            // external
            writeFileTree(dirs.external, "external", zipStream)
            // engine, but for a log being compacted (RecordStore): it holds nothing the log has not
            if (dirs.engine.isDirectory) {
                writeFileTree(dirs.engine, "engine", zipStream) { it.name.endsWith(".compacting") }
            }
            // recently_used moved to SharedPreference and shoud not be exported
            // metadata
            zipStream.putNextEntry(ZipEntry("metadata.json"))
            val pkgInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            val metadata = Metadata(
                pkgInfo.packageName,
                pkgInfo.versionCodeCompat,
                Const.versionName,
                timestamp
            )
            json.encodeToStream(metadata, zipStream)
            zipStream.closeEntry()
        }
    }

    private fun copyDir(source: File, target: File) {
        val exists = source.exists()
        val isDir = source.isDirectory
        if (exists && isDir) {
            source.copyRecursively(target, overwrite = true)
        } else {
            Timber.w("Cannot import user data: path='${source.path}', exists=$exists, isDir=$isDir")
        }
    }

    fun import(src: InputStream) = import(src, appDirs)

    internal fun import(src: InputStream, dirs: Dirs) = runCatching {
        withTempDir { tempDir ->
            // closed before anything is copied: nothing may fail the import once the engine's is in
            val extracted = ZipInputStream(src).use { it.extract(tempDir) }
            val metadataFile = extracted.find { it.name == "metadata.json" }
                ?: errorRuntime(R.string.exception_user_data_metadata)
            val metadata = json.decodeFromString<Metadata>(metadataFile.readText())
            val origin = UserDataOrigin.of(
                metadata.packageName, BuildConfig.APPLICATION_ID, BuildConfig.APPLICATION_ID_ROOT
            ) ?: errorRuntime(R.string.exception_user_data_package_name_mismatch)
            if (origin is UserDataOrigin.Legacy) {
                val prefs = File(tempDir, "shared_prefs")
                val legacy = File(prefs, origin.preferences)
                // else copied in under a name this app does not read: every setting at its default
                if (legacy.exists() && !legacy.renameTo(File(prefs, origin.renamed))) {
                    throw IOException("cannot rename ${legacy.name}")
                }
            }
            // the settings it brings that have no UI go back to their defaults at the next start:
            // not here, where AppPrefs would write its own copy over the imported one
            importedUserDataMarker(appContext).createNewFile()
            copyDir(File(tempDir, "shared_prefs"), dirs.sharedPrefs)
            val databases = File(tempDir, "databases")
            // only an import's own copy marks the clipboard's file the backup's: not a marker the backup brings, which
            // would mark the user's own file if it has no database. One an earlier import left stays, a start having
            // kept it for what may pass: the file is a backup's whether this import brings none, fails before its copy
            // or copies its own
            importedClipboardMarker(databases).deleteRecursively()
            copyDir(databases, dirs.dataBases)
            // a journal left of a database it replaced would be played over the one it brings: deleted only once that
            // is in, as a failed import must leave it its journal; the backup's own kept
            databases.list()?.forEach { name ->
                journals.map { name + it }.filterNot { File(databases, it).exists() }.forEach { File(dirs.dataBases, it).delete() }
            }
            // the clipboard's file the backup's now: its next open, at the start after this or at the reopen after a
            // failure, may start over what will not pass, where the user's own is kept. A marker that cannot be written
            // leaves the file to be kept as the user's
            if (File(databases, CLIPBOARD_DATABASE).exists()) {
                runCatching { importedClipboardMarker(dirs.dataBases).createNewFile() }
                    .onFailure { Timber.w(it, "Clipboard database import not marked: its next open keeps it as the user's") }
            }
            copyDir(File(tempDir, "external"), dirs.external)
            // keep importing recently_used for backwords compatibility
            copyDir(File(tempDir, "recently_used"), dirs.recentlyUsed)
            // last: an import failing after it would start fcitx again on the logs it replaced
            if (File(tempDir, "engine").exists()) importEngine(File(tempDir, "engine"), dirs.engine)
            metadata
        }
    }

    /**
     * [source] laid over [engine], as [copyDir] would, but put in place in one rename: if the
     * import fails here, the engine's directory is whole as it was, for fcitx to start again on.
     */
    private fun importEngine(source: File, engine: File) {
        if (!source.isDirectory) {
            Timber.w("Cannot import user data: path='${source.path}' is not a directory")
            return
        }
        replaceDirectory(engine, File(engine.path + ".import")) { staged ->
            // the backup moved, and only the engine's files it lacks copied: a full phone may have no room for two copies
            if (!source.renameTo(staged)) source.copyRecursively(staged)
            engine.walkTopDown()
                // a directory the backup has as a file: the backup's
                .onEnter { !File(staged, it.toRelativeString(engine)).isFile }
                .forEach {
                    val target = File(staged, it.toRelativeString(engine))
                    if (it.isDirectory) target.mkdirs() else if (!target.exists()) it.copyTo(target)
                }
        }
    }
}

/**
 * Left by [UserDataManager.import] for the next start, which puts the settings without a UI back
 * to their defaults ([org.fcitx.fcitx5.android.data.prefs.AppPrefs.resetHiddenSettings]); outside
 * the object, which the app's start must not make (it needs the external storage).
 */
fun importedUserDataMarker(context: Context) = File(context.noBackupFilesDir, "user_data_imported")