/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.search

/**
 * Finds settings by what the user types: every word of the query somewhere in a setting's
 * title, its summary or the pages it is under, in any case. Titles that match come first, those
 * that start with the query before them; otherwise the settings keep their order, which is the
 * order of the pages.
 *
 * @param T where a setting is: what opening a result needs
 */
class SettingsSearch<T>(entries: List<Entry<T>>) {

    /** A setting: what it is called, what it says under that, and the pages it is on, outermost first. */
    data class Entry<T>(val title: String, val summary: String, val path: List<String>, val target: T)

    /** An entry with its text lower-cased once: [find] runs on every key typed. */
    private class Indexed<T>(val entry: Entry<T>) {
        val title = entry.title.lowercase()
        val all = listOf(title, entry.summary.lowercase(), entry.path.joinToString(" ").lowercase()).joinToString("\n")
    }

    // the same setting reached twice (a page listed under two others) is offered once
    private val entries = entries.filter { it.title.isNotBlank() }.distinctBy { it.title to it.path }.map { Indexed(it) }

    fun find(query: String, limit: Int = LIMIT): List<Entry<T>> {
        val terms = query.lowercase().split(WHITESPACE).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()
        val whole = terms.joinToString(" ")
        return entries.asSequence()
            .mapNotNull { e -> rank(e, terms, whole)?.let { e.entry to it } }
            .sortedBy { it.second } // stable: equal ranks keep the pages' order
            .take(limit)
            .map { it.first }
            .toList()
    }

    private fun rank(e: Indexed<T>, terms: List<String>, whole: String): Int? {
        if (terms.any { it !in e.all }) return null
        return when {
            e.title.startsWith(whole) -> TITLE_START
            terms.all { it in e.title } -> TITLE
            else -> ELSEWHERE
        }
    }

    private companion object {
        const val LIMIT = 50
        const val TITLE_START = 0
        const val TITLE = 1
        const val ELSEWHERE = 2

        // \p{Z} for the full-width space a Chinese keyboard types
        val WHITESPACE = Regex("[\\s\\p{Z}]+")
    }
}
