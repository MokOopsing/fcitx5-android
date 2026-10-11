/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowToast
import java.io.IOException

/** A file the user picked that cannot be read or written is told of, where thrown it would close the app. */
@RunWith(RobolectricTestRunner::class)
class PickedFilesTest {

    private val ctx = RuntimeEnvironment.getApplication()

    @Test
    fun aFileThatCannotBeReadOrOpenedIsShownNotThrown() {
        ctx.showingFileErrors("test") { throw IOException("cannot read x") }
        assertEquals("cannot read x", ShadowToast.getTextOfLatestToast())
        ctx.showingFileErrors("test") { throw SecurityException("not yours") }
        assertEquals("not yours", ShadowToast.getTextOfLatestToast())
    }

    @Test(expected = IllegalStateException::class)
    fun aBugIsNotTakenForOne() {
        ctx.showingFileErrors("test") { error("a bug") }
    }
}
