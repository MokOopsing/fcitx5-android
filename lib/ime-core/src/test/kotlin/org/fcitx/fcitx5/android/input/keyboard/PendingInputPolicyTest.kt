/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import org.fcitx.fcitx5.android.input.keyboard.PendingInputPolicy.Resolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class PendingInputPolicyTest {

    @Test
    fun chineseTakesTheFirstCandidateForAPreedit() {
        assertEquals(Resolution.SelectFirstCandidate, PendingInputPolicy.resolve("zh_CN", hasPreedit = true))
        assertEquals(Resolution.SelectFirstCandidate, PendingInputPolicy.resolve("zh_TW", hasPreedit = true))
        assertEquals(Resolution.SelectFirstCandidate, PendingInputPolicy.resolve("zh", hasPreedit = true))
    }

    @Test
    fun chineseLeavesPredictionsAlone() {
        assertEquals(Resolution.None, PendingInputPolicy.resolve("zh_CN", hasPreedit = false))
    }

    @Test
    fun otherLanguagesKeepWhatWasTyped() {
        assertEquals(Resolution.FinishComposing, PendingInputPolicy.resolve("en", hasPreedit = true))
        assertEquals(Resolution.FinishComposing, PendingInputPolicy.resolve("ja", hasPreedit = true))
        assertEquals("finishing with nothing composed is harmless", Resolution.FinishComposing,
            PendingInputPolicy.resolve("en", hasPreedit = false))
    }

    /**
     * Characterises, not specifies: the upstream check is a plain prefix test on fcitx's
     * language code, case and all, so "zhuang" counts as Chinese.
     */
    @Test
    fun currentlyTheLanguageIsMatchedByAPlainPrefix() {
        assertEquals(Resolution.FinishComposing, PendingInputPolicy.resolve("", hasPreedit = true))
        assertEquals(Resolution.FinishComposing, PendingInputPolicy.resolve("ZH_CN", hasPreedit = true))
        assertEquals(Resolution.SelectFirstCandidate, PendingInputPolicy.resolve("zhuang", hasPreedit = true))
    }

    /** xiaoming then @ in an email field was 小明@: there what is typed is meant. */
    @Test
    fun inAnAddressFieldChineseCommitsWhatWasTyped() {
        assertEquals(Resolution.CommitAsTyped, PendingInputPolicy.resolve("zh_CN", hasPreedit = true, address = true))
        // nothing typed: nothing to commit, predictions are not taken there either
        assertEquals(Resolution.None, PendingInputPolicy.resolve("zh_CN", hasPreedit = false, address = true))
        // other languages type the text itself already
        assertEquals(Resolution.FinishComposing, PendingInputPolicy.resolve("en", hasPreedit = true, address = true))
    }

    /** The screen turned mid-pinyin left `ni hao` in the text. */
    @Test
    fun leavingAChinesePreeditDropsItAndOtherLanguagesKeepTheirs() {
        assertFalse(PendingInputPolicy.keepsPreeditOnLeaving("zh_CN"))
        assertTrue(PendingInputPolicy.keepsPreeditOnLeaving("en"))
        assertTrue(PendingInputPolicy.keepsPreeditOnLeaving(""))
    }
}
