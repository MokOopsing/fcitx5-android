/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommitTest {

    @Test
    fun whatWasSaidIsTypedIntoTheFieldItWasSaidFor() {
        assertEquals("你好", VoiceCommit.typed("你好", sameField = true, passwordField = false))
    }

    @Test
    fun notIntoAnotherFieldNorAPasswordNorNothing() {
        assertNull(VoiceCommit.typed("你好", sameField = false, passwordField = false))
        assertNull(VoiceCommit.typed("你好", sameField = true, passwordField = true))
        assertNull(VoiceCommit.typed("", sameField = true, passwordField = false))
    }
}
