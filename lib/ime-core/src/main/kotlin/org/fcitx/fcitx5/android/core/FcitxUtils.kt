/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024-2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.core

object FcitxUtils {

    // https://github.com/fcitx/fcitx5/blob/5.1.21/src/lib/fcitx-utils/stringutils.cpp#L100
    // https://en.cppreference.com/cpp/language/escape
    private val unescapeMap = mapOf(
        '\\' to '\\',
        '"' to '"',
        'n' to '\n',
        'f' to '\u000c',
        'r' to '\r',
        't' to '\t',
        'v' to '\u000b',
    )

    // https://github.com/fcitx/fcitx5/blob/5.1.21/src/lib/fcitx-utils/stringutils.cpp#L390
    // null where a quote closes before the end, as fcitx's std::nullopt
    fun unescapeForValue(str: String): String? {
        if (str.length < 2 || !str.startsWith('"') || !str.endsWith('"')) return str
        val value = StringBuilder(str.length)
        return if (consumeMaybeEscapedValue(str, 0, "", value) == str.length) value.toString() else null
    }

    // https://github.com/fcitx/fcitx5/blob/5.1.21/src/lib/fcitx-utils/stringutils.cpp#L435
    /**
     * The value in [input] from [from] on, past any of [skip]: one starting with a quote
     * unescaped up to the quote closing it, else (or if that quote never closes) as it is, up to
     * the next of [skip]. Put in [out]; returns where it ends, or -1 if only [skip] is left.
     */
    fun consumeMaybeEscapedValue(input: String, from: Int, skip: String, out: StringBuilder): Int {
        out.setLength(0)
        var start = from
        while (start < input.length && input[start] in skip) start++
        if (start == input.length) return -1
        if (input[start] == '"') {
            var i = start + 1
            while (i < input.length) {
                val c = input[i]
                if (c == '"') return i + 1
                if (c == '\\' && i + 1 < input.length) {
                    i++
                    // treat invalid escape sequence as normal character
                    out.append(unescapeMap[input[i]] ?: input[i])
                } else {
                    out.append(c)
                }
                i++
            }
            out.setLength(0)
        }
        var end = start + 1
        while (end < input.length && input[end] !in skip) end++
        out.append(input, start, end)
        return end
    }

    // https://github.com/fcitx/fcitx5/blob/5.1.21/src/lib/fcitx-utils/stringutils.cpp#L86
    private val escapeMap = mapOf(
        '\\' to '\\',
        '"' to '"',
        '\n' to 'n',
        '\u000c' to 'f',
        '\r' to 'r',
        '\t' to 't',
        '\u000b' to 'v',
    )

    // "\f\r\t\v \"\\\n"
    private val EscapeChars = charArrayOf('\u000c', '\r', '\t', '\u000b', ' ', '"', '\\', '\n')

    // https://github.com/fcitx/fcitx5/blob/5.1.21/src/lib/fcitx-utils/stringutils.cpp#L404
    fun escapeForValue(str: String): String {
        if (str.lastIndexOfAny(EscapeChars) < 0) return str
        return buildString {
            append('"')
            str.forEach { c ->
                val escape = escapeMap[c]
                if (escape != null) {
                    append('\\')
                    append(escape)
                } else {
                    append(c)
                }
            }
            append('"')
        }
    }
}
