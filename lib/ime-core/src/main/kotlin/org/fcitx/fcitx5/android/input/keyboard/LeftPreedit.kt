/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

/**
 * A Chinese preedit taken out as the screen turned ([PendingInputPolicy.keepsPreeditOnLeaving]),
 * which the app may have put back: most make their activity anew on turning, and saved the field's
 * text, `ni hao` with it, before the input method heard of it. Taking it out reached the field
 * going away; the new one starts with it as plain text before the cursor. Found there just after,
 * it is taken out of that one.
 */
class LeftPreedit(private val within: Long = WITHIN) {

    private var text = ""
    private var at = 0L

    /** The preedit [text] was taken out at [now] (milliseconds). */
    fun dropped(text: String, now: Long) {
        this.text = text
        at = now
    }

    /**
     * How many characters before the cursor of a field starting at [now] are the preedit put back:
     * [before] gives so many, the selection is [selStart]..[selEnd]. Asked once: a later field's
     * text is its own.
     */
    fun putBack(before: (Int) -> CharSequence?, selStart: Int, selEnd: Int, now: Long): Int {
        val left = text
        text = ""
        if (left.isEmpty() || now - at > within || selStart != selEnd) return 0
        return if (before(left.length)?.toString() == left) left.length else 0
    }

    companion object {
        /** From the turn to the new activity's field: about a second on a slow device. */
        const val WITHIN = 3000L
    }
}
