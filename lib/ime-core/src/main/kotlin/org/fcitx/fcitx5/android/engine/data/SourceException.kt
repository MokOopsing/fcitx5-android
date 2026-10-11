/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.data

import java.io.BufferedReader

/** A problem in a text source, reported with where it is. */
class SourceException(val source: String, val line: Int, message: String, cause: Throwable? = null) :
    Exception("$source:$line: $message", cause)

/**
 * Calls [block] with each line and its 1-based number, turning a failure inside it into a
 * [SourceException] that points at the line. A leading byte-order mark is dropped.
 */
inline fun BufferedReader.forEachNumberedLine(source: String, block: (line: String, number: Int) -> Unit) {
    var number = 0
    while (true) {
        val raw = readLine() ?: break
        number++
        val line = if (number == 1) raw.removePrefix("\uFEFF") else raw
        try {
            block(line, number)
        } catch (e: SourceException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw SourceException(source, number, e.message ?: e.toString(), e)
        }
    }
}
