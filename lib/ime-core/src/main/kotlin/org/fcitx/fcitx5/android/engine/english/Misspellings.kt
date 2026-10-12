/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.english

/**
 * Misspellings the model learned as words from the web (recieve, definately, untill: in the pages
 * often enough that the right word was not [EnglishSession.KNOWN_MARGIN] likelier), each to the
 * word meant: the list `misspellings.txt` beside this class.
 */
object Misspellings {

    /** Misspelling, lower case, to the word meant. */
    val MAP: Map<String, String> by lazy {
        val stream = requireNotNull(Misspellings::class.java.getResourceAsStream("misspellings.txt")) { "misspellings.txt" }
        stream.bufferedReader().useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
                .associate { it.substringBefore(' ') to it.substringAfter(' ') }
        }
    }
}
