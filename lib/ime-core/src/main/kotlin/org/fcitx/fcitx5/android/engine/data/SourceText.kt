/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.data

import org.fcitx.fcitx5.android.core.FcitxUtils

/**
 * Spaces, tabs and a stray carriage return. Deliberately not [Char.isWhitespace]: that includes
 * the ideographic space U+3000, which code tables list as a character to type.
 */
fun Char.isSeparator() = this == ' ' || this == '\t' || this == '\r'

fun String.trimSeparators() = trim { it.isSeparator() }

/** [isSeparator]-separated fields; a run of separators counts as one. */
fun String.fields(): List<String> {
    val out = ArrayList<String>(4)
    var start = -1
    for (i in indices) {
        if (this[i].isSeparator()) {
            if (start >= 0) out += substring(start, i)
            start = -1
        } else if (start < 0) {
            start = i
        }
    }
    if (start >= 0) out += substring(start)
    return out
}

/**
 * [value] as fcitx writes one with spaces, quotes or line breaks in it: quoted, `\` escaping a
 * quote, a backslash or one of `n f r t v`, any other character escaped standing for itself.
 * Unchanged if it is not quoted, or a quote ends it early: then the quotes are its own, where
 * fcitx's [FcitxUtils.unescapeForValue], which quick phrases are read with, calls it no value.
 */
fun unescapeValue(value: String): String = FcitxUtils.unescapeForValue(value) ?: value

/** [value] as fcitx's `escapeForValue` writes it, which [unescapeValue] reads back. */
fun escapeValue(value: String): String = FcitxUtils.escapeForValue(value)

/**
 * [line] cut into values as fcitx's `consumeMaybeEscapedValue` cuts it at `FCITX_WHITESPACE`,
 * which libime's text dictionaries are read by: a value starting with a quote runs, unescaped, to
 * the quote closing it (what follows it starts the next value), and one whose quote never closes
 * is taken as it is, to the next whitespace, quote and all.
 */
fun splitValues(line: String): List<String> {
    val out = ArrayList<String>(4)
    val value = StringBuilder()
    var i = 0
    while (true) {
        i = FcitxUtils.consumeMaybeEscapedValue(line, i, WHITESPACE, value)
        if (i < 0) return out
        out += value.toString()
    }
}

private const val WHITESPACE = "\u000C\n\r\t\u000B "
