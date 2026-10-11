/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.table

import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.table.dict.Dictionary
import org.fcitx.fcitx5.android.engine.host.LibimeMigration
import org.fcitx.fcitx5.android.utils.appContext
import org.fcitx.fcitx5.android.utils.errorRuntime
import org.fcitx.fcitx5.android.utils.extract
import org.fcitx.fcitx5.android.utils.withLowercaseExtension
import org.fcitx.fcitx5.android.utils.withTempDir
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

object TableManager {

    internal val inputMethodDir = File(
        appContext.getExternalFilesDir(null)!!, "data/inputmethod"
    ).also { it.mkdirs() }

    internal val tableDicDir = File(
        appContext.getExternalFilesDir(null)!!, "data/table"
    ).also { it.mkdirs() }

    fun inputMethods(): List<TableBasedInputMethod> =
        inputMethodDir.listFiles()?.mapNotNull { confFile ->
            runCatching {
                TableBasedInputMethod.new(confFile).apply {
                    runCatching {
                        table = Dictionary.new(File(tableDicDir, tableFileName))
                    }
                }
            }.getOrElse { e ->
                Timber.w("Failed to read input method file ${confFile.absolutePath}: ${e.message}")
                null
            }
        } ?: emptyList()

    fun importFromZip(src: InputStream): Result<TableBasedInputMethod> =
        runCatching {
            ZipInputStream(src).use { zipStream ->
                withTempDir { tempDir ->
                    val extracted = zipStream.extract(tempDir)
                    val confFile = extracted.find { it.name.endsWith(".conf") }
                        ?: extracted.find { it.name.endsWith(".conf.in") }
                        ?: errorRuntime(R.string.exception_table_im)
                    val dictFile = extracted.find { it.name.endsWith(".dict") }
                        ?: extracted.find { it.name.endsWith(".txt") }
                        ?: errorRuntime(R.string.exception_table)
                    importFiles(confFile, dictFile)
                }
            }
        }

    fun importFromConfAndDict(
        confName: String,
        confStream: InputStream,
        dictName: String,
        dictStream: InputStream
    ): Result<TableBasedInputMethod> = runCatching {
        withTempDir { tempDir ->
            // the names a provider gives are no paths to trust: their last parts alone, which name the files
            val confFile = File(tempDir, File(confName).name).also {
                it.outputStream().use { o -> confStream.use { i -> i.copyTo(o) } }
            }
            // each reader checks its extension in lowercase: a file manager's WUBI.TXT is a table too
            val dictFile = File(tempDir, withLowercaseExtension(File(dictName).name)).also {
                it.outputStream().use { o -> dictStream.use { i -> i.copyTo(o) } }
            }
            importFiles(confFile, dictFile)
        }
    }

    private fun importFiles(confFile: File, dictFile: File): TableBasedInputMethod {
        // in any case, as isTableConf takes the conf: WUBI.CONF is the input method WUBI, so a later wubi.conf is one already there
        val name = LibimeMigration.importedTableName(confFile.name.withoutSuffix(".in").withoutSuffix(".conf"))
        val importedConfFile = File(inputMethodDir, "$name.conf").also {
            if (it.exists())
                errorRuntime(R.string.table_already_exists, it.name)
            confFile.copyTo(it)
        }
        val im = runCatching {
            TableBasedInputMethod.new(importedConfFile)
        }.getOrElse {
            importedConfFile.delete()
            throw it
        }
        val table = Dictionary.new(dictFile)!!
        // named after the input method: two input methods' dictionary files may share a name
        im.tableFileName = TableBasedInputMethod.fixedTableFileName(name)
        val dest = File(tableDicDir, im.tableFileName)
        // another input method's, named alike or, as before, after its dictionary file
        if (dest.exists()) {
            im.file.delete()
            errorRuntime(R.string.table_dict_already_exists, dest.name)
        }
        im.table = runCatching { ImportedTables.install(table, dest) }.getOrElse {
            im.file.delete()
            errorRuntime(R.string.invalid_table_dict, it.message)
        }
        im.useEngine()
        im.save()
        return im
    }

    /**
     * Replaces the table of [im]. One kept as libime's binary before the engine is replaced by
     * text, under the name [TableBasedInputMethod.replacedTableFileName] gives it.
     */
    fun replaceTableDict(
        im: TableBasedInputMethod,
        dictName: String,
        dictStream: InputStream
    ): Result<Dictionary> = runCatching {
        withTempDir { tempDir ->
            // as on import, the provider's name is no path
            val dictFile = File(tempDir, withLowercaseExtension(File(dictName).name)).also {
                it.outputStream().use { o -> dictStream.use { i -> i.copyTo(o) } }
            }
            val dict = Dictionary.new(dictFile)!!
            val old = File(tableDicDir, im.tableFileName)
            val name = TableBasedInputMethod.replacedTableFileName(old.name, im.file.name.removeSuffix(".conf"))
            // another input method's
            if (name != old.name && File(tableDicDir, name).exists())
                errorRuntime(R.string.table_dict_already_exists, name)
            val installed = runCatching { ImportedTables.install(dict, File(tableDicDir, name)) }
                .getOrElse { errorRuntime(R.string.invalid_table_dict, it.message) }
            if (installed.file != old) {
                im.tableFileName = name
                im.save()
                old.delete()
            }
            installed
        }
    }
}

private fun String.withoutSuffix(suffix: String) =
    if (endsWith(suffix, ignoreCase = true)) dropLast(suffix.length) else this
