/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldPolicyTest {

    private val policy = FieldPolicy()

    @Test
    fun aPasswordPairsNothing() {
        policy.focus(password = true)
        assertFalse(policy.pairs(autoPairs = true, rawKeys = false))
        assertTrue(policy.password)
    }

    @Test
    fun ordinaryFieldsPairButRawKeysAndDisabledPairsDoNot() {
        policy.focus(password = false)
        assertTrue(policy.pairs(autoPairs = true, rawKeys = false))
        assertFalse(policy.pairs(autoPairs = false, rawKeys = false))
        assertFalse(policy.pairs(autoPairs = true, rawKeys = true))
    }

    @Test
    fun finishingInputClearsPasswordState() {
        policy.focus(password = true)
        policy.finish()
        assertFalse(policy.password)
    }
}
