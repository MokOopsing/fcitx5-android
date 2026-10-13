/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

/**
 * User labels for letter keys, kept separately for each input method and optionally its submode.
 * A missing entry leaves the key's usual corner label in place and never changes what it types.
 *
 * Stored one mapping per tab-separated line as input method, scope type, submode, letter, and
 * label. Older three-field whitespace and four-field tab-separated records are still read.
 */
class KeyLabelOverrides private constructor(
    private val table: Map<Scope, Map<String, String>>,
) {

    data class Scope(val inputMethod: String, val subMode: String? = null)

    val isEmpty: Boolean get() = table.isEmpty()

    fun labelsFor(scope: Scope): Map<String, String> = table[scope].orEmpty()

    fun labelsFor(inputMethod: String, subMode: String? = null): Map<String, String> =
        buildMap {
            putAll(table[Scope(inputMethod)].orEmpty())
            if (!subMode.isNullOrEmpty()) putAll(table[Scope(inputMethod, subMode)].orEmpty())
        }

    fun subModesFor(inputMethod: String): Set<String> = buildSet {
        table.keys.forEach { scope ->
            if (scope.inputMethod == inputMethod) scope.subMode?.let(::add)
        }
    }

    fun with(scope: Scope, key: String, label: String): KeyLabelOverrides {
        require(isValidScope(scope))
        require(key.length == 1 && key[0] in 'a'..'z')
        require(isValidLabel(label))
        return KeyLabelOverrides(LinkedHashMap(table).also { updated ->
            updated[scope] = LinkedHashMap(table[scope].orEmpty()).also { it[key] = label }
        })
    }

    fun without(scope: Scope, key: String): KeyLabelOverrides {
        val labels = table[scope] ?: return this
        if (key !in labels) return this
        val updated = LinkedHashMap(table)
        val remaining = LinkedHashMap(labels).also { it.remove(key) }
        if (remaining.isEmpty()) updated.remove(scope) else updated[scope] = remaining
        return if (updated.isEmpty()) Empty else KeyLabelOverrides(updated)
    }

    fun without(scope: Scope): KeyLabelOverrides =
        if (scope !in table) this
        else LinkedHashMap(table).also { it.remove(scope) }
            .takeIf { it.isNotEmpty() }
            ?.let(::KeyLabelOverrides) ?: Empty

    fun serialize(): String = table.flatMap { (scope, labels) ->
        labels.map { (key, label) ->
            listOf(
                scope.inputMethod,
                if (scope.subMode == null) "ime" else "submode",
                scope.subMode ?: "-",
                key,
                label,
            ).joinToString("\t")
        }
    }.joinToString("\n")

    override fun equals(other: Any?): Boolean = other is KeyLabelOverrides && other.table == table

    override fun hashCode(): Int = table.hashCode()

    override fun toString(): String = "KeyLabelOverrides($table)"

    companion object {
        val Empty = KeyLabelOverrides(emptyMap())

        private val whitespace = Regex("[\\s\\p{Z}]+")

        fun isValidLabel(label: String): Boolean =
            label.isNotEmpty() && !whitespace.containsMatchIn(label) && label.none(Char::isWhitespace)

        private fun isValidScope(scope: Scope) =
            scope.inputMethod.isNotBlank() &&
                !scope.inputMethod.any(Char::isWhitespace) &&
                scope.subMode?.let { mode ->
                    mode.isNotEmpty() && mode.none { it == '\t' || it == '\n' || it == '\r' }
                } != false

        /** Invalid or incomplete records are ignored so a malformed preference cannot break typing. */
        fun parse(text: String): KeyLabelOverrides {
            val table = LinkedHashMap<Scope, MutableMap<String, String>>()
            for (line in text.lineSequence()) {
                val fields = if ('\t' in line) line.split('\t') else whitespace.split(line.trim())
                val record = when (fields.size) {
                    5 -> {
                        val (inputMethod, type, subMode, key, label) = fields
                        val scope = when (type) {
                            "ime" -> if (subMode == "-" || subMode.isEmpty()) {
                                Scope(inputMethod)
                            } else {
                                continue
                            }
                            "submode" -> Scope(inputMethod, subMode)
                            else -> continue
                        }
                        Triple(scope, key, label)
                    }
                    4 -> {
                        val (inputMethod, subMode, key, label) = fields
                        Triple(Scope(inputMethod, subMode.takeUnless { it == "-" }), key, label)
                    }
                    3 -> Triple(Scope(fields[0]), fields[1], fields[2])
                    else -> continue
                }
                val (scope, key, label) = record
                if (!isValidScope(scope) || key.length != 1 || key[0] !in 'a'..'z' ||
                    !isValidLabel(label)
                ) continue
                table.getOrPut(scope, ::LinkedHashMap)[key] = label
            }
            return if (table.isEmpty()) Empty else KeyLabelOverrides(table)
        }
    }
}
