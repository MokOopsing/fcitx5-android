/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

/**
 * What the finger lifting from holding to talk types (VoiceHoldSession): what was said, into the
 * field it was said for only, not one the keyboard has gone to meanwhile, and never a password.
 */
object VoiceCommit {

    /** [text] to type, or null: nothing heard, another field ([sameField] false), or a password one */
    fun typed(text: String, sameField: Boolean, passwordField: Boolean): String? =
        text.takeIf { it.isNotEmpty() && sameField && !passwordField }
}
