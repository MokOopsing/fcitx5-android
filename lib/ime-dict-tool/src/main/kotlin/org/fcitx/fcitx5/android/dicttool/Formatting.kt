/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.dicttool

import java.util.Formatter
import java.util.Locale

/**
 * Runs [write] with one Formatter over [out], for the millions of lines a file gets (String.format
 * makes one for every call), then throws the IOException of a write that failed: a Formatter
 * keeps it instead of throwing it, and a file cut short would pass for whole.
 */
internal fun formatting(out: Appendable, write: (Formatter) -> Unit) {
    val formatter = Formatter(out, Locale.ROOT)
    write(formatter)
    formatter.ioException()?.let { throw it }
}
