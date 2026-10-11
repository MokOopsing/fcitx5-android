/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.english

import org.fcitx.fcitx5.android.engine.host.EngineEvent
import org.fcitx.fcitx5.android.engine.host.Keyboard
import org.fcitx.fcitx5.android.engine.session.Action
import org.fcitx.fcitx5.android.engine.session.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishSessionTest {

    private val words = EnglishWords.of(mapOf("hello" to 9, "help" to 8, "he" to 7, "i'm" to 6, "the" to 5, "i" to 9))

    private fun session() = EnglishSession(words)

    private fun EnglishSession.type(text: String): Snapshot = text.map { apply(Action.Key(it)) }.last()

    @Test
    fun theWordTypedComesFirstThenWhatItMayBe() {
        val s = session().type("hel")
        assertEquals("hel", s.preedit)
        // the longer words it starts, then, being no word, those one edit away
        assertEquals(listOf("hel", "hello", "help", "he"), s.candidates)
        assertEquals("the", session().type("teh").candidates[1])
    }

    @Test
    fun suggestionsTakeTheCaseOfWhatIsTyped() {
        assertEquals(listOf("Hel", "Hello", "Help"), session().type("Hel").candidates.take(3))
        assertEquals(listOf("HEL", "HELLO", "HELP"), session().type("HEL").candidates.take(3))
        // one capital letter is a word's start, whatever follows it
        assertEquals("I'm", session().type("I'").candidates[1])
    }

    @Test
    fun spaceCommitsTheWordTypedAndASpace() {
        val session = session()
        session.type("hel")
        val s = session.apply(Action.Key(' '))
        assertEquals("hel ", s.commit)
        assertTrue(s.handled)
        assertEquals("", s.preedit)
    }

    @Test
    fun aWordPickedIsFollowedByASpace() {
        val session = session()
        session.type("hel")
        assertEquals("hello ", session.apply(Action.Pick(1)).commit)
        session.type("he")
        assertEquals("help ", session.apply(Action.Select(2)).commit)
    }

    @Test
    fun punctuationAfterTheSpaceOfAWordTakesItsPlace() {
        val session = session()
        session.type("hello")
        session.apply(Action.Key(' '))
        val stop = session.apply(Action.Key('.'))
        assertEquals(". ", stop.commit)
        assertEquals(1, stop.deleteBefore)
        // a space the user typed after that is theirs
        assertFalse(session.apply(Action.Key(' ')).handled)
    }

    @Test
    fun spaceTwiceAfterAWordIsAFullStop() {
        val session = session()
        session.type("hello")
        session.apply(Action.Key(' '))
        val stop = session.apply(Action.Key(' '))
        assertEquals(". ", stop.commit)
        assertEquals(1, stop.deleteBefore)
        // not again: the space is after a full stop
        assertFalse(session.apply(Action.Key(' ')).handled)
    }

    @Test
    fun theCursorMovedOrABackspaceKeepsTheSpaceAsItIs() {
        val moved = session()
        moved.type("hello")
        moved.apply(Action.Key(' '))
        moved.apply(Action.Context("hello "))
        assertFalse(moved.apply(Action.Key('.')).handled)
        val deleted = session()
        deleted.type("hello")
        deleted.apply(Action.Key(' '))
        assertFalse(deleted.apply(Action.Backspace).handled)
        assertFalse(deleted.apply(Action.Key(',')).handled)
    }

    @Test
    fun aLoneIIsUpperCase() {
        val session = session()
        session.type("i")
        assertEquals("I ", session.apply(Action.Key(' ')).commit)
        session.type("i'm")
        assertEquals("I'm ", session.apply(Action.Key(' ')).commit)
        assertEquals(listOf("I", "I'm"), session().type("i").candidates.take(2))
        // in a word it is as typed
        assertEquals("it", session().type("it").candidates[0])
    }

    @Test
    fun otherKeysEndTheWordAsTypedAndAreTheApps() {
        val session = session()
        session.type("hel")
        val digit = session.apply(Action.Key('1'))
        assertEquals("hel", digit.commit)
        assertFalse(digit.handled)
        // an apostrophe or hyphen only within a word
        assertFalse(session.reads('\''))
        session.type("i")
        assertTrue(session.reads('\''))
        // enter: the word as typed, and the key the app's all the same (a search runs)
        val enter = session.apply(Action.CommitRaw)
        assertEquals("I", enter.commit)
        assertFalse(enter.handled)
        assertFalse(session.apply(Action.CommitRaw).handled)
    }

    @Test
    fun backspaceDeletesALetterThenIsTheApps() {
        val session = session()
        session.type("he")
        assertEquals(listOf("h"), session.apply(Action.Backspace).candidates.take(1))
        assertEquals("", session.apply(Action.Backspace).preedit)
        assertFalse(session.apply(Action.Backspace).handled)
    }

    @Test
    fun aTapInTheWordPutsTheCursorWhereLettersGoAndBackspaceDeletes() {
        val session = session()
        assertEquals(-1, session.type("helo").cursor)
        val moved = session.apply(Action.Cursor(3))
        assertEquals(3, moved.cursor)
        val typed = session.apply(Action.Key('l'))
        assertEquals("hello", typed.preedit)
        // after the letter, short of the end
        assertEquals(4, typed.cursor)
        session.apply(Action.Cursor(2))
        assertEquals("hllo", session.apply(Action.Backspace).preedit)
        // at the start, backspace deletes nothing
        session.apply(Action.Cursor(0))
        assertEquals("hllo", session.apply(Action.Backspace).preedit)
        assertEquals("hllo ", session.apply(Action.Key(' ')).commit)
        // nothing typed: the app's
        assertFalse(session.apply(Action.Cursor(0)).handled)
    }

    @Test
    fun withNoWordsTheWordTypedAlone() {
        assertEquals(listOf("hel"), EnglishSession(null).type("hel").candidates)
    }

    @Test
    fun pagesOfCandidates() {
        val many = EnglishWords.of((1..20).associate { "a$it" to it })
        val session = EnglishSession(many, pageSize = 4)
        val first = session.type("a")
        assertEquals(4, first.candidates.size)
        assertTrue(first.hasNextPage)
        val next = session.apply(Action.NextPage)
        assertEquals(1, next.page)
        assertEquals(4, next.first)
        assertEquals("a20 ", session.apply(Action.Pick(1)).commit)
    }

    // the host hands it space and digits: they pick nothing here
    @Test
    fun theKeyboardHandsEnglishEveryKey() {
        val keyboard = Keyboard(session(), nineKeys = false, english = true)
        "hel".forEach { keyboard.onEvent(EngineEvent.CHAR, it.code) }
        assertEquals("hel ", keyboard.onEvent(EngineEvent.CHAR, ' '.code).commit)
        "he".forEach { keyboard.onEvent(EngineEvent.CHAR, it.code) }
        val digit = keyboard.onEvent(EngineEvent.CHAR, '2'.code)
        assertEquals("he", digit.commit)
        assertFalse(digit.handled)
    }

    // an arrow while a word is typed: the word as typed, and the key the app's
    @Test
    fun anArrowCommitsTheWordAndIsTheApps() {
        val keyboard = Keyboard(session(), nineKeys = false, english = true)
        "hel".forEach { keyboard.onEvent(EngineEvent.CHAR, it.code) }
        val arrow = keyboard.onEvent(EngineEvent.OTHER, 0)
        assertEquals("hel", arrow.commit)
        assertFalse(arrow.handled)
        // a tap with nothing typed is the app's
        assertFalse(keyboard.onEvent(EngineEvent.CURSOR, 0).handled)
    }

    // the space after a word is no longer before the cursor: punctuation goes after what is there
    @Test
    fun aKeyThatIsTheAppsForgetsTheSpaceAfterAWord() {
        for (event in listOf(EngineEvent.ENTER, EngineEvent.OTHER, EngineEvent.ESCAPE)) {
            val keyboard = Keyboard(session(), nineKeys = false, english = true)
            "hello ".forEach { keyboard.onEvent(EngineEvent.CHAR, it.code) }
            assertFalse(keyboard.onEvent(event, 0).handled)
            val stop = keyboard.onEvent(EngineEvent.CHAR, '.'.code)
            assertEquals("$event", 0, stop.deleteBefore)
            assertFalse(stop.handled)
        }
    }
}
