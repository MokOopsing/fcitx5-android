/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

/**
 * What happens to half-typed input when a key does something other than typing -- commits a
 * symbol, starts quick phrase or unicode input. fcitx is reset afterwards either way; this
 * decides what the user keeps first.
 */
object PendingInputPolicy {

    enum class Resolution {
        /** Take the first candidate, as Space would. */
        SelectFirstCandidate,

        /** Keep the preedit in the editor exactly as typed. */
        FinishComposing,

        /** The input method commits what was typed, its letters (Enter's), not a candidate. */
        CommitAsTyped,

        /** Nothing to keep. */
        None,
    }

    /**
     * Chinese input methods turn their preedit (pinyin, say) into the first candidate: the raw
     * preedit is rarely what anyone wants in the text. With no preedit the candidates on offer
     * are predictions, which are not taken. Other languages type the text itself as the
     * preedit, so it is kept as is.
     *
     * In an [address] field (a URL's, an email's) what is typed is what is meant: `xiaoming` then
     * `@` is xiaoming@, not 小明@. A candidate is still there to pick before the key.
     *
     * @param hasPreedit whether there is a preedit, in the editor or on fcitx's input panel
     */
    fun resolve(languageCode: String, hasPreedit: Boolean, address: Boolean = false): Resolution = when {
        !languageCode.startsWith("zh") -> Resolution.FinishComposing
        hasPreedit && address -> Resolution.CommitAsTyped
        hasPreedit -> Resolution.SelectFirstCandidate
        else -> Resolution.None
    }

    /**
     * Whether the editor keeps the preedit when the keyboard goes (hidden, the screen turned, another
     * app): other languages' is their text; a Chinese one is pinyin, and kept it left `ni hao` in
     * the text. Its first candidate cannot take its place: it comes from fcitx's thread, after the
     * field may have gone, into another app's field even. So it goes, as typed into nothing.
     */
    fun keepsPreeditOnLeaving(languageCode: String): Boolean = !languageCode.startsWith("zh")
}
