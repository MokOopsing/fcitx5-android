/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A build made without git has "N/A" for its commit, and the about page linked to `.../commit/N/A`. */
class AboutFragmentTest {

    @Test
    fun aBuildWithoutGitLinksToNoCommit() {
        assertNull(commitUrl(REPO, "N/A"))
        assertNull(commitUrl(REPO, ""))
    }

    @Test
    fun aCommitHashLinksToItsCommit() {
        assertEquals("$REPO/commit/b6868b7f", commitUrl(REPO, "b6868b7f"))
        assertEquals("$REPO/commit/b6868b7f", commitUrl(REPO, "b6868b7f-dirty"))
    }

    private companion object {
        const val REPO = "https://example.invalid/repo"
    }
}
