/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import android.text.TextUtils
import android.widget.TextView

/**
 * What the two views held to talk in (VoiceWindow, VoiceHoldOverlay) show alike: in one place,
 * so that they stay alike.
 */
object VoiceStyle {

    /** the colour of the finger in the zone where lifting drops it */
    const val CANCEL_RED = 0xFFC62828.toInt()

    /** how long each change they show takes */
    const val ANIMATE_MS = 150L

    /** [view] as it shows what was heard so far: two lines at most */
    fun heard(view: TextView) {
        view.maxLines = 2
        // the end of a long one is what was just said
        view.ellipsize = TextUtils.TruncateAt.START
    }
}
