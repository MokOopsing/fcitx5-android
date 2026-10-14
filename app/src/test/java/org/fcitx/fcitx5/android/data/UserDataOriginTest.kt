/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserDataOriginTest {

    private val root = "io.github.mokoopsing.youmo.rime"

    private fun of(exporter: String, applicationId: String) = UserDataOrigin.of(exporter, applicationId, root)

    @Test
    fun theAppsOwnDataIsImportedAsItIs() {
        assertEquals(UserDataOrigin.Own, of(root, root))
        assertEquals(UserDataOrigin.Own, of("$root.debug", "$root.debug"))
    }

    @Test
    fun dataFromBeforeTheRenamingIsImportedByTheSameBuildTypeWithItsPreferencesRenamed() {
        assertEquals(
            UserDataOrigin.Legacy(
                "org.fcitx.fcitx5.android_preferences.xml",
                "${root}_preferences.xml"
            ),
            of("org.fcitx.fcitx5.android", root)
        )
        assertEquals(
            UserDataOrigin.Legacy(
                "org.fcitx.fcitx5.android.debug_preferences.xml",
                "${root}.debug_preferences.xml"
            ),
            of("org.fcitx.fcitx5.android.debug", "$root.debug")
        )
        assertEquals(
            UserDataOrigin.Legacy(
                "io.github.sraw.youmo_preferences.xml",
                "${root}_preferences.xml"
            ),
            of("io.github.sraw.youmo", root)
        )
        assertEquals(
            UserDataOrigin.Legacy(
                "io.github.sraw.youmo.debug_preferences.xml",
                "${root}.debug_preferences.xml"
            ),
            of("io.github.sraw.youmo.debug", "$root.debug")
        )
    }

    @Test
    fun otherAppsAndOtherBuildTypesAreNot() {
        assertNull(of("org.fcitx.fcitx5.android.debug", root))
        assertNull(of("org.fcitx.fcitx5.android", "$root.debug"))
        assertNull(of("$root.debug", root))
        assertNull(of("com.example.ime", root))
    }

    @Test
    fun anIdOutsideTheRootHasNoLegacy() {
        assertNull(UserDataOrigin.of("org.fcitx.fcitx5.android", "com.example.other", root))
    }
}
