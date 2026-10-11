/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.core

import org.fcitx.fcitx5.android.engine.host.LibimeMigration
import org.fcitx.fcitx5.android.engine.store.replaceFile
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Carries over the config of those who used libime's pinyin and tables, before fcitx reads it:
 * the profile names the engine's input methods instead, and the settings of pinyin and of the
 * tables become the androidengine addon's. What the user learned is read in later, by the engine (EngineBridge).
 * Each file is kept as it was before it is first rewritten ([keep]).
 */
object EngineMigration {

    /** [configHome] and [dataHome] are fcitx's (FCITX_CONFIG_HOME, FCITX_DATA_HOME). */
    fun run(configHome: File, dataHome: File) {
        try {
            // the tables the user imported: libime's table addon is not in the app any more
            tables(dataHome, File(dataHome, "inputmethod"))
            val profile = File(configHome, "profile")
            if (profile.exists()) LibimeMigration.profile(profile.readText())?.let { replace(configHome, profile, it) }
            val settings = File(configHome, "conf/androidengine.conf")
            val pinyin = File(configHome, "conf/pinyin.conf")
            // libime keeps a table's settings by the table's name, not under conf/ as addons do
            val tables = LibimeMigration.TABLE_CONFIGS.map { it to File(configHome, "table/$it.conf") }
                .filter { it.second.exists() }.associate { (name, file) -> name to file.readText() }
            if (!settings.exists()) {
                if (pinyin.exists() || tables.isNotEmpty()) {
                    replace(configHome, settings, LibimeMigration.settings(if (pinyin.exists()) pinyin.readText() else "", tables))
                }
            } else if (tables.isNotEmpty()) {
                // migrated by a build whose engine had no table settings yet
                LibimeMigration.withTables(settings.readText(), tables)?.let { replace(configHome, settings, it) }
            }
            val chttrans = File(configHome, "conf/chttrans.conf")
            if (chttrans.exists()) LibimeMigration.inputMethodList(chttrans.readText())?.let { replace(configHome, chttrans, it) }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // run before every start: whatever it trips on must not keep fcitx from starting
            // fcitx starts with its defaults then: the old input methods are just not there
            Timber.w(e, "cannot migrate libime's config")
        }
    }

    /**
     * The tables the user imported: libime's table addon is not in the app any more. Not one named
     * as libime's own (a wbx.conf of their own, say): the profile names the engine's for those.
     */
    private fun tables(home: File, dir: File) {
        val confs = dir.listFiles { f -> f.isFile && f.name.endsWith(".conf") }.orEmpty()
        for (conf in confs.filter { it.name.removeSuffix(".conf") !in LibimeMigration.INPUT_METHODS }) {
            try {
                LibimeMigration.tableInputMethod(conf.readText())?.let { replace(home, conf, it) }
            } catch (e: IOException) {
                // the others, and the rest of the migration, all the same
                Timber.w(e, "cannot migrate %s", conf)
            }
        }
    }

    // a profile cut short by a kill would lose every group
    private fun replace(home: File, file: File, text: String) {
        if (file.exists()) keep(home, file)
        replaceFile(file, File(file.path + ".new")) {
            it.writeText(text)
        }
    }

    /**
     * [file], under [home], as it was before the migration first rewrote it: kept by the same path
     * under [KEPT], once, for a bug here to be undone and an older build to read it. One that
     * cannot be kept is not rewritten.
     */
    private fun keep(home: File, file: File) {
        val kept = File(File(home, KEPT), file.toRelativeString(home))
        if (kept.exists()) return
        kept.parentFile?.mkdirs()
        replaceFile(kept, File(kept.path + ".new")) {
            file.copyTo(it, overwrite = true)
        }
    }

    // at the top of each home: in no directory fcitx or the settings list
    private const val KEPT = ".libime"
}
