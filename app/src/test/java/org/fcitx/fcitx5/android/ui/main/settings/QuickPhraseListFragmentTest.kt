/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.fcitx.fcitx5.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A new quick phrase list is the file `<name>.mb`. A name with a slash crashed the app, and a name
 * already listed added a second row over the same file, so removing either deleted both.
 * A reload fcitx stopped under took the app down; the catch for it then let any bug go as well.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuickPhraseListFragmentTest {

    @Test
    fun aNameWithASlashIsRefused() {
        assertEquals(R.string.invalid_value, newQuickPhraseNameError("a/b", emptyList()))
    }

    @Test
    fun aNameAlreadyListedIsRefusedWhateverItsCase() {
        assertEquals(R.string.quickphrase_already_exists, newQuickPhraseNameError("emoji", listOf("emoji")))
        assertEquals(R.string.quickphrase_already_exists, newQuickPhraseNameError("Emoji", listOf("emoji")))
    }

    @Test
    fun anUnusedNameIsAccepted() {
        assertNull(newQuickPhraseNameError("work", listOf("emoji")))
    }

    @Test
    fun aReloadFcitxStoppedUnderIsHandedOnNotThrown() = runTest {
        val stopped = mutableListOf<IllegalStateException>()
        // what FcitxDispatcher.dispatch throws once fcitx has stopped
        val notRunning = IllegalStateException("Dispatcher is not in running state!")
        reloadingUnlessStopped({ false }, { stopped += it }) { throw notRunning }
        assertEquals(listOf(notRunning), stopped)
    }

    @Test
    fun aBugWhileFcitxIsReadyGoesOn() = runTest {
        val stopped = mutableListOf<IllegalStateException>()
        val thrown = runCatching { reloadingUnlessStopped({ true }, { stopped += it }) { error("a bug") } }.exceptionOrNull()
        // to the crash handler, not a log line
        assertTrue(thrown.toString(), thrown is IllegalStateException)
        assertEquals(emptyList<IllegalStateException>(), stopped)
    }

    @Test
    fun aCancellationIsNoStop() = runTest {
        val stopped = mutableListOf<IllegalStateException>()
        val job = launch { reloadingUnlessStopped({ false }, { stopped += it }) { awaitCancellation() } }
        runCurrent()
        job.cancel()
        // its catch over before stopped is read
        job.join()
        assertEquals(emptyList<IllegalStateException>(), stopped)
    }
}
