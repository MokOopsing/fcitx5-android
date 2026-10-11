/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.quickphrase

import kotlinx.parcelize.Parcelize
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.utils.errorArg
import timber.log.Timber
import java.io.File

@Parcelize
class CustomQuickPhrase(
    private var _file: File,
    private var _enabled: Boolean = false
) : QuickPhrase() {

    override var isEnabled: Boolean
        get() = _enabled
        private set(value) {
            _enabled = value
        }

    override var file: File
        get() = _file
        private set(value) {
            _file = value
        }

    override val name: String
        get() = if (isEnabled) super.name
        else file.name.substringBefore(".$EXT.$DISABLE")

    override fun loadData() = QuickPhraseData.fromLines(file.readLines())

    init {
        ensureFileExists()
        isEnabled = when {
            file.extension == EXT -> {
                true
            }
            file.name.endsWith(".$EXT.$DISABLE") -> {
                false
            }
            else -> errorArg(R.string.exception_quickphrase_filename, file.name)
        }
    }

    override fun enable() {
        if (isEnabled)
            return
        val newFile = file.resolveSibling("$name.$EXT")
        if (!renamedTo(newFile))
            return
        file = newFile
        isEnabled = true
    }

    override fun disable() {
        if (!isEnabled)
            return
        val newFile = file.resolveSibling("$name.$EXT.$DISABLE")
        if (!renamedTo(newFile))
            return
        file = newFile
        isEnabled = false
    }

    // the list sets its switch back from isEnabled: only a log says why it did not move
    private fun renamedTo(newFile: File): Boolean =
        file.renameTo(newFile).also { if (!it) Timber.w("Cannot rename ${file.name} to ${newFile.name}") }

    override fun saveData(data: QuickPhraseData) =
        file.writeText(data.serialize())

    override fun toString(): String {
        return "CustomQuickPhrase(isEnabled=$isEnabled, file=$file, name='$name')"
    }

}
