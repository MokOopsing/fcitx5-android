/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.utils

/** [name], joined to a directory, names a file directly inside it, not a path. */
internal fun isPlainFileName(name: String) =
    name.isNotEmpty() && name != "." && name != ".." && '/' !in name

/** [name] with its extension in lowercase, as each dictionary and table reader checks it: a file manager may name one `WORDS.SCEL` or `WUBI.TXT`. */
internal fun withLowercaseExtension(name: String): String {
    val dot = name.lastIndexOf('.')
    return if (dot < 0) name else name.substring(0, dot) + name.substring(dot).lowercase()
}
