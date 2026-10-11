/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.store

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Replaces [dest] with what [write] puts in [temp], whole or not at all, and leaves no [temp]
 * behind; a return out of [write] keeps [dest] as it was.
 *
 * @throws IOException if [dest] could not be replaced
 */
inline fun replaceFile(dest: File, temp: File, write: (File) -> Unit) {
    try {
        write(temp)
        // the sync below would make a missing one, empty, and rename it over dest
        if (!temp.isFile) throw IOException("cannot replace ${dest.name}: nothing written")
        // on disk before the rename: else a power cut may leave the new name on an empty file
        FileOutputStream(temp, true).use { it.fd.sync() }
        if (!temp.renameTo(dest)) throw IOException("cannot replace ${dest.name}")
    } finally {
        temp.delete()
    }
}
