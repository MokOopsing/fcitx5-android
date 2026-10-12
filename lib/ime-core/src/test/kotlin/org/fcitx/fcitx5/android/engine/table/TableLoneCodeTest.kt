/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.table

import org.fcitx.fcitx5.android.engine.host.EngineEvent
import org.fcitx.fcitx5.android.engine.host.Keyboard
import org.fcitx.fcitx5.android.engine.session.Action.Backspace
import org.fcitx.fcitx5.android.engine.session.Action.Key
import org.fcitx.fcitx5.android.engine.session.Action.Select
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A code with one candidate commits it itself (五笔's 四码唯一), and what follows such a code. */
class TableLoneCodeTest {

    private val wubi = dictionary("a 工", "a 戈", "sie 梢", "sieg 梢", "w 人", "wqiy 你", "wun 们", "wqvb 你好")

    private fun session() = TableSession(wubi, TableOptions.WUBI)

    @Test
    fun aLoneCandidateOfTheWholeCodeCommitsItself() {
        val t = session()
        // wqi leads only to 你, whose code is longer: nothing yet
        assertEquals("", t.type("wqi").first)
        val (commit, s) = t.type("y")
        assertEquals("你", commit)
        assertEquals("", s.preedit)
        assertEquals(emptyList<String>(), s.candidates)
        // at any length, not only a full code
        assertEquals("们", session().type("wun").first)
        // and alone once each text shows once: sie and sieg are both 梢
        assertEquals("梢", session().type("sie").first)
    }

    /** 五笔 users type a space after each code: after one that committed itself it made a gap. */
    @Test
    fun aSpaceRightAfterACodeCommittedItselfIsTakenOnce() {
        val t = session()
        assertEquals("你", t.type("wqiy").first)
        assertTrue(t.reads(' '))
        val taken = t.apply(Key(' '))
        assertTrue(taken.handled)
        assertEquals("", taken.commit)
        // only that one: a second space is the app's
        assertFalse(t.reads(' '))
        // nor after anything else: a code committed by space, then another space
        val u = session()
        u.type("a")
        assertEquals("工", u.apply(Select(0)).commit)
        assertFalse(u.reads(' '))
        // a key between: the space after it is the app's
        val v = session()
        v.type("wqiy")
        v.apply(Backspace)
        assertFalse(v.reads(' '))
    }

    @Test
    fun throughTheKeyboardTheSpaceAfterALoneCodeIsNotTyped() {
        val keyboard = Keyboard(session(), nineKeys = false)
        "wqiy".forEach { keyboard.onEvent(EngineEvent.CHAR, it.code) }
        val space = keyboard.onEvent(EngineEvent.CHAR, ' '.code)
        assertTrue(space.handled)
        assertEquals("", space.commit)
        assertFalse(keyboard.onEvent(EngineEvent.CHAR, ' '.code).handled)
    }
}
