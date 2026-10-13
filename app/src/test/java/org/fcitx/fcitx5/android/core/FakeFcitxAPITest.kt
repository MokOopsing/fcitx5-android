/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The fake records generic input-method list changes for callers to inspect. */
class FakeFcitxAPITest {

    private fun entry(name: String) = InputMethodEntry(name, name, "", "", "", "zh_CN", "", false)

    private val rime = entry(RIME_INPUT_METHOD)
    private val other = entry("other")
    private val another = entry("another")

    private fun fcitx() = FakeFcitxAPI().apply { availableIme = arrayOf(rime, other, another) }

    @Test
    fun theInputMethodsEnabledAreInTheOrderGiven() = runTest {
        val fcitx = fcitx()
        fcitx.setEnabledIme(arrayOf("other", "none", RIME_INPUT_METHOD))
        assertEquals(listOf(other, rime), fcitx.enabledIme().toList())
    }

    @Test
    fun theCurrentInputMethodTakenOutIsNoLongerCurrent() = runTest {
        val fcitx = fcitx()
        fcitx.setEnabledIme(arrayOf(RIME_INPUT_METHOD, "other"))
        fcitx.activateIme("other")
        fcitx.setEnabledIme(arrayOf("another", RIME_INPUT_METHOD))
        assertEquals(another, fcitx.currentIme())
        // one still enabled stays current, wherever it moved to
        fcitx.setEnabledIme(arrayOf(RIME_INPUT_METHOD, "another"))
        assertEquals(another, fcitx.currentIme())
    }
}
