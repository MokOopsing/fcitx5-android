/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input

/**
 * Punctuation as Unicode's general categories have it: `，` `“` `-` `%`, not `+` (a symbol). Its
 * own, not any one reader's: AutoPairs reads it for what may follow the cursor where a bracket
 * opens in pairs, VoiceRerank for which hypotheses are alike but for it; a rule of one of them
 * goes in that one.
 */
object UnicodePunctuation {

    private val CATEGORIES = setOf(
        Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
        Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
        Character.OTHER_PUNCTUATION,
    )

    operator fun contains(c: Char): Boolean = Character.getType(c).toByte() in CATEGORIES
}
