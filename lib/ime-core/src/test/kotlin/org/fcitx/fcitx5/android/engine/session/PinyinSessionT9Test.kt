/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.session

import org.fcitx.fcitx5.android.engine.data.PinyinData
import org.fcitx.fcitx5.android.engine.data.PinyinDataBuilder
import org.fcitx.fcitx5.android.engine.host.EngineEvent
import org.fcitx.fcitx5.android.engine.host.Keyboard
import org.fcitx.fcitx5.android.engine.phrase.CustomPhrases
import org.fcitx.fcitx5.android.engine.phrase.PhraseBook
import org.fcitx.fcitx5.android.engine.pinyin.Fuzzy
import org.fcitx.fcitx5.android.engine.pinyin.NineKeyLetters
import org.fcitx.fcitx5.android.engine.pinyin.PinyinSegmenter
import org.fcitx.fcitx5.android.engine.pinyin.T9Segmenter
import org.fcitx.fcitx5.android.engine.rerank.SentenceRefiner
import org.fcitx.fcitx5.android.engine.session.Action.Backspace
import org.fcitx.fcitx5.android.engine.session.Action.Key
import org.fcitx.fcitx5.android.engine.user.UserModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class PinyinSessionT9Test {

    private val data = PinyinData.load(
        ByteBuffer.wrap(
            PinyinDataBuilder()
                .unigram("<unk>", -7f, 0f)
                .unigram("你", -2f, 0f)
                .unigram("米", -3f, 0f)
                .unigram("哦", -4f, 0f)
                .unigram("好", -2.5f, 0f)
                .unigram("高", -3f, 0f)
                .unigram("你好", -2.5f, 0f)
                .unigram("米糕", -5f, 0f)
                .entry("你", syl("ni"))
                .entry("米", syl("mi"))
                .entry("哦", syl("o"))
                .entry("好", syl("hao"))
                .entry("高", syl("gao"))
                .entry("你好", syl("ni", "hao"))
                .entry("米糕", syl("mi", "gao"))
                .build().toByteArray(),
        ),
    )

    private fun session(user: UserModel? = null, refiner: SentenceRefiner? = null) =
        PinyinSession(data, T9Segmenter(abbreviations = false), spell = true, user = user, prediction = false, refiner = refiner)

    private fun Session.type(keys: String): Snapshot = keys.map { apply(Key(it)) }.last()

    @Test
    fun digitsShowAsThePinyinOfTheBestReading() {
        val s = session().type("64426")
        assertEquals("你好", s.candidates[0])
        assertEquals("ni hao", s.preedit)
        // the first digits' syllables, the best reading's first, the letters of 6 last
        assertEquals(listOf("ni", "mi", "o", "m", "n"), s.syllables)
    }

    @Test
    fun keysHandedOverTogetherReadAsTypedOneByOne() {
        // the syllables offered with their id: a pick from the list shown names it
        assertEquals(session().type("64426"), session().typeAll("64426"))
    }

    @Test
    fun aSyllableTakenNarrowsTheReadingsAndBackspaceGivesItBack() {
        val session = session()
        session.type("64426")
        val taken = session.apply(Action.Syllable(1))
        assertEquals("米糕", taken.candidates[0])
        // a syllable taken stands apart
        assertEquals("mi'gao", taken.preedit)
        // offered next: the digits after it
        assertTrue("gao" in taken.syllables && "hao" in taken.syllables)
        val back = session.apply(Backspace)
        assertEquals("你好", back.candidates[0])
        assertEquals(listOf("ni", "mi", "o", "m", "n"), back.syllables)
        // then a digit goes
        assertEquals("ni ha", session.apply(Backspace).preedit)
    }

    @Test
    fun twoTakenThenBackspaceGivesBackTheLast() {
        val session = session()
        session.type("64426")
        val first = session.apply(Action.Syllable(0))
        val both = session.apply(Action.Syllable(first.syllables.indexOf("hao")))
        assertEquals("你好", both.candidates[0])
        assertEquals(emptyList<String>(), both.syllables)
        val back = session.apply(Backspace)
        assertEquals("ni'hao", back.preedit)
        assertTrue("hao" in back.syllables)
    }

    @Test
    fun aKeyPressedAgainIsTheLetterItTypesThere() {
        val session = session()
        session.type("6")
        // 6 four times fast: m, in place of the 6
        val m = session.apply(Action.Letter('m', again = true))
        assertEquals(listOf("m"), m.syllables.filter { it.length == 1 })
        val s = session.type("4426")
        assertEquals("米糕", s.candidates[0])
        assertEquals("mi gao", s.preedit)
        assertTrue(s.syllables.none { it == "ni" })
    }

    @Test
    fun aLetterChosenGoesAfterTheLastAndAgainAfterAnotherKeyIsTheKey() {
        val session = session()
        session.type("6")
        // 4 pressed again after 6, which the keyboard does not send: the key 4, any of g h i
        val again = session.apply(Action.Letter('h', again = true))
        assertEquals(session().type("64").preedit, again.preedit)
        session.apply(Backspace)
        // a letter chosen from the long press, after the last key though of the same
        val h = session.apply(Action.Letter('h'))
        assertTrue(h.preedit.endsWith("h"))
        assertEquals(h.preedit.lowercase(), h.preedit)
        session.apply(Action.Letter('i'))
        assertEquals(h.preedit, session.apply(Backspace).preedit)
    }

    @Test
    fun aKeyPressedAgainWithNoKeyBeforeItIsTheKeyAnyOfItsLetters() {
        // a candidate picked between the presses, on the bar: nothing typed is left
        val again = session().apply(Action.Letter('h', again = true))
        val four = session().type("4")
        assertEquals(four.preedit, again.preedit)
        assertEquals(four.syllables, again.syllables)
    }

    @Test
    fun aLetterTypedAsItselfShowsAsTheLetterInTheCandidatesToo() {
        val session = session()
        session.type("8")
        val u = session.apply(Action.Letter('u', again = true))
        assertTrue(u.candidates.none { it.any(Char::isUpperCase) })
        assertTrue(u.preedit.none(Char::isUpperCase))
    }

    @Test
    fun noPhraseIsPinnedToAKeyWithALetterTypedAsItself() {
        val book = PhraseBook(CustomPhrases.parse("")) {}
        // m i, each typed as itself: letters, as a phrase key is, but no other input method's
        val session = PinyinSession(data, T9Segmenter(abbreviations = false), spell = true, prediction = false, phraseBook = book)
        session.apply(Action.Letter('m'))
        val mi = session.apply(Action.Letter('i'))
        assertEquals("米", mi.candidates[0])
        assertTrue(Offer.PIN !in session.offers(0))
        // full pinyin's mi may be
        val pinyin = PinyinSession(data, PinyinSegmenter(), prediction = false, phraseBook = book)
        pinyin.apply(Key('m'))
        pinyin.apply(Key('i'))
        assertTrue(Offer.PIN in pinyin.offers(0))
    }

    @Test
    fun aSyllableTakenOverALetterTypedAsItselfGivesItBack() {
        val session = session()
        session.type("6")
        session.apply(Action.Letter('m', again = true))
        session.type("4")
        val taken = session.apply(Action.Syllable(0))
        assertEquals("mi", taken.preedit)
        assertTrue(taken.syllables.isEmpty())
        // given back as typed: still m, not any of 6
        val back = session.apply(Backspace)
        assertEquals("mi", back.syllables.first())
        assertTrue("ni" !in back.syllables)
    }

    @Test
    fun theKeyboardSendsALetterTypedAsItselfPastThePlane() {
        val keyboard = Keyboard(session(), nineKeys = true)
        keyboard.onEvent(EngineEvent.CHAR, '6'.code)
        val m = keyboard.onEvent(EngineEvent.CHAR, Keyboard.letterKey('m', again = true))
        assertTrue(m.handled)
        assertTrue(m.syllables.none { it == "n" })
        assertTrue(Keyboard.letterKey('z', again = true) in Keyboard.LETTERS && Keyboard.letterKey('a', again = false) == Keyboard.LETTERS.first)
        // a keyboard of letters has no use for them
        val letters = Keyboard(session(), nineKeys = false)
        assertFalse(letters.onEvent(EngineEvent.CHAR, Keyboard.letterKey('m', again = false)).handled)
    }

    @Test
    fun theKeyboardSendsASyllableAsACharacterOfThePrivateUseArea() {
        val keyboard = Keyboard(session(), nineKeys = true)
        val typed = "64426".map { keyboard.onEvent(EngineEvent.CHAR, it.code) }.last()
        val s = keyboard.onEvent(EngineEvent.CHAR, Keyboard.syllableKey(1, typed.syllablesId).code)
        assertEquals("米糕", s.candidates[0])
        // 0 is no key on the nine keys: it ends the input as typed
        val zero = keyboard.onEvent(EngineEvent.CHAR, '0'.code)
        assertEquals("米糕", zero.commit)
    }

    @Test
    fun aLetterTakenIsAnInitial() {
        val session = session()
        val s = session.type("64426")
        // m for the 6 alone: 米 its likeliest character, the rest read on
        val m = session.apply(Action.Syllable(s.syllables.indexOf("m")))
        assertTrue(m.preedit.startsWith("m'"))
        assertTrue(m.candidates[0].startsWith("米"))
    }

    @Test
    fun oneSeparatesWhileTypingAndIsTheAppsOtherwise() {
        val keyboard = Keyboard(session(), nineKeys = true)
        assertEquals(false, keyboard.onEvent(EngineEvent.CHAR, '1'.code).handled)
        "64".forEach { keyboard.onEvent(EngineEvent.CHAR, it.code) }
        val separated = keyboard.onEvent(EngineEvent.CHAR, '1'.code)
        assertEquals(true, separated.handled)
        "42".forEach { keyboard.onEvent(EngineEvent.CHAR, it.code) }
        assertEquals("ni'hao", keyboard.onEvent(EngineEvent.CHAR, '6'.code).preedit)
    }

    @Test
    fun enterCommitsThePinyinShownNotTheDigits() {
        val session = session()
        session.type("64426")
        assertEquals("nihao", session.apply(Action.CommitRaw).commit)
        session.type("64426")
        session.apply(Action.Syllable(1))
        assertEquals("migao", session.apply(Action.CommitRaw).commit)
        // what no reading took is kept: digits, as typed
        session.type("64")
        session.apply(Action.Syllable(0))
        val shown = session.type("9999").preedit
        val commit = session.apply(Action.CommitRaw).commit
        assertEquals(shown.filter { it != ' ' && it != '\'' }, commit)
        assertEquals("ni9999", commit)
    }

    @Test
    fun aFuzzyReadingShowsAndCommitsItsWholeSyllable() {
        val zhong = PinyinData.load(
            ByteBuffer.wrap(
                PinyinDataBuilder()
                    .unigram("<unk>", -7f, 0f)
                    .unigram("中", -3.5f, 0f)
                    .entry("中", syl("zhong"))
                    .build().toByteArray(),
            ),
        )
        val session = PinyinSession(zhong, T9Segmenter(setOf(Fuzzy.Z_ZH), abbreviations = false), spell = true, prediction = false)
        // 9664 types zong, and z stands for zh: zhong, not its first four letters
        val s = session.type("9664")
        assertEquals("中", s.candidates[0])
        assertEquals("zhong", s.preedit)
        assertEquals("zhong", session.apply(Action.CommitRaw).commit)
    }

    @Test
    fun startingOverWithNothingTypedIsNotTheApps() {
        val keyboard = Keyboard(session(), nineKeys = true)
        assertEquals(true, keyboard.onEvent(EngineEvent.ESCAPE, 0).handled)
        // nor is a syllable for a session without the nine keys
        val full = Keyboard(PinyinSession(data, PinyinSegmenter(), prediction = false), nineKeys = false)
        assertEquals(false, full.onEvent(EngineEvent.CHAR, Keyboard.syllableKey(0, 0).code).handled)
        assertEquals(false, full.onEvent(EngineEvent.ESCAPE, 0).handled)
    }

    @Test
    fun aSyllableTakenAfterASeparatorTypedKeepsItAndDigitsTypedAfterAreDeletedFirst() {
        val session = session()
        session.type("641")
        session.apply(Action.Syllable(0))
        session.type("426")
        repeat(2) { session.apply(Backspace) }
        assertEquals("ni", session.apply(Backspace).preedit)
        // all typed after it gone: backspace gives the syllable back, the separator typed stays
        val back = session.apply(Backspace)
        assertEquals(listOf("ni", "mi", "o", "m", "n"), back.syllables)
        assertEquals("ni", back.preedit)
    }

    @Test
    fun aPickFromAListAKeyReplacedIsDropped() {
        val session = session()
        val shown = session.type("6")
        assertEquals("o", shown.syllables[0])
        // 4 typed before the pick reached the engine: its list is another
        val typed = session.type("4")
        assertTrue(typed.syllablesId != shown.syllablesId)
        val stale = session.apply(Action.Syllable(0, shown.syllablesId))
        assertEquals("ni", stale.preedit)
        assertEquals(typed.syllables, stale.syllables)
        // picked from the list shown now, taken
        assertEquals("ni", session.apply(Action.Syllable(0, typed.syllablesId)).preedit)
        assertEquals(emptyList<String>(), session.apply(Action.Syllable(0, typed.syllablesId)).syllables)
        // the same list again keeps its id: a word forgotten reads the input again, offering what
        // it did, and stales no pick (a pause reads nothing again: see the test after this one)
        val again = session(UserModel(data.dictionary, data.vocabulary)) { _, _, _, _ -> 0 }
        val first = again.type("64")
        val forgotten = again.apply(Action.Forget(0))
        // the input was read again: the refiner has its readings anew
        assertTrue(forgotten.refines)
        assertEquals(first.syllables, forgotten.syllables)
        assertEquals(first.syllablesId, forgotten.syllablesId)
    }

    @Test
    fun aPauseThatPutsAnotherReadingFirstKeepsTheSyllablesOffered() {
        val session = session { _, readings, _, _ -> readings.indexOfFirst { it.startsWith("米") } }
        val typed = session.type("64426")
        assertEquals("ni", typed.syllables[0])
        assertTrue(typed.refines)
        val refined = session.apply(Action.Refine)
        assertTrue(refined.candidates[0].startsWith("米"))
        // as shown when the user paused: a tap on it, on its way, is not dropped
        assertEquals(typed.syllables, refined.syllables)
        assertEquals(typed.syllablesId, refined.syllablesId)
        assertEquals("ni'hao", session.apply(Action.Syllable(0, typed.syllablesId)).preedit)
    }

    @Test
    fun theKeysTypeTheLettersTheUserPutOnThem() {
        // h moved to 6 beside m n o, i on 5 too
        val keys = NineKeyLetters.of(listOf("abc", "def", "gi", "jkli", "mnoh", "pqrs", "tuv", "wxyz"))
        fun session() = PinyinSession(data, T9Segmenter(abbreviations = false, letters = keys), spell = true, prediction = false)
        val s = session().type("65626")
        assertEquals("你好", s.candidates[0])
        // 6's letters offered as initials, h with them
        assertTrue(s.syllables.containsAll(listOf("m", "n", "h")))
        // pressed again soon, a letter of the same key goes in place of the last
        val session = session()
        session.apply(Key('6'))
        val h = session.apply(Action.Letter('h', again = true))
        assertEquals("h", h.preedit)
    }

    @Test
    fun aLetterOnTwoKeysPressedAgainWithNoKeyBeforeGoesInAsItself() {
        // a on 2 and on 5: which was pressed is not told, so not 2's letters for it
        val keys = NineKeyLetters.of(listOf("abc", "def", "ghi", "jkla"))
        val session = PinyinSession(data, T9Segmenter(abbreviations = false, letters = keys), spell = true, prediction = false)
        assertEquals("a", session.apply(Action.Letter('a', again = true)).preedit)
        // on one key: that key, any of its letters
        val one = PinyinSession(data, T9Segmenter(abbreviations = false, letters = keys), spell = true, prediction = false)
        one.apply(Action.Letter('h', again = true))
        assertTrue(one.apply(Key('4')).candidates.isNotEmpty())
    }
}
