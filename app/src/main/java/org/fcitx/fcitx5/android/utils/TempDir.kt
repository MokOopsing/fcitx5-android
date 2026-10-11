/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.utils

import java.io.File
import java.io.IOException
import java.util.UUID

inline fun <T> withTempDir(block: (File) -> T): T {
    // named by the millisecond, two imports at once shared one and the first to end deleted it
    val dir = appContext.cacheDir.resolve(UUID.randomUUID().toString())
    if (!dir.mkdirs()) throw IOException("Cannot create '${dir.path}'")
    try {
        return block(dir)
    } finally {
        dir.deleteRecursively()
    }
}
