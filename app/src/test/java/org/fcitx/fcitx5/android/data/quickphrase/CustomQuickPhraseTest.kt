/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.quickphrase

import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import timber.log.Timber
import java.io.File

/** A quick phrase list the user made: turned off and on by renaming its file. */
class CustomQuickPhraseTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun turningOffAndOnRenamesTheFile() {
        val phrase = CustomQuickPhrase(folder.newFile("work.mb"))
        phrase.disable()
        assertFalse(phrase.isEnabled)
        assertEquals(File(folder.root, "work.mb.disable"), phrase.file)
        phrase.enable()
        assertTrue(phrase.isEnabled)
        assertEquals(listOf("work.mb"), folder.root.list()!!.toList())
    }

    // a directory where the file would go: no file is renamed over one, whoever runs the test
    @Test
    fun aRenameThatFailsLeavesItAsItWasAndIsLogged() {
        val logged = mutableListOf<Pair<Int, String>>()
        val tree = object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                logged += priority to message
            }
        }
        Timber.plant(tree)
        try {
            val on = folder.newFile("work.mb")
            folder.newFolder("work.mb.disable")
            val phrase = CustomQuickPhrase(on)
            phrase.disable()
            assertTrue(phrase.isEnabled)
            assertEquals(on, phrase.file)
            val off = folder.newFile("home.mb.disable")
            folder.newFolder("home.mb")
            val other = CustomQuickPhrase(off)
            other.enable()
            assertFalse(other.isEnabled)
            assertEquals(off, other.file)
        } finally {
            Timber.uproot(tree)
        }
        // a warning for each, naming the file and where it was to go; in the log's own words
        assertEquals(logged.toString(), listOf(Log.WARN, Log.WARN), logged.map { it.first })
        listOf("work.mb.disable" to "work.mb", "home.mb.disable" to "home.mb").zip(logged).forEach { (names, log) ->
            val (longer, shorter) = names
            // the shorter name is in the longer: looked for with that taken out
            assertTrue(log.second, longer in log.second && shorter in log.second.replace(longer, ""))
        }
    }
}
