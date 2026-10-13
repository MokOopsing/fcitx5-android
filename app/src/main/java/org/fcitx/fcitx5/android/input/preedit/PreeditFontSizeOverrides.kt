/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.preedit

/** Font sizes saved for an input method, optionally narrowed to one of its submodes. */
class PreeditFontSizeOverrides private constructor(
    private val sizes: Map<Scope, Int>,
) {

    data class Scope(val inputMethod: String, val subMode: String? = null)

    fun fontSizeFor(inputMethod: String, subMode: String?, defaultSize: Int): Int =
        subMode?.let { sizes[Scope(inputMethod, it)] } ?: sizes[Scope(inputMethod)] ?: defaultSize

    fun fontSizeFor(scope: Scope): Int? = sizes[scope]

    fun subModesFor(inputMethod: String): Set<String> = buildSet {
        sizes.keys.forEach { scope ->
            if (scope.inputMethod == inputMethod) scope.subMode?.let(::add)
        }
    }

    fun with(scope: Scope, size: Int): PreeditFontSizeOverrides {
        require(isValidScope(scope))
        require(size in MIN_SIZE..MAX_SIZE)
        return PreeditFontSizeOverrides(LinkedHashMap(sizes).also { it[scope] = size })
    }

    fun without(scope: Scope): PreeditFontSizeOverrides =
        if (scope !in sizes) this
        else LinkedHashMap(sizes).also { it.remove(scope) }
            .takeIf { it.isNotEmpty() }
            ?.let(::PreeditFontSizeOverrides) ?: Empty

    fun serialize(): String = sizes.map { (scope, size) ->
        listOf(scope.inputMethod, scope.subMode.orEmpty(), size.toString()).joinToString("\t")
    }.joinToString("\n")

    companion object {
        const val MIN_SIZE = 8
        const val MAX_SIZE = 64

        val Empty = PreeditFontSizeOverrides(emptyMap())

        private fun isValidScope(scope: Scope): Boolean =
            scope.inputMethod.isNotBlank() &&
                scope.inputMethod.none { it == '\t' || it == '\n' || it == '\r' } &&
                scope.subMode?.let { mode ->
                    mode.isNotEmpty() && mode.none { it == '\t' || it == '\n' || it == '\r' }
                } != false

        fun parse(text: String): PreeditFontSizeOverrides {
            val sizes = LinkedHashMap<Scope, Int>()
            text.lineSequence().forEach { line ->
                val fields = line.split('\t')
                if (fields.size != 3) return@forEach
                val (inputMethod, subMode, rawSize) = fields
                val scope = Scope(inputMethod, subMode.takeIf(String::isNotEmpty))
                val size = rawSize.toIntOrNull()?.takeIf { it in MIN_SIZE..MAX_SIZE }
                    ?: return@forEach
                if (isValidScope(scope)) sizes[scope] = size
            }
            return if (sizes.isEmpty()) Empty else PreeditFontSizeOverrides(sizes)
        }
    }
}
