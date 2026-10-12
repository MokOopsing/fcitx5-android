/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.fcitx.fcitx5.android.core.CapabilityFlag
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.core.Fcitx
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.InputMethodNames
import org.fcitx.fcitx5.android.core.KeyState
import org.fcitx.fcitx5.android.core.RawConfig
import org.fcitx.fcitx5.android.data.punctuation.PunctuationManager
import org.fcitx.fcitx5.android.data.punctuation.PunctuationMapEntry
import org.fcitx.fcitx5.android.engine.host.EngineSettings
import org.fcitx.fcitx5.android.engine.host.Engines
import org.fcitx.fcitx5.android.engine.host.Keyboard
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import timber.log.Timber
import java.io.File

class FcitxTest {

    /**
     * Without this a broken engine interaction hangs the whole run instead of failing it --
     * which is exactly what happened before `activate`/`focus` were added to [setup].
     */
    @get:Rule
    val timeout: Timeout = Timeout.seconds(30)

    private companion object {

        lateinit var fcitx: Fcitx
        /**
         * Buffered, not CONFLATED. A conflated channel keeps only the newest event, so a
         * `CommitStringEvent` is overwritten by the `CandidateListEvent` fcitx emits right
         * after a commit -- the test then waits forever for an event that was dropped.
         */
        val fcitxEventChannel = Channel<FcitxEvent<*>>(capacity = Channel.UNLIMITED)

        /**
         * The input panel as of the newest event delivered here. `Fcitx.inputPanelCached` is
         * written on the fcitx thread without synchronisation, so reading it from the test
         * thread is a data race; this is written on the collector and read through a volatile.
         */
        @Volatile
        var latestInputPanel = FcitxEvent.InputPanelEvent.Data()

        /** The page of candidates as of the newest event, kept as [latestInputPanel] is. */
        @Volatile
        var latestPaged = FcitxEvent.PagedCandidateEvent.Data.Empty

        /**
         * Off the main thread: eventFlow drops its oldest event past 15 buffered, so a collector
         * held up by a main-thread stall could lose a `CommitStringEvent` before it reaches the channel.
         */
        val scope = CoroutineScope(Dispatchers.Default)

        /** What [setUpEngine] saves over in the app's data, put back by [cleanup]. */
        var enabledImeBefore: Array<String>? = null
        var globalConfigBefore: RawConfig? = null
        var predictionBefore: String? = null

        @BeforeClass
        @JvmStatic
        fun setup() {
            checkThisInputMethodIsNotBound()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            fcitx = Fcitx(context)

            // Forward to our channel for point to point consuming. UNDISPATCHED subscribes right
            // here, before `start()`: eventFlow keeps no replay, so a ReadyEvent emitted before a
            // dispatched collector got round to subscribing would be lost and setup would hang.
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                fcitx.eventFlow.collect {
                    if (it is FcitxEvent.InputPanelEvent) latestInputPanel = it.data
                    if (it is FcitxEvent.PagedCandidateEvent) latestPaged = it.data
                    fcitxEventChannel.send(it)
                }
            }
            fcitx.start()

            // The Timeout rule covers tests, not @BeforeClass; bound this separately so a
            // broken engine fails the run instead of hanging it.
            runBlocking {
                withTimeout(60_000) { setUpEngine() }
            }
        }

        private suspend fun setUpEngine() {
            receiveFirst<FcitxEvent.ReadyEvent>()
            // Without an input context fcitx has nowhere to route keys: `sendKey` is
            // accepted but produces no preedit and no candidates, so every test that waits
            // for an event hangs. FcitxInputMethodService does this from `onBindInput` /
            // `onStartInputView`; a bare engine has to do it itself.
            fcitx.activate(TEST_UID, TEST_PKG_NAME)
            fcitx.focus(true)
            // both are saved to the app's data at once
            enabledImeBefore = fcitx.enabledIme().map { it.uniqueName }.toTypedArray()
            globalConfigBefore = fcitx.getGlobalConfig()["cfg"]
            // what the tests commit is not learned into the app's data: as in a field that asks
            // for no personalized learning (CapabilityFlags.fromEditorInfo)
            fcitx.setCapFlags(CapabilityFlags(CapabilityFlag.Sensitive))
            fcitx.setEnabledIme(arrayOf(Engines.PINYIN))
            // prediction on, whatever the app's data says: a commit then followed by none says learning is off
            predictionBefore = fcitx.getImConfig(Engines.PINYIN)["cfg"][EngineSettings.PREDICTION].value
            fcitx.setImConfig(Engines.PINYIN, RawConfig(arrayOf(RawConfig(EngineSettings.PREDICTION, true))))
            fcitx.setGlobalConfig(
                RawConfig(
                    arrayOf(
                        RawConfig(
                            "Behavior", arrayOf(
                                RawConfig("ShowInputMethodInformation", false)
                            )
                        )
                    )
                )
            )
        }

        @AfterClass
        @JvmStatic
        fun cleanup() {
            // setup failed before it made one
            if (!::fcitx.isInitialized) return
            try {
                runBlocking {
                    predictionBefore?.let { fcitx.setImConfig(Engines.PINYIN, RawConfig(arrayOf(RawConfig(EngineSettings.PREDICTION, it)))) }
                    enabledImeBefore?.let { fcitx.setEnabledIme(it) }
                    globalConfigBefore?.let { fcitx.setGlobalConfig(it) }
                    fcitx.focus(false)
                    fcitx.deactivate(TEST_UID)
                }
            } finally {
                fcitx.stop()
                scope.cancel()
            }
        }

        /**
         * How long the event channel must stay empty to count as drained. Events reach it through
         * the collector, which a GC or class loading on a loaded emulator can stall for a few
         * hundred ms; this leaves headroom over that.
         */
        const val QUIET_MS = 500L

        /** Any uid works; fcitx only uses it to key its input context table. */
        const val TEST_UID = 0
        const val TEST_PKG_NAME = "org.fcitx.fcitx5.android.test"

        /** 五笔, the engine's own table (a key of [Engines.TABLES]). */
        const val WUBI = "engine-wubi"

        private suspend fun sendString(str: String) {
            str.forEach { c ->
                fcitx.sendKey(c)
                delay(50)
            }
        }

        private suspend inline fun <reified T : FcitxEvent<*>> receiveFirst(): T? =
            fcitxEventChannel.receiveAsFlow().mapNotNull { it as? T }.firstOrNull()

        private suspend fun receiveFirstCommitString() =
            receiveFirst<FcitxEvent.CommitStringEvent>()

        private suspend fun receiveFirstPreedit() = receiveFirst<FcitxEvent.ClientPreeditEvent>()

        /**
         * Waits for the input panel to show [expected] as its preedit. The panel is updated
         * when fcitx flushes its UI, which is not necessarily done by the time `sendKey`
         * returns, so asserting straight after typing races it.
         *
         * @return the preedit last seen: [expected], unless it did not arrive in time
         */
        private suspend fun awaitPanelPreedit(expected: String, timeoutMs: Long = 5_000): String {
            val deadline = SystemClock.uptimeMillis() + timeoutMs
            while (true) {
                val preedit = latestInputPanel.preedit.toString()
                if (preedit == expected || SystemClock.uptimeMillis() >= deadline) return preedit
                delay(20)
            }
        }

        /**
         * Waits until no event has come for [QUIET_MS], dropping those that did: the engine's
         * refine slices may still reorder a list after it is first shown.
         */
        private suspend fun awaitQuiet() {
            while (withTimeoutOrNull(QUIET_MS) { fcitxEventChannel.receive() } != null) {
                // discard
            }
        }

        /** As [awaitQuiet], but the events that came, for a test to see what a key did not do. */
        private suspend fun eventsTillQuiet(): List<FcitxEvent<*>> {
            val events = ArrayList<FcitxEvent<*>>()
            while (true) events += withTimeoutOrNull(QUIET_MS) { fcitxEventChannel.receive() } ?: return events
        }

        /** Where Fcitx starts fcitx: its data/ and config/. */
        private fun fcitxHome(): File =
            FcitxApplication.getInstance().directBootAwareContext.let { it.getExternalFilesDir(null) ?: it.filesDir }
    }

    private var enabledIme: List<String> = listOf()

    /**
     * Each test must start from a clean engine AND a clean event queue.
     *
     * The channel is buffered (see [fcitxEventChannel]), so events a previous test did not
     * consume are still queued, and `receiveFirst*` returns the OLDEST match -- which shows up
     * as a test asserting on another test's input. Draining here makes the tests independent
     * of each other and of their declaration order.
     *
     * `reset()` returns before the events it causes have come through the collector, so a
     * single sweep of the channel can finish before they land. Drain until it has been quiet
     * for a while instead, then forget the last panel too.
     */
    @Before
    fun resetEngineAndDrainEvents() = runBlocking {
        fcitx.reset()
        while (withTimeoutOrNull(QUIET_MS) { fcitxEventChannel.receive() } != null) {
            // discard
        }
        latestInputPanel = FcitxEvent.InputPanelEvent.Data()
        latestPaged = FcitxEvent.PagedCandidateEvent.Data.Empty
        enabledIme = fcitx.enabledIme().map { it.uniqueName }
    }

    @After
    fun restoreEnabledIME() = runBlocking {
        fcitx.setEnabledIme(enabledIme.toTypedArray())
    }

    /**
     * `wqvb` is the table's 你好 and 您好, in the order of their use (TableText.addWords): the
     * model's, or on a device whose user picked one, theirs. So each is picked by content, and with
     * two candidates neither commits itself on the last key ([aFullCodeOfOneCandidateCommitsItself]
     * has a code of one). 你好 is typed by its characters too, 你 picked from `wq`'s candidates: what
     * this test is for is the code -> candidates -> select -> commit path.
     */
    @Test
    fun testWbx(): Unit = runBlocking {
        fcitx.setEnabledIme(arrayOf(WUBI))
        sendString("wqvb")
        awaitQuiet()
        val words = fcitx.getCandidates(0, 16).map { it.text }
        Timber.i("candidates for wubi 'wqvb' are $words")
        Assert.assertTrue("您好 and 你好 among candidates: $words", words.containsAll(listOf("您好", "你好")))
        fcitx.select(words.indexOf("您好"))
        Assert.assertEquals("您好", receiveFirstCommitString()?.data?.text)
        awaitQuiet()
        Assert.assertEquals("nothing left to pick", emptyList<String>(), fcitx.getCandidates(0, 16).map { it.text })

        val expected = "你"
        sendString("wq")
        awaitQuiet()
        val candidates = fcitx.getCandidates(0, 16).map { it.text }
        Timber.i("candidates for wubi 'wq' are $candidates")
        val index = candidates.indexOf(expected)
        Assert.assertTrue("$expected not among candidates: $candidates", index >= 0)

        fcitx.select(index)
        Assert.assertEquals(expected, receiveFirstCommitString()?.data?.text)
        fcitx.reset()
    }

    /**
     * Picks the candidate by content rather than by index.
     *
     * Asserting `select(0)` would pin the engine's ranking: "nihaoshijie" reads as 你好世界 or
     * 你好时节, and either is a legitimate reading. What this test is for is
     * the sendKey -> candidates -> select -> commit path, not the engine's scoring.
     */
    @Test
    fun testPinyin(): Unit = runBlocking {
        fcitx.setEnabledIme(arrayOf(Engines.PINYIN))
        val expected = "你好世界"
        sendString("nihaoshijie")
        awaitQuiet()

        val candidates = fcitx.getCandidates(0, 32).map { it.text }
        Timber.i("candidates are $candidates")
        val index = candidates.indexOf(expected)
        Assert.assertTrue("$expected not among candidates: $candidates", index >= 0)

        fcitx.select(index)
        val commitString = receiveFirstCommitString()?.data?.text
        Timber.i("commitString is $commitString")
        Assert.assertEquals(expected, commitString)
        fcitx.reset()
    }

    /**
     * A letter chosen from a nine-key's long press: the keyboard sends it as a character past the
     * BMP (Keyboard.letterKey), which fcitx must hand the engine whole. m then 4 is mi alone,
     * never ni.
     */
    @Test
    fun aLetterTypedAsItselfOnTheNineKeysReachesTheEngine(): Unit = runBlocking {
        fcitx.setEnabledIme(arrayOf(Engines.T9))
        fcitx.sendKey(0x01000000 + Keyboard.letterKey('m', again = false))
        sendString("4")
        awaitQuiet()
        val candidates = fcitx.getCandidates(0, 32).map { it.text }
        Timber.i("candidates for m, 4 are $candidates")
        Assert.assertTrue("米 among $candidates", "米" in candidates)
        Assert.assertFalse("no 你 among $candidates", "你" in candidates)
        fcitx.reset()
    }

    /**
     * English through the engine as the keyboard types it (virtual keys): the word typed offered
     * with what it starts, space committing it with a space, and a full stop typed next put in
     * that space's place, `hel. `; a slip corrected by space, and, nothing learned here, no words
     * offered after it; a hardware key types itself.
     */
    @Test
    fun englishPutsTheSpaceAfterAWordBehindTheFullStopTypedNext(): Unit = runBlocking {
        fcitx.setEnabledIme(arrayOf(InputMethodNames.KEYBOARD))
        fcitx.activateIme(InputMethodNames.KEYBOARD)
        awaitQuiet()
        "hel".forEach { fcitx.sendKey(it, KeyState.Virtual.state) }
        awaitQuiet()
        val candidates = fcitx.getCandidates(0, 8).map { it.text }
        Assert.assertEquals("hel", candidates.firstOrNull())
        Assert.assertTrue("help among $candidates", "help" in candidates)
        fcitx.sendKey(' ', KeyState.Virtual.state)
        val space = eventsTillQuiet()
        Assert.assertEquals("hel ", space.filterIsInstance<FcitxEvent.CommitStringEvent>().joinToString("") { it.data.text })
        fcitx.sendKey('.', KeyState.Virtual.state)
        val stop = eventsTillQuiet()
        val deleted = stop.filterIsInstance<FcitxEvent.DeleteSurroundingEvent>().map { it.data }
        Assert.assertEquals("$stop", listOf(FcitxEvent.DeleteSurroundingEvent.Data(1, 0)), deleted)
        Assert.assertEquals(". ", stop.filterIsInstance<FcitxEvent.CommitStringEvent>().joinToString("") { it.data.text })
        val started = System.nanoTime()
        "teh".forEach { fcitx.sendKey(it, KeyState.Virtual.state) }
        awaitQuiet()
        Timber.i("teh typed in ${(System.nanoTime() - started) / 1_000_000} ms, the first key building the word index")
        fcitx.sendKey(' ', KeyState.Virtual.state)
        val corrected = eventsTillQuiet()
        // the capital at a sentence's start is the keyboard's (AutoCaps), which these keys are not
        Assert.assertEquals("the ", corrected.filterIsInstance<FcitxEvent.CommitStringEvent>().joinToString("") { it.data.text })
        // the engine is Sensitive here (setUpEngine): nothing of the text kept, so nothing offered after it
        Assert.assertEquals("$corrected", 0, corrected.filterIsInstance<FcitxEvent.CandidateListEvent>().last().data.total)
        val warm = System.nanoTime()
        "becuase".forEach { fcitx.sendKey(it, KeyState.Virtual.state) }
        awaitQuiet()
        Timber.i("becuase typed in ${(System.nanoTime() - warm) / 1_000_000} ms: ${fcitx.getCandidates(0, 4).map { it.text }}")
        fcitx.sendKey(' ', KeyState.Virtual.state)
        Assert.assertEquals("because ", eventsTillQuiet().filterIsInstance<FcitxEvent.CommitStringEvent>().joinToString("") { it.data.text })
        // a hardware keyboard's key is the app's to type
        fcitx.sendKey('a')
        Assert.assertTrue(eventsTillQuiet().any { it is FcitxEvent.KeyEvent })
        Assert.assertEquals("", latestInputPanel.preedit.toString())
        fcitx.reset()
    }

    // region input method management

    @Test
    fun switchingInputMethodChangesTheCurrentOne(): Unit = runBlocking {
        fcitx.setEnabledIme(arrayOf(Engines.PINYIN, WUBI))
        fcitx.activateIme(Engines.PINYIN)
        Assert.assertEquals(Engines.PINYIN, fcitx.currentIme().uniqueName)
        fcitx.activateIme(WUBI)
        Assert.assertEquals(WUBI, fcitx.currentIme().uniqueName)
    }

    @Test
    fun enumerateImeCyclesThroughEnabledOnes(): Unit = runBlocking {
        fcitx.setEnabledIme(arrayOf(Engines.PINYIN, WUBI))
        fcitx.activateIme(Engines.PINYIN)
        fcitx.enumerateIme(forward = true)
        Assert.assertEquals(WUBI, fcitx.currentIme().uniqueName)
        fcitx.enumerateIme(forward = true)
        Assert.assertEquals("cycles back round", Engines.PINYIN, fcitx.currentIme().uniqueName)
    }

    @Test
    fun enabledImeIsWhatWasSet(): Unit = runBlocking {
        fcitx.setEnabledIme(arrayOf(Engines.PINYIN, WUBI))
        Assert.assertEquals(listOf(Engines.PINYIN, WUBI), fcitx.enabledIme().map { it.uniqueName })
    }

    @Test
    fun pinyinAndWubiAreBothAvailable(): Unit = runBlocking {
        val available = fcitx.availableIme().map { it.uniqueName }
        Assert.assertTrue("${Engines.PINYIN} missing from $available", Engines.PINYIN in available)
        Assert.assertTrue("$WUBI missing from $available", WUBI in available)
    }

    // endregion

    // region pinyin decoding

    /**
     * Waits for the finished composition rather than taking the first preedit event:
     * `sendString` produces one per keystroke, so the first would be "n".
     *
     * Uses the input panel's preedit, not [FcitxAPI.clientPreeditCached]. This test drives a
     * bare engine whose capability flags (see [setUpEngine]) leave out `Preedit`, so fcitx has
     * no reason to believe the client can render a preedit and keeps it in the input panel. The
     * real service sets the capability flags in `onStartInput`, which is what moves it client-side.
     */
    @Test
    fun pinyinSegmentsSyllablesInThePreedit(): Unit = runBlocking {
        fcitx.setEnabledIme(arrayOf(Engines.PINYIN))
        sendString("nihao")
        val preedit = awaitPanelPreedit("ni hao")
        Timber.i("input panel preedit is $preedit")
        Assert.assertEquals("ni hao", preedit)
        fcitx.reset()
    }

    /** The preedit in the input panel has its cursor at its end, as one in the app has. */
    @Test
    fun theInputPanelsPreeditHasItsCursorAtItsEnd(): Unit = runBlocking {
        fcitx.setEnabledIme(arrayOf(Engines.PINYIN))
        sendString("nihao")
        Assert.assertEquals("ni hao", awaitPanelPreedit("ni hao"))
        Assert.assertEquals("ni hao".length, latestInputPanel.preedit.cursor)
        fcitx.reset()
    }

    @Test
    fun aSingleSyllableOffersItsCommonCharacters(): Unit = runBlocking {
        fcitx.setEnabledIme(arrayOf(Engines.PINYIN))
        sendString("ni")
        val candidates = fcitx.getCandidates(0, 16).map { it.text }
        Timber.i("candidates for 'ni' are $candidates")
        Assert.assertTrue("你 missing from $candidates", "你" in candidates)
        fcitx.reset()
    }

    @Test
    fun candidatesArePagedByOffsetAndLimit(): Unit = runBlocking {
        fcitx.setEnabledIme(arrayOf(Engines.PINYIN))
        sendString("ni")
        awaitQuiet()
        val firstFour = fcitx.getCandidates(0, 4).map { it.text }
        val nextFour = fcitx.getCandidates(4, 4).map { it.text }
        Assert.assertEquals(4, firstFour.size)
        Assert.assertEquals(4, nextFour.size)
        Assert.assertTrue("pages overlap: $firstFour vs $nextFour",
            firstFour.intersect(nextFour.toSet()).isEmpty())
        fcitx.reset()
    }

    @Test
    fun resetClearsPreeditAndCandidates(): Unit = runBlocking {
        fcitx.setEnabledIme(arrayOf(Engines.PINYIN))
        sendString("nihao")
        Assert.assertFalse("engine should hold a composition", fcitx.isEmpty())
        // seen first, so that an empty preedit below is the reset's doing and not a leftover
        Assert.assertEquals("ni hao", awaitPanelPreedit("ni hao"))
        fcitx.reset()
        Assert.assertTrue("reset should clear it", fcitx.isEmpty())
        Assert.assertEquals(0, fcitx.getCandidates(0, 8).size)
        Assert.assertEquals("", awaitPanelPreedit(""))
    }

    /**
     * The preedit is cleared, and the whole panel with it: where it learns the engine follows a
     * commit with predictions for the word just typed (`isEmpty()` false), prediction being on as
     * [setUpEngine] sets it, and the Sensitive flag it sets, which keeps the tests' words out of the
     * app's data, turns learning off.
     */
    @Test
    fun selectingACandidateCommitsItAndClearsThePreedit(): Unit = runBlocking {
        fcitx.setEnabledIme(arrayOf(Engines.PINYIN))
        sendString("ni")
        Assert.assertEquals("ni", awaitPanelPreedit("ni"))
        awaitQuiet()
        val first = fcitx.getCandidates(0, 1).first().text
        fcitx.select(0)
        Assert.assertEquals(first, receiveFirstCommitString()?.data?.text)
        Assert.assertEquals("preedit is consumed by the commit", "", awaitPanelPreedit(""))
        Assert.assertTrue("nothing predicted: learning is off", fcitx.isEmpty())
        fcitx.reset()
    }

    /**
     * A tap carries the text it showed. A refine slice can reorder the list between the tap and
     * its pick, and the index then names another word: that pick is dropped, not committed.
     */
    @Test
    fun aPickOfTextTheCandidateNoLongerShowsIsDropped(): Unit = runBlocking {
        fcitx.setEnabledIme(arrayOf(Engines.PINYIN))
        sendString("ni")
        Assert.assertEquals("ni", awaitPanelPreedit("ni"))
        awaitQuiet()
        val shown = fcitx.getCandidates(0, 2).map { it.text }
        Assert.assertEquals("two candidates for 'ni': $shown", 2, shown.size)
        Assert.assertFalse("the second's text at the first's index", fcitx.select(0, shown[1]))
        Assert.assertEquals("nothing was picked", shown, fcitx.getCandidates(0, 2).map { it.text })
        Assert.assertTrue("its own text at its index", fcitx.select(1, shown[1]))
        Assert.assertEquals(shown[1], receiveFirstCommitString()?.data?.text)
        fcitx.reset()
    }

    /**
     * The same past U+FFFF (CJK Ext-B, in the tables): JNI's modified UTF-8 spells such a
     * character as two surrogates, which no candidate's text equals.
     */
    @Test
    fun aPickOfACharacterPastTheBmpIsCheckedByItsText(): Unit = runBlocking {
        // 五笔's fae is 霰 and U+2298F
        fcitx.setEnabledIme(arrayOf(WUBI))
        sendString("fae")
        awaitQuiet()
        val shown = fcitx.getCandidates(0, 16).map { it.text }
        val at = shown.indexOf("𢦏")
        Assert.assertTrue("U+2298F among fae's: $shown", at >= 0)
        Assert.assertFalse("another's text at its index", fcitx.select(at, shown[if (at == 0) 1 else 0]))
        Assert.assertTrue("its own text at its index", fcitx.select(at, shown[at]))
        Assert.assertEquals(shown[at], receiveFirstCommitString()?.data?.text)
        fcitx.reset()
    }

    /**
     * A page at a time, as the floating window of a physical keyboard has them: arrows only where
     * there is a page, what space commits highlighted, and a pick of a page turned from dropped.
     */
    @Test
    fun pagedCandidatesTurnAndDropAPickOfAnotherPage(): Unit = runBlocking {
        fcitx.setEnabledIme(arrayOf(Engines.PINYIN))
        fcitx.setCandidatePagingMode(1)
        try {
            sendString("ni")
            Assert.assertEquals("ni", awaitPanelPreedit("ni"))
            awaitQuiet()
            val first = latestPaged
            val firstWords = first.candidates.map { it.text }
            Assert.assertTrue("a page for 'ni': $firstWords", firstWords.size >= 2)
            Assert.assertEquals("what space commits", 0, first.cursorIndex)
            Assert.assertFalse("nothing before the first page", first.hasPrev)
            Assert.assertTrue("more than a page for 'ni'", first.hasNext)
            fcitx.offsetCandidatePage(1)
            awaitQuiet()
            val second = latestPaged
            val secondWords = second.candidates.map { it.text }
            Assert.assertTrue("a page to go back to", second.hasPrev)
            Assert.assertTrue("another page: $firstWords, $secondWords", firstWords.none { it in secondWords })
            Assert.assertEquals(0, second.cursorIndex)
            Assert.assertEquals("the list from the first of all", firstWords, fcitx.getCandidates(0, firstWords.size).map { it.text })
            Assert.assertFalse("a word of the page turned from", fcitx.select(0, firstWords[0]))
            fcitx.offsetCandidatePage(-1)
            awaitQuiet()
            Assert.assertEquals("the first page again", firstWords, latestPaged.candidates.map { it.text })
            Assert.assertTrue("its own text at its index", fcitx.select(1, firstWords[1]))
            Assert.assertEquals(firstWords[1], receiveFirstCommitString()?.data?.text)
        } finally {
            fcitx.setCandidatePagingMode(0)
            fcitx.reset()
        }
    }

    /**
     * A page turned to: the bar still shows the list from the first of all, those before the page
     * fetched as it asks for them, and a pick on the page commits that page's word, sent as its
     * index among all.
     */
    @Test
    fun aPageTurnedToKeepsTheBarWholeAndCommitsItsOwnWord(): Unit = runBlocking {
        fcitx.setEnabledIme(arrayOf(Engines.PINYIN))
        try {
            sendString("ni")
            Assert.assertEquals("ni", awaitPanelPreedit("ni"))
            awaitQuiet()
            val all = fcitx.getCandidates(0, 16).map { it.text }
            fcitx.offsetCandidatePage(1)
            val bar = receiveFirst<FcitxEvent.CandidateListEvent>()?.data?.candidates?.map { it.text }
            Assert.assertEquals("the bar from the first of all", all, bar)
            awaitQuiet()
            fcitx.setCandidatePagingMode(1)
            awaitQuiet()
            val page = latestPaged
            val words = page.candidates.map { it.text }
            Assert.assertTrue("a page past the first for 'ni': $words", page.hasPrev && words.size >= 2)
            Assert.assertTrue("its own text at its index", fcitx.select(1, words[1]))
            Assert.assertEquals(words[1], receiveFirstCommitString()?.data?.text)
        } finally {
            fcitx.setCandidatePagingMode(0)
            fcitx.reset()
        }
    }

    // endregion

    // region code table input

    @Test
    fun wubiOffersCandidatesForAPartialCode(): Unit = runBlocking {
        fcitx.setEnabledIme(arrayOf(WUBI))
        sendString("wq")
        val candidates = fcitx.getCandidates(0, 16).map { it.text }
        Timber.i("candidates for wubi 'wq' are $candidates")
        Assert.assertTrue("expected some candidates for a partial code", candidates.isNotEmpty())
        fcitx.reset()
    }

    /**
     * A full code with one candidate commits it on its last key (唯一自动上屏): the code -> commit
     * path. 五笔's aaog is 工业 alone.
     */
    @Test
    fun aFullCodeOfOneCandidateCommitsItself(): Unit = runBlocking {
        fcitx.setEnabledIme(arrayOf(WUBI))
        sendString("aaog")
        Assert.assertEquals("工业", receiveFirstCommitString()?.data?.text)
        Assert.assertEquals("", awaitPanelPreedit(""))
        fcitx.reset()
    }

    // endregion

    // region punctuation

    // fcitx's zh_CN map: . has 。 alone, [ has 【 「 『 ❲ ［ [

    @Test
    fun aPunctuationKeyWithOneMarkTypesIt(): Unit = runBlocking {
        fcitx.setEnabledIme(arrayOf(Engines.PINYIN))
        sendString(".")
        Assert.assertEquals("。", receiveFirstCommitString()?.data?.text)
        awaitQuiet()
        Assert.assertEquals("nothing to choose from", 0, fcitx.getCandidates(0, 16).size)
        fcitx.reset()
    }

    /**
     * A key with several marks offers them all, its first as typed: the next key puts that in
     * and goes on, a tap puts in another, Backspace none.
     */
    @Test
    fun aPunctuationKeyWithSeveralMarksOffersThemItsFirstGoingIn(): Unit = runBlocking {
        fcitx.setEnabledIme(arrayOf(Engines.PINYIN))
        sendString("[")
        Assert.assertEquals("【", awaitPanelPreedit("【"))
        awaitQuiet()
        Assert.assertEquals(listOf("【", "「", "『", "❲", "［", "["), fcitx.getCandidates(0, 16).map { it.text })
        sendString("1")
        Assert.assertEquals("【", receiveFirstCommitString()?.data?.text)

        sendString("[")
        awaitQuiet()
        Assert.assertTrue(fcitx.select(1, "「"))
        Assert.assertEquals("「", receiveFirstCommitString()?.data?.text)

        sendString("[")
        awaitQuiet()
        fcitx.sendKey("BackSpace")
        Assert.assertEquals("", awaitPanelPreedit(""))
        Assert.assertTrue("the key taken back", fcitx.isEmpty())

        // Space and Return pick the first and type nothing more: no space after it, no message sent
        for (key in listOf("space", "Return")) {
            sendString("[")
            awaitQuiet()
            fcitx.sendKey(key)
            Assert.assertEquals(key, "【", receiveFirstCommitString()?.data?.text)
            // the key itself not passed on to the app (a space typed, a message sent), nor anything more committed
            val after = eventsTillQuiet()
            Assert.assertTrue("$key: $after", after.none { it is FcitxEvent.KeyEvent || it is FcitxEvent.CommitStringEvent })
            Assert.assertTrue(key, fcitx.isEmpty())
        }
        fcitx.reset()
    }

    /**
     * A pair as a key's first card is one choice, the half the key types: its other half is not
     * offered beside it. The halves come in turn, fcitx's default (TypePairedPunctuationsTogether=False).
     */
    @Test
    fun aPairAsAKeysFirstCardIsOneChoice(): Unit = runBlocking {
        val shipped = PunctuationManager.load(fcitx, "zh_CN")
        val saved = File(fcitxHome(), "data/punctuation/punc.mb.zh_CN")
        val hadSaved = saved.exists()
        try {
            // " with a card after its pair, as the editor page lets one add
            PunctuationManager.save(
                fcitx, "zh_CN",
                shipped.filter { it.key != "\"" } + PunctuationMapEntry("\"", "“", "”") + PunctuationMapEntry("\"", "＂", "")
            )
            fcitx.setEnabledIme(arrayOf(Engines.PINYIN))
            sendString("\"")
            Assert.assertEquals("“", awaitPanelPreedit("“"))
            awaitQuiet()
            Assert.assertEquals(listOf("“", "＂"), fcitx.getCandidates(0, 16).map { it.text })
            Assert.assertTrue(fcitx.select(0, "“"))
            Assert.assertEquals("“", receiveFirstCommitString()?.data?.text)

            sendString("\"")
            Assert.assertEquals("the other half next", "”", awaitPanelPreedit("”"))
            awaitQuiet()
            Assert.assertEquals(listOf("”", "＂"), fcitx.getCandidates(0, 16).map { it.text })
            Assert.assertTrue(fcitx.select(1, "＂"))
            Assert.assertEquals("＂", receiveFirstCommitString()?.data?.text)
        } finally {
            fcitx.reset()
            PunctuationManager.save(fcitx, "zh_CN", shipped)
            // the save above wrote a map of the user's where there was none
            if (!hadSaved) saved.delete()
        }
    }

    /**
     * A pair's half on offer opens or closes the pair only as it goes in: after another card is
     * picked, or the key is taken back with Backspace, the key opens the pair again.
     */
    @Test
    fun aPairsHalfNotPutInLeavesThePairAsItWas(): Unit = runBlocking {
        val shipped = PunctuationManager.load(fcitx, "zh_CN")
        val saved = File(fcitxHome(), "data/punctuation/punc.mb.zh_CN")
        val hadSaved = saved.exists()
        try {
            PunctuationManager.save(
                fcitx, "zh_CN",
                shipped.filter { it.key != "\"" } + PunctuationMapEntry("\"", "“", "”") + PunctuationMapEntry("\"", "＂", "")
            )
            fcitx.setEnabledIme(arrayOf(Engines.PINYIN))
            sendString("\"")
            Assert.assertEquals("“", awaitPanelPreedit("“"))
            awaitQuiet()
            Assert.assertTrue(fcitx.select(1, "＂"))
            Assert.assertEquals("＂", receiveFirstCommitString()?.data?.text)
            // gone first, so that the “ below is the next press's and not a leftover
            Assert.assertEquals("", awaitPanelPreedit(""))

            sendString("\"")
            Assert.assertEquals("another card picked", "“", awaitPanelPreedit("“"))
            awaitQuiet()
            fcitx.sendKey("BackSpace")
            Assert.assertEquals("", awaitPanelPreedit(""))

            sendString("\"")
            Assert.assertEquals("taken back", "“", awaitPanelPreedit("“"))
            awaitQuiet()
            // the next key puts in the half on offer, and this press then closes the pair
            sendString("\"")
            Assert.assertEquals("“", receiveFirstCommitString()?.data?.text)
            Assert.assertEquals("”", awaitPanelPreedit("”"))
        } finally {
            fcitx.reset()
            PunctuationManager.save(fcitx, "zh_CN", shipped)
            if (!hadSaved) saved.delete()
        }
    }

    /**
     * The same for the half that closes it: with “ in, the key offers ” after another card is
     * picked or Backspace, till ” goes in.
     */
    @Test
    fun aClosingHalfNotPutInLeavesThePairOpen(): Unit = runBlocking {
        val shipped = PunctuationManager.load(fcitx, "zh_CN")
        val saved = File(fcitxHome(), "data/punctuation/punc.mb.zh_CN")
        val hadSaved = saved.exists()
        try {
            PunctuationManager.save(
                fcitx, "zh_CN",
                shipped.filter { it.key != "\"" } + PunctuationMapEntry("\"", "“", "”") + PunctuationMapEntry("\"", "＂", "")
            )
            fcitx.setEnabledIme(arrayOf(Engines.PINYIN))
            sendString("\"")
            Assert.assertEquals("“", awaitPanelPreedit("“"))
            awaitQuiet()
            Assert.assertTrue(fcitx.select(0, "“"))
            Assert.assertEquals("“", receiveFirstCommitString()?.data?.text)

            sendString("\"")
            Assert.assertEquals("”", awaitPanelPreedit("”"))
            awaitQuiet()
            Assert.assertTrue(fcitx.select(1, "＂"))
            Assert.assertEquals("＂", receiveFirstCommitString()?.data?.text)
            // gone first, so that the ” below is the next press's and not a leftover
            Assert.assertEquals("", awaitPanelPreedit(""))

            sendString("\"")
            Assert.assertEquals("another card picked", "”", awaitPanelPreedit("”"))
            awaitQuiet()
            fcitx.sendKey("BackSpace")
            Assert.assertEquals("", awaitPanelPreedit(""))

            sendString("\"")
            Assert.assertEquals("taken back", "”", awaitPanelPreedit("”"))
            awaitQuiet()
            // the next key puts in ”, which closes the pair, and this press then opens it again
            sendString("\"")
            Assert.assertEquals("”", receiveFirstCommitString()?.data?.text)
            Assert.assertEquals("“", awaitPanelPreedit("“"))
        } finally {
            fcitx.reset()
            PunctuationManager.save(fcitx, "zh_CN", shipped)
            if (!hadSaved) saved.delete()
        }
    }

    /** Switching input method puts in the first of the marks on offer, as it keeps what was typed. */
    @Test
    fun switchingInputMethodPutsInTheMarkOnOffer(): Unit = runBlocking {
        fcitx.setEnabledIme(arrayOf(Engines.PINYIN, Engines.SHUANGPIN))
        fcitx.activateIme(Engines.PINYIN)
        sendString("[")
        Assert.assertEquals("【", awaitPanelPreedit("【"))
        awaitQuiet()
        fcitx.activateIme(Engines.SHUANGPIN)
        Assert.assertEquals("【", receiveFirstCommitString()?.data?.text)
        Assert.assertEquals("", awaitPanelPreedit(""))
        fcitx.reset()
    }

    /** A shortcut puts in the first of the marks on offer before it goes to the app, as any other key does. */
    @Test
    fun aShortcutPutsInTheMarkOnOfferBeforeTheAppGetsIt(): Unit = runBlocking {
        fcitx.setEnabledIme(arrayOf(Engines.PINYIN))
        sendString("[")
        Assert.assertEquals("【", awaitPanelPreedit("【"))
        awaitQuiet()
        fcitx.sendKey("v", KeyState.Ctrl.state)
        Assert.assertEquals("【", receiveFirstCommitString()?.data?.text)
        // then the key: what the app pastes goes in after the mark
        val key = receiveFirst<FcitxEvent.KeyEvent>()?.data
        Assert.assertEquals('v'.code, key?.unicode)
        Assert.assertEquals(true, key?.states?.ctrl)
        Assert.assertEquals("", awaitPanelPreedit(""))
        fcitx.reset()
    }

    // endregion

    @Test
    fun testInputPanelStatus(): Unit = runBlocking {
        fcitx.reset()
        Timber.i("after first reset: ${fcitx.isEmpty()}")
        Assert.assertEquals(true, fcitx.isEmpty())
        fcitx.sendKey('a')
        // isEmpty() asks the engine directly, so there is no event to wait for
        Timber.i("after sending 'a': ${fcitx.isEmpty()}")
        Assert.assertEquals(false, fcitx.isEmpty())
        fcitx.reset()
        Timber.i("after second reset: ${fcitx.isEmpty()}")
        Assert.assertEquals(true, fcitx.isEmpty())
    }

    /** A character key reaches fcitx as that character: one the engine does not take comes back to the app as it. */
    @Test
    fun aCharacterKeyPastLatin1ReachesFcitxAsItself(): Unit = runBlocking {
        fcitx.setEnabledIme(arrayOf(Engines.PINYIN))
        // neither of its two bytes is 0: read as a C string, it had no end
        fcitx.sendKey('中')
        Assert.assertEquals('中'.code, receiveFirst<FcitxEvent.KeyEvent>()?.data?.unicode)
        fcitx.reset()
    }

}