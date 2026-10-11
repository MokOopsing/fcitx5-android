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

    private val data = EnglishDecoderTest.data()

    private fun session() = EnglishSession(data)

    private fun EnglishSession.type(text: String): Snapshot = text.map { apply(Action.Key(it)) }.last()

    @Test
    fun theWordTypedComesFirstThenWhatItMayBe() {
        val s = session().type("hel")
        assertEquals("hel", s.preedit)
        // the longer words it starts, likeliest first, then those a slip from it
        assertEquals(listOf("hel", "hello", "help", "hell", "he"), s.candidates)
        assertFalse(s.predicting)
    }

    @Test
    fun aWordTypedThatIsNoneIsCorrectedBySpace() {
        val teh = session()
        assertEquals(listOf("the", "teh"), teh.type("teh").candidates.take(2))
        assertEquals("the ", teh.apply(Action.Key(' ')).commit)
        // written otherwise: an apostrophe, a capital
        for ((typed, meant) in listOf("dont" to "don't", "london" to "London", "im" to "I'm")) {
            val session = session()
            session.type(typed)
            assertEquals("$meant ", session.apply(Action.Key(' ')).commit)
        }
        // punctuation commits the correction too
        val comma = session().also { it.type("teh") }.apply(Action.Key(','))
        assertEquals("the", comma.commit)
        assertFalse(comma.handled)
    }

    @Test
    fun aWordOrTheStartOfOneIsNotCorrected() {
        for (typed in listOf("us", "form", "hel", "he")) {
            val session = session()
            session.type(typed)
            assertEquals("$typed ", session.apply(Action.Key(' ')).commit)
        }
    }

    private fun committed(keys: String, before: String = "I met "): String {
        val session = session()
        session.apply(Action.Context(before))
        session.type(keys)
        return session.apply(Action.Key(' ')).commit.trimEnd()
    }

    @Test
    fun aRareWordIsCorrectedOnlyToOneFarLikelier() {
        // of the many words the pages have, nad is a slip of and
        assertEquals("and", committed("nad"))
        // a word as likely is the user's: no capital more or less, no longer word
        assertEquals("us", committed("us"))
        assertEquals("form", committed("form"))
    }

    @Test
    fun anApostropheTypedIsNeverTakenAway() {
        assertEquals("year's", committed("year's"))
        assertEquals("it's", committed("it's"))
        // nor moved: what follows it is the user's
        assertEquals("its'", committed("its'"))
    }

    @Test
    fun aDigitOrAHyphenLeavesTheWordAsTyped() {
        val digit = session().also { it.type("teh") }.apply(Action.Key('1'))
        assertEquals("teh", digit.commit)
        assertFalse(digit.handled)
        // each part a word of its own, which the words have none of
        assertEquals("teh-cut", committed("teh-cut"))
    }

    // where nothing is learned nothing is known of the text: nothing follows, nothing is a sentence's start
    @Test
    fun withoutLearningNothingIsOffered() {
        val session = session().apply { learning = false }
        session.apply(Action.Context(""))
        session.type("i")
        assertFalse(session.apply(Action.Key(' ')).predicting)
        // turned off, what was known goes
        val turned = session()
        turned.apply(Action.Context("I fed the "))
        turned.learning = false
        assertEquals("cut", turned.type("cit").candidates.first())
    }

    // the user's own capital: a name, likelier than a slip, but for a key beside
    @Test
    fun aWordWithACapitalOfItsOwnIsCorrectedOnlyForAKeyBeside() {
        assertEquals("Steve", committed("Stebe"))
        assertEquals("Stve", committed("Stve"))
        // at a sentence's start the capital is the keyboard's
        assertEquals("Steve", committed("Stve", before = ""))
    }

    // a letter short and a key beside: the word, though the keys start it too
    @Test
    fun aWordALetterShortWithASlipIsCorrected() {
        assertEquals("hello", committed("hwll"))
        assertEquals("hwl", committed("hwl"))
    }

    @Test
    fun backspaceTakesTheCorrectionBackForGood() {
        val session = session()
        session.type("teh")
        session.apply(Action.Key(' '))
        val back = session.apply(Action.Backspace)
        assertEquals(4, back.deleteBefore)
        assertEquals("teh", back.preedit)
        assertEquals("teh ", session.apply(Action.Key(' ')).commit)
        assertEquals("teh", session.type("teh").candidates.first())
        // only right after: a backspace after more is the app's
        val later = session()
        later.type("teh")
        later.apply(Action.Key(' '))
        later.apply(Action.Key(','))
        assertFalse(later.apply(Action.Backspace).handled)
    }

    @Test
    fun theWordsBeforeTheCursorWeighWhatIsTyped() {
        assertEquals("cut", session().type("cit").candidates.first())
        val after = session()
        after.apply(Action.Context("I fed the "))
        assertEquals("cat", after.type("cit").candidates.first())
        // committed here too
        val typed = session()
        typed.type("the")
        typed.apply(Action.Key(' '))
        assertEquals("cat", typed.type("cit").candidates.first())
        // nothing kept where nothing is learned
        val private = session().apply { learning = false }
        private.apply(Action.Context("I fed the "))
        assertEquals("cut", private.type("cit").candidates.first())
    }

    @Test
    fun aWordCommittedIsFollowedByWhatMayComeNext() {
        val session = session()
        session.apply(Action.Context(""))
        session.type("i")
        val next = session.apply(Action.Key(' '))
        assertTrue(next.predicting)
        assertEquals(listOf("don't", "I'm"), next.candidates)
        assertEquals("don't ", session.apply(Action.Pick(0)).commit)
        // a letter starts a word in their place
        val typed = session.type("h")
        assertFalse(typed.predicting)
        assertEquals("h", typed.candidates.first())
        // a sentence's first, capitalised: after the full stop two spaces make
        val start = session()
        start.type("hello")
        start.apply(Action.Key(' '))
        assertEquals(listOf("I", "The"), start.apply(Action.Key(' ')).candidates)
    }

    @Test
    fun suggestionsTakeTheCaseOfWhatIsTyped() {
        assertEquals(listOf("Hel", "Hello", "Help"), session().type("Hel").candidates.take(3))
        assertEquals(listOf("HEL", "HELLO", "HELP"), session().type("HEL").candidates.take(3))
        // one capital letter is a word's start, whatever follows it
        assertTrue("I'm" in session().type("I'").candidates)
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
        val he = session.type("he").candidates
        assertEquals("help ", session.apply(Action.Select(he.indexOf("help"))).commit)
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
        // no word: corrected
        assertEquals("hello ", session.apply(Action.Key(' ')).commit)
        // nothing typed: the app's
        assertFalse(session.apply(Action.Cursor(0)).handled)
    }

    @Test
    fun withNoWordsTheWordTypedAlone() {
        assertEquals(listOf("hel"), EnglishSession(null).type("hel").candidates)
    }

    @Test
    fun pagesOfCandidates() {
        val session = EnglishSession(data, pageSize = 2)
        val first = session.type("hel")
        assertEquals(listOf("hel", "hello"), first.candidates)
        assertTrue(first.hasNextPage)
        val next = session.apply(Action.NextPage)
        assertEquals(1, next.page)
        assertEquals(2, next.first)
        assertEquals(listOf("help", "hell"), next.candidates)
        assertEquals("hell ", session.apply(Action.Pick(3)).commit)
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
