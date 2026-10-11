/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.utils

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.IOException

object FileUtil {

    private fun File.isSymlink(): Boolean = OsConstants.S_ISLNK(Os.lstat(path).st_mode)

    // exists() follows a symlink, so a dangling one was kept and symlink() over it failed
    private fun File.existsNoFollow(): Boolean =
        exists() || try {
            isSymlink()
        } catch (_: ErrnoException) {
            false
        }

    /**
     * Delete a [File].
     * If it's a directory, delete its contents first.
     * If it's a symlink, don't follow.
     */
    fun removeFile(file: File) = runCatching {
        if (!file.existsNoFollow())
            return@runCatching
        val result = if (file.isSymlink()) {
            file.delete()
        } else if (file.isDirectory) {
            file.walkBottomUp()
                .onEnter {
                    // delete symlink (to directory) instead of entering it
                    if (it.isSymlink()) {
                        it.delete()
                        false
                    } else {
                        true
                    }
                }
                .fold(true) { acc, it ->
                    // delete first: a failure is kept, and the files after it are still deleted
                    if (!it.existsNoFollow()) acc else it.delete() && acc
                }
        } else {
            file.delete()
        }
        if (!result)
            throw IOException("Cannot delete '${file.path}'")
    }

    fun symlink(source: File, target: File) = runCatching {
        target.parentFile?.mkdirs()
        Os.symlink(source.path, target.path)
    }
}