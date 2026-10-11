/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.im

import kotlinx.coroutines.test.runTest
import org.fcitx.fcitx5.android.core.FakeFcitxAPI
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.engine.host.Engines
import org.fcitx.fcitx5.android.engine.host.LibimeMigration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The engine is loaded again for an imported table only, whose options it reads as it loads the
 * table, and once as its page is left, or as a save ends after it was: on each option changed,
 * every session was dropped each time.
 */
class InputMethodConfigFragmentTest {

    private fun entry(uniqueName: String, addon: String = LibimeMigration.ENGINE_ADDON) =
        InputMethodEntry(uniqueName, uniqueName, "", "", "", "zh_CN", addon, true)

    // as onStop does, with fcitx ready
    private suspend fun ReloadOnLeave.leave(fcitx: FakeFcitxAPI) {
        leaving()
        left(fcitx)
    }

    @Test
    fun onlyATableTheUserImportedIsLoadedAgain() {
        assertTrue(isImportedTable(entry("cangjie5")))
        // libime's wbx imported, named apart from the engine's own
        assertTrue(isImportedTable(entry(LibimeMigration.importedTableName("wbx"))))
        (listOf(Engines.PINYIN, Engines.SHUANGPIN, Engines.T9) + Engines.TABLES.keys).forEach {
            assertFalse(it, isImportedTable(entry(it)))
        }
        assertFalse(isImportedTable(entry("keyboard-us", addon = "keyboard")))
    }

    @Test
    fun severalOptionsChangedOnAnImportedTableLoadTheEngineOnceWhenThePageIsLeft() = runTest {
        val fcitx = FakeFcitxAPI().apply { availableIme = arrayOf(entry("cangjie5")) }
        val reload = ReloadOnLeave()
        repeat(3) { reload.saved(fcitx, "cangjie5") }
        assertEquals(emptyList<String>(), fcitx.calls)
        reload.leave(fcitx)
        reload.leave(fcitx)
        assertEquals(listOf("reloadEngine()"), fcitx.calls)
    }

    @Test
    fun anotherInputMethodsPageLoadsNothingAgain() = runTest {
        val fcitx = FakeFcitxAPI().apply { availableIme = arrayOf(entry(Engines.PINYIN)) }
        val reload = ReloadOnLeave()
        reload.saved(fcitx, Engines.PINYIN)
        reload.leave(fcitx)
        assertEquals(emptyList<String>(), fcitx.calls)
    }

    @Test
    fun aSaveThatEndsAfterThePageWasLeftLoadsTheEngineItself() = runTest {
        // left by Home while the save still ran: left() found nothing to load
        val fcitx = FakeFcitxAPI().apply { availableIme = arrayOf(entry("cangjie5")) }
        val reload = ReloadOnLeave()
        reload.leave(fcitx)
        reload.saved(fcitx, "cangjie5")
        assertEquals(listOf("reloadEngine()"), fcitx.calls)
        reload.leave(fcitx)
        assertEquals(listOf("reloadEngine()"), fcitx.calls)
    }

    @Test
    fun onceThePageIsShownAgainASaveWaitsForItToBeLeft() = runTest {
        val fcitx = FakeFcitxAPI().apply { availableIme = arrayOf(entry("cangjie5")) }
        val reload = ReloadOnLeave()
        reload.leave(fcitx)
        reload.shown()
        reload.saved(fcitx, "cangjie5")
        assertEquals(emptyList<String>(), fcitx.calls)
        reload.leave(fcitx)
        assertEquals(listOf("reloadEngine()"), fcitx.calls)
    }

    @Test
    fun aLeaveThatWaitedForFcitxPastTheNextShowLeavesTheSavesAfterItWaiting() = runTest {
        // left while fcitx was not ready, and shown again before it was
        val fcitx = FakeFcitxAPI().apply { availableIme = arrayOf(entry("cangjie5")) }
        val reload = ReloadOnLeave()
        reload.saved(fcitx, "cangjie5")
        reload.leaving()
        reload.shown()
        reload.left(fcitx)
        assertEquals(listOf("reloadEngine()"), fcitx.calls)
        repeat(2) { reload.saved(fcitx, "cangjie5") }
        assertEquals(listOf("reloadEngine()"), fcitx.calls)
        reload.leave(fcitx)
        assertEquals(listOf("reloadEngine()", "reloadEngine()"), fcitx.calls)
    }
}
