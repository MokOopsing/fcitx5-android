/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.user

/**
 * The words [UserModel] made, by the syllables they are read as, for the decoder to walk as
 * [org.fcitx.fcitx5.android.engine.lattice.UserWords]: node 0 is the root, and each node has
 * its children by syllable, sorted, and its words.
 */
internal class SyllableTrie {
    private val childSyllables = arrayListOf(IntArray(0))
    private val childNodes = arrayListOf(IntArray(0))
    private val nodeWords = arrayListOf(IntArray(0))

    /** @return the child of [node] reached by [syllable], or -1 */
    fun child(node: Int, syllable: Int): Int {
        val syllables = childSyllables[node]
        val at = syllables.binarySearch(syllable)
        return if (at >= 0) childNodes[node][at] else -1
    }

    fun childCount(node: Int) = childSyllables[node].size

    fun wordCount(node: Int) = nodeWords[node].size

    fun word(node: Int, index: Int) = nodeWords[node][index]

    /** Puts [word] at the node [syllables] lead to, making the nodes it lacks. */
    fun add(syllables: IntArray, word: Int) {
        var node = ROOT
        for (s in syllables) {
            var next = child(node, s)
            if (next < 0) {
                next = nodeWords.size
                childSyllables += IntArray(0)
                childNodes += IntArray(0)
                nodeWords += IntArray(0)
                val at = -(childSyllables[node].binarySearch(s) + 1)
                childSyllables[node] = childSyllables[node].inserted(at, s)
                childNodes[node] = childNodes[node].inserted(at, next)
            }
            node = next
        }
        nodeWords[node] = nodeWords[node] + word
    }

    // the nodes stay: a walk down them finds no word, and the next start leaves them out
    fun remove(syllables: IntArray, word: Int) {
        var node = ROOT
        for (s in syllables) {
            node = child(node, s)
            if (node < 0) return
        }
        nodeWords[node] = nodeWords[node].filter { it != word }.toIntArray()
    }

    private fun IntArray.inserted(at: Int, value: Int) = IntArray(size + 1) {
        when {
            it < at -> this[it]
            it == at -> value
            else -> this[it - 1]
        }
    }

    private companion object {
        const val ROOT = 0
    }
}
