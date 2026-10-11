/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.table

import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.EngineBridge
import org.fcitx.fcitx5.android.core.RawConfig
import org.fcitx.fcitx5.android.data.table.dict.Dictionary
import org.fcitx.fcitx5.android.engine.host.Engines
import org.fcitx.fcitx5.android.engine.host.LibimeMigration
import org.fcitx.fcitx5.android.utils.Ini
import org.fcitx.fcitx5.android.utils.Locales
import org.fcitx.fcitx5.android.utils.appContext
import org.fcitx.fcitx5.android.utils.errorRuntime
import timber.log.Timber
import java.io.File

class TableBasedInputMethod internal constructor(val file: File, private var ini: Ini) {

    // fcitx parses the conf, over JNI: a JVM test, which has no fcitx, gives the internal one its ini
    constructor(file: File) : this(file, Ini.parseIniFromFile(file) ?: errorRuntime(R.string.invalid_im, file.name))

    var table: Dictionary? = null

    val name: String by lazy {
        ini.get(InputMethod)?.let {
            (it.get(NameI18n.format(Locales.languageWithCountry))
                ?: it.get(NameI18n.format(Locales.language))
                ?: it.get(Name))?.value
        } ?: errorRuntime(
            R.string.invalid_im,
            ERROR_MISSING_INPUT_METHOD_OR_NAME
        )
    }

    var tableFileName: String
        get() = ini.get(Table, File)?.value
            ?.substringAfterLast('/')
            ?: errorRuntime(R.string.invalid_im, ERROR_MISSING_TABLE_OR_FILE)
        set(value) {
            ini.set(Table, File, str = "table/$value")
        }

    /** Made an input method of the app's engine, whichever addon the file was written for. */
    fun useEngine() {
        ini.set(InputMethod, Addon, str = LibimeMigration.ENGINE_ADDON)
    }

    val tableFileExists
        get() = table != null

    fun save() {
        Ini.writeIniToFile(ini, file)
    }

    fun delete() {
        deleteTable(file, table?.file, File(appContext.filesDir, "engine"), EngineBridge.fcitxHome())
        table = null
    }

    companion object {
        const val InputMethod = "InputMethod"
        const val Name = "Name"
        const val Addon = "Addon"
        const val NameI18n = "Name[%s]"
        const val Table = "Table"
        const val File = "File"

        const val ERROR_MISSING_INPUT_METHOD_OR_NAME = "missing [InputMethod] section or 'Name' key"
        const val ERROR_MISSING_TABLE_OR_FILE = "missing [Table] section or 'File' key"

        fun fixedTableFileName(name: String) =
            name.split(' ')
                .joinToString(separator = "-")
                .lowercase() + ".txt"

        /**
         * The file a table replacing the one in [oldName] goes to: the same, or for libime's
         * binary the text one a table imported now would have, named after its input method [im].
         */
        fun replacedTableFileName(oldName: String, im: String) =
            if (oldName.endsWith(".txt")) oldName
            else fixedTableFileName(im)

        fun new(configFile: File): TableBasedInputMethod {
            val im = TableBasedInputMethod(configFile)
            Timber.d("new TableBasedInputMethod(name=${im.name}, tableFileName=${im.tableFileName})")
            return im
        }
    }
}

/** Deletes the table input method of [conf] and its [tableFile], as [TableBasedInputMethod.delete] does, its directories passed in. */
internal fun deleteTable(conf: File, tableFile: File?, engineDir: File, home: File) {
    tableFile?.delete()
    // what the engine built of it and learned: a table imported later by its name starts afresh
    val im = conf.name.removeSuffix(".conf")
    Engines.addedTableFiles(im).forEach { File(engineDir, it).delete() }
    // and what its page set, which the engine reads over its conf (EngineBridge.userTable) in fcitx's home
    File(home, "config/table/$im.conf").delete()
    conf.delete()
}