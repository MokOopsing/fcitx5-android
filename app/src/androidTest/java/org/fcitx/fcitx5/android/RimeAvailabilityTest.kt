/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.runBlocking
import org.fcitx.fcitx5.android.core.Fcitx
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.RIME_INPUT_METHOD
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.Rule

class RimeAvailabilityTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(90)

    @Test
    fun onlyRimeIsAvailableAndCanBeEnabled() = runBlocking {
        checkThisInputMethodIsNotBound()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fcitx = Fcitx(context)
        val ready = CoroutineScope(Dispatchers.Default).async(start = CoroutineStart.UNDISPATCHED) {
            fcitx.eventFlow.first { it is FcitxEvent.ReadyEvent }
        }
        fcitx.start()
        try {
            withTimeout(60_000) { ready.await() }
            assertEquals(listOf(RIME_INPUT_METHOD), fcitx.availableIme().map { it.uniqueName })
            fcitx.setEnabledIme(arrayOf(RIME_INPUT_METHOD))
            assertEquals(listOf(RIME_INPUT_METHOD), fcitx.enabledIme().map { it.uniqueName })
        } finally {
            fcitx.stop()
        }
    }
}
