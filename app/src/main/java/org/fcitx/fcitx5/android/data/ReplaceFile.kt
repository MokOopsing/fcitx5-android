/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data

import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Replaces directory [dest] with what [fill] puts in [staged], in one rename, and leaves no
 * [staged] behind: if it fails, [dest] and the files of it held open are as they were. A [dest]
 * left aside by one killed between its renames is put back first ([restoreDirectory]).
 *
 * @throws IOException if [dest] could not be replaced
 */
fun replaceDirectory(dest: File, staged: File, fill: (File) -> Unit) {
    val aside = asideOf(dest)
    restoreDirectory(dest)
    staged.deleteRecursively()
    aside.deleteRecursively()
    try {
        fill(staged)
        // on disk before the renames, as in replaceFile: the old copy is deleted right after
        staged.walkTopDown().filter { it.isFile }.forEach { file -> FileOutputStream(file, true).use { it.fd.sync() } }
        val moved = dest.exists()
        if (moved) dest.renameOrThrow(aside, "cannot move ${dest.name} aside")
        if (!staged.renameTo(dest)) {
            // back as it was; else restoreDirectory puts it back before the next use
            if (moved) aside.renameOrThrow(dest, "cannot replace ${dest.name}, left as ${aside.name}")
            throw IOException("cannot replace ${dest.name}")
        }
    } finally {
        staged.deleteRecursively()
    }
    aside.deleteRecursively()
}

/**
 * Puts [dest] back where a [replaceDirectory] killed between its renames left it aside, the only
 * copy of what it held; nothing to do while [dest] is there. For its owner to call before [dest]
 * is opened, as anything that makes [dest] anew then hides that copy.
 *
 * @throws IOException if it could not be put back
 */
fun restoreDirectory(dest: File) {
    val aside = asideOf(dest)
    if (dest.exists() || !aside.exists()) return
    if (!aside.renameTo(dest)) throw IOException("cannot put ${aside.name} back as ${dest.name}")
    Timber.i("%s put back as %s", aside.name, dest.name)
}

private fun asideOf(dest: File) = File(dest.path + ".old")

private fun File.renameOrThrow(to: File, message: String) {
    if (!renameTo(to)) throw IOException(message)
}
