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

    /**
     * [configHome] is fcitx's (FCITX_CONFIG_HOME), beside its data home; [engineDir] the engine's
     * user data, null while the phone is locked.
     */
    fun run(configHome: File, engineDir: File?) {
        importedTablesGone(configHome, engineDir)
        try {
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
     * What a table imported in an older build left: its input method's conf, which fcitx would
     * still list with nothing to type it, its table, settings and what it learned. The app's own
     * tables are kept apart: no conf of theirs in the data home, their logs in [engineDir] itself.
     */
    private fun importedTablesGone(configHome: File, engineDir: File?) {
        val data = File(configHome.parentFile, "data")
        val confs = File(data, "inputmethod").listFiles { f -> f.name.endsWith(".conf") }.orEmpty()
        for (conf in confs) {
            val ours = try {
                conf.readLines().any { it.trim() == "Addon=androidengine" }
            } catch (e: IOException) {
                Timber.w(e, "imported table %s", conf.name)
                false
            }
            if (ours) conf.delete()
        }
        File(data, "table").deleteRecursively()
        File(configHome, "table").listFiles { f -> f.name.removeSuffix(".conf") !in LibimeMigration.TABLE_CONFIGS }
            ?.forEach { it.delete() }
        engineDir?.let { File(it, "tables").deleteRecursively() }
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
