/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.clipboard

import android.util.Log
import org.fcitx.fcitx5.android.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import timber.log.Timber

class CleanClipboardTextTest {

    @Test
    fun withoutRulesTheTextPassesThrough() {
        assertEquals("dirty", cleanClipboardText("dirty", null))
    }

    @Test
    fun theCleanerIsApplied() {
        assertEquals("clean", cleanClipboardText("dirty") { "clean" })
    }

    @Test
    fun aThrowingCleanerFallsBackToTheOriginalText() {
        assertEquals("dirty", cleanClipboardText("dirty") { error("bad rule") })
    }

    /** The one warning [clean] logs of a cleaner that throws on a clip with a secret in it. */
    private fun warning(clean: (String, (String) -> String) -> String): String {
        val logged = mutableListOf<Pair<Int, String>>()
        val tree = object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                logged += priority to message
            }
        }
        Timber.plant(tree)
        try {
            clean("https://example.com/secret") { error("bad rule for $it") }
        } finally {
            Timber.uproot(tree)
        }
        val (priority, message) = logged.single()
        assertEquals(Log.WARN, priority)
        return message
    }

    @Test
    fun aThrowingCleanerIsLoggedWithTheClipOnlyInDebugBuilds() {
        // both builds' in one run: CI runs the debug unit tests alone
        assertTrue("secret" in warning { text, cleaner -> cleanClipboardText(text, debug = true, clearUrls = cleaner) })
        val release = warning { text, cleaner -> cleanClipboardText(text, debug = false, clearUrls = cleaner) }
        assertFalse(release, "secret" in release)
        assertTrue(release, IllegalStateException::class.java.name in release)
        // and each build as it is
        assertEquals(BuildConfig.DEBUG, "secret" in warning { text, cleaner -> cleanClipboardText(text, cleaner) })
    }
}
