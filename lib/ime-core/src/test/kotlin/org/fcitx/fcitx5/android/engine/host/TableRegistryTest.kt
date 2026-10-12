/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

/** The user's words as [TableRegistry] codes them for a table, below what a session makes of them. */
class TableRegistryTest : EnginesFixture() {

    @Test
    fun aWordAddedInAnotherReadingOfOneListedIsNotCodedTwice() {
        val additions = Engines.Additions("", "d") { sequenceOf("你好 ni'hao 0") }
        val now = Executor { it.run() }
        val assets = EngineAssets(::load, {}, null)
        val lexicon = UserLexicon(null, {}, { null }, { additions }, assets, now, forgetInTables = {}, dropSessions = {})
        val registry = TableRegistry(::load, null, {}, now, assets, lexicon)
        val wubi = "engine-wubi"
        val shared = registry.shared(registry.table(wubi), wubi)
        assertEquals(listOf("wqvb" to "你好"), shared.words("wqvb"))
        assertTrue(lexicon.addWord("好你", "hao ni"))
        assertTrue(lexicon.addWord("你好", "li hao"))
        assertEquals(listOf("vbwq" to "好你"), shared.words("vbwq"))
        // once is the registry's own doing, nothing the user sees: a session shows a text once however often
        // it is offered (EnginesTest's ...OfferedOnce). Twice would be the word coded again and ranked twice on each key
        assertEquals(listOf("wqvb" to "你好"), shared.words("wqvb"))
    }
}
