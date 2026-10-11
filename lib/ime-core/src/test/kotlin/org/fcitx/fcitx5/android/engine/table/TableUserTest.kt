/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.table

import org.fcitx.fcitx5.android.engine.session.Action.Reset
import org.fcitx.fcitx5.android.engine.session.Action.Select
import org.fcitx.fcitx5.android.engine.store.RecordStore
import org.fcitx.fcitx5.android.engine.user.UserLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.Executor

class TableUserTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val wubi = dictionary("a 工", "aaaa 工", "aaaa 恭恭敬敬", "aaad 工期", "wqiy 你", "wun 们", "vbg 好")

    /** What a user did: picked the second of aaaa, saved 你们 by picking it, and typed 你好 once. */
    private fun teach(session: TableSession) {
        session.type("aaaa")
        session.apply(Select(1))
        session.type("wqiy")
        session.type("wun")
        session.type("wqwu")
        session.apply(Select(0))
        session.apply(Reset)
        session.type("wqiy")
        session.type("vbg")
        session.apply(Reset)
    }

    /**
     * What a session over [user] shows for what [teach] taught. It commits nothing, so it learns
     * nothing: a code leading nowhere shows none.
     */
    private fun shown(user: TableUser = TableUser(wubi)): List<List<String>> {
        val session = TableSession(wubi, TableOptions.WUBI.copy(autoSelect = false), user = user)
        return listOf("aaaa", "wqw", "wqvb").map { session.type(it).second.candidates.also { session.apply(Reset) } }
    }

    /** A session over a user kept in [file], the store that keeps it, and the user. */
    private fun stored(file: File, compactAt: Long = RecordStore.DEFAULT_COMPACT_AT, table: TableDictionary = wubi): Triple<TableSession, TableUser.Store, TableUser> {
        val user = TableUser(table)
        val store = TableUser.Store(file, user, compactAt = compactAt).apply { open() }
        return Triple(TableSession(table, TableOptions.WUBI, user = user), store, user)
    }

    @Test
    fun whatIsTaughtIsKeptAndReadBack() {
        val file = folder.root.resolve("wubi.user")
        val (session, store, user) = stored(file)
        teach(session)
        val expected = shown(user)
        store.close()
        assertEquals(listOf(listOf("恭恭敬敬", "工"), listOf("你们"), listOf("你好")), expected)
        // what a session with nothing kept shows
        assertEquals(listOf("工", "恭恭敬敬"), shown()[0])
        val (_, reopened, again) = stored(file)
        assertEquals(expected, shown(again))
        reopened.close()
    }

    @Test
    fun compactedTheLogReadsTheSame() {
        val file = folder.root.resolve("wubi.user")
        val (session, store, user) = stored(file, compactAt = 1)
        var appended = 0
        val expected = store.use {
            val journal = user.journal
            user.journal = { appended++; journal?.invoke(it) }
            teach(session)
            repeat(3) {
                session.type("aaaa")
                session.apply(Select(1))
            }
            shown(user)
        }
        // compacted on open, then each time the log doubled: fewer records than were appended
        var kept = 0
        TableUser.TableLog.FORMAT.read(file.readBytes()) { _, _ -> kept++ }
        assertTrue("$kept records of $appended appended", kept < appended)
        val (_, reopened, again) = stored(file)
        reopened.use { assertEquals(expected, shown(again)) }
    }

    @Test
    fun compactedInTheBackgroundTheLogReadsTheSame() {
        val file = folder.root.resolve("wubi.user")
        val deferred = ArrayList<Runnable>()
        val user = TableUser(wubi)
        val session = TableSession(wubi, TableOptions.WUBI, user = user)
        val store = TableUser.Store(file, user, compactAt = 1, background = Executor { deferred += it }).apply { open() }
        val expected = store.use {
            teach(session)
            // due from the start, and written only now: what was taught meanwhile goes in after it
            assertEquals(1, deferred.size)
            deferred.single().run()
            repeat(3) {
                session.type("aaaa")
                session.apply(Select(1))
            }
            shown(user)
        }
        assertFalse(File(file.path + ".compacting").exists())
        val (_, reopened, again) = stored(file)
        reopened.use { assertEquals(expected, shown(again)) }
    }

    @Test
    fun aPickOfAnEntryGoneFromTheTableIsTheUsersPhrase() {
        val file = folder.root.resolve("wubi.user")
        val (session, store) = stored(file)
        teach(session)
        store.close()
        // a later table without 恭恭敬敬, left out as rare: the user picked it, it stays theirs
        val (again, reopened) = stored(file, table = dictionary("aaaa 工", "aaaa 敬", "wqiy 你", "wun 们", "vbg 好"))
        assertEquals(listOf("恭恭敬敬", "工", "敬"), again.type("aaaa").second.candidates)
        reopened.close()
    }

    @Test
    fun aLogOfAnotherKindIsSetAside() {
        val file = folder.root.resolve("wubi.user")
        file.writeBytes(UserLog.header())
        val (session, store) = stored(file)
        teach(session)
        store.close()
        assertTrue(File(file.path + RecordStore.UNREADABLE).exists())
        val (_, reopened, again) = stored(file)
        assertFalse(shown(again).any { it.isEmpty() })
        reopened.close()
    }

    @Test
    fun aRecordOfALaterVersionOrWrittenWrongIsSkipped() {
        val file = folder.root.resolve("wubi.user")
        stored(file).let { (session, store) ->
            teach(session)
            store.close()
        }
        // a later type whose fields read as a seen phrase's would, and a pick whose text does not read
        file.appendBytes(TableUser.TableLog.FORMAT.record(9) { writeUTF("vbwq"); writeUTF("好你"); writeInt(5) })
        file.appendBytes(TableUser.TableLog.FORMAT.record(1) { writeShort(2); writeByte(0xFF); writeByte(0xFF) })
        file.appendBytes(TableUser.TableLog.picked("wqwu", "你们", 5))
        val (session, store, user) = stored(file)
        assertEquals(listOf(listOf("恭恭敬敬", "工"), listOf("你们"), listOf("你好")), shown(user))
        // not taken for a phrase seen
        assertEquals(listOf("wqvb" to "你好"), user.seen(""))
        repeat(2) { session.apply(Select(session.type("aaaa").second.candidates.indexOf("工"))) }
        store.close()
        val (_, reopened, again) = stored(file)
        assertEquals("工", shown(again)[0].first())
        reopened.close()
        assertEquals(6, TableUser(wubi).also { TableUser.Store(file, it).apply { open(); close() } }.picks("wqwu", "你们"))
    }

    @Test
    fun whatWasSeenReadsBackInTheOrderSeen() {
        val file = folder.root.resolve("wubi.user")
        val user = TableUser(wubi)
        val store = TableUser.Store(file, user).apply { open() }
        for (code in listOf("xa", "xb", "xc", "xa")) user.sighted(code, "字$code", 3)
        val live = user.seen("")
        assertEquals(listOf("xa", "xc", "xb"), live.map { it.first })
        store.close()
        fun reread() = TableUser(wubi).also { TableUser.Store(file, it).apply { open(); close() } }
        assertEquals(live, reread().seen(""))
        // compacted: the same order again
        TableUser(wubi).let { TableUser.Store(file, it, compactAt = 1).apply { open(); close() } }
        assertEquals(live, reread().seen(""))
        // the third time across a restart saves it
        reread().let { back ->
            TableUser.Store(file, back).apply {
                open()
                back.sighted("xa", "字xa", 3)
                close()
            }
        }
        reread().let {
            assertTrue(it.isSaved("xa", "字xa"))
            assertEquals(listOf("xc", "xb"), it.seen("").map { p -> p.first })
        }
    }

    @Test
    fun onlyTheMostRecentlySeenAreKept() {
        val file = folder.root.resolve("wubi.user")
        val user = TableUser(wubi)
        TableUser.Store(file, user).apply {
            open()
            repeat(1030) { user.sighted("x$it", "字", 5) }
            close()
        }
        val back = TableUser(wubi).also { TableUser.Store(file, it).apply { open(); close() } }
        assertEquals(user.seen(""), back.seen(""))
        assertEquals(1024, back.seen("").size)
        assertEquals("x1029", back.seen("").first().first)
        assertTrue(back.seen("x0").isEmpty())
    }

    @Test
    fun aPhraseALaterTableHasIsTheTablesAlone() {
        val file = folder.root.resolve("wubi.user")
        val (session, store) = stored(file)
        teach(session)
        store.close()
        val (again, reopened, user) = stored(file, table = dictionary("wqwu 你们", "wqiy 你", "wun 们", "vbg 好"))
        // once, not also as a phrase: wqwu itself commits it at once
        assertEquals(listOf("你们"), again.type("wqw").second.candidates)
        assertFalse("你们" in user.savedTexts())
        reopened.close()
    }

    @Test
    fun whatIsForgottenStaysForgotten() {
        val file = folder.root.resolve("wubi.user")
        stored(file).let { (session, store) ->
            teach(session)
            store.close()
        }
        val user = TableUser(wubi)
        TableUser.Store(file, user).apply { open() }.use {
            user.forget("aaaa", "恭恭敬敬")
            user.forget("wqwu", "你们")
            user.forget("wqvb", "你好")
            // nothing learned of it: no record
            val records = ArrayList<ByteArray>()
            val journal = user.journal
            user.journal = { records += it; journal?.invoke(it) }
            user.forget("aaaa", "工")
            assertTrue(records.isEmpty())
        }
        // as nothing was ever learned
        val forgotten = shown()
        val (_, reopened, again) = stored(file)
        assertEquals(forgotten, shown(again))
        reopened.close()
        // compacted, the same
        stored(file, compactAt = 1).second.close()
        val (_, closing, compacted) = stored(file)
        assertEquals(forgotten, shown(compacted))
        closing.close()
    }

    @Test
    fun aSharedWordPickedHereAndForgottenByItsTextIsNotBackAtTheNextStart() {
        val file = folder.root.resolve("wubi.user")
        val user = TableUser(wubi)
        TableUser.Store(file, user).apply { open() }.use {
            // as a session counts a pick of pinyin's 你们 by its code: no entry of the table's
            user.picked("wqwu", "你们")
            // then taken out of pinyin's words
            user.forgetText("你们")
        }
        val back = TableUser(wubi).also { TableUser.Store(file, it).apply { open(); close() } }
        assertFalse(back.isSaved("wqwu", "你们"))
        assertEquals(0, back.picks("wqwu", "你们"))
        assertEquals(emptyList<Pair<String, String>>(), back.saved("wq"))
    }

    @Test
    fun aPhraseSavedIsSavedOnce() {
        val user = TableUser(wubi)
        val records = ArrayList<ByteArray>()
        user.journal = { records += it }
        user.save("wqwu", "你们")
        user.save("wqwu", "你们")
        user.sighted("wqwu", "你们", 3)
        assertEquals(1, records.size)
        assertEquals(listOf("wqwu" to "你们"), user.saved("wq"))
        assertTrue(user.leadsAnywhere("wqw"))
        assertFalse(user.leadsAnywhere("x"))
    }

    @Test
    fun savedPhrasesAreFoundByTheStartOfTheirCodeInCodeOrder() {
        val user = TableUser(wubi)
        user.save("wuwq", "们你")
        user.save("wqwu", "你们")
        user.save("wqvb", "你好")
        assertEquals(listOf("wqvb" to "你好", "wqwu" to "你们"), user.saved("wq"))
        assertEquals(listOf("wqwu" to "你们"), user.saved("wqw"))
        assertEquals(3, user.saved("").size)
        assertTrue(user.leadsAnywhere("wu"))
        assertFalse(user.leadsAnywhere("wv"))
        assertFalse(user.leadsAnywhere("wqwuw"))
    }

    @Test
    fun manyTextsAreForgottenAtOnceUnderEveryCodeTheyWereLearnedBy() {
        val user = TableUser(wubi)
        user.save("wqwu", "你们")
        user.save("wqwx", "你们")
        user.save("wuwq", "们你")
        user.sighted("wqvb", "你好", 3)
        user.picked("vbwq", "好你")
        val records = ArrayList<ByteArray>()
        user.journal = { records += it }
        user.forgetTexts(setOf("你们", "你好", "好你"))
        assertEquals(setOf("们你"), user.savedTexts())
        assertEquals(emptyList<Pair<String, String>>(), user.seen(""))
        assertEquals(0, user.picks("vbwq", "好你"))
        // one record for each code and text forgotten: 你们 twice, 你好, 好你
        assertEquals(4, records.size)
    }
}
