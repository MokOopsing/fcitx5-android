/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.host

import org.fcitx.fcitx5.android.engine.data.CodeTable
import org.fcitx.fcitx5.android.engine.data.CodeTableReader
import org.fcitx.fcitx5.android.engine.data.DataFormatException
import org.fcitx.fcitx5.android.engine.data.SourceException
import org.fcitx.fcitx5.android.engine.host.Engines.Companion.FORGOTTEN
import org.fcitx.fcitx5.android.engine.host.Engines.Companion.TABLES
import org.fcitx.fcitx5.android.engine.host.Engines.Companion.TABLE_DIR
import org.fcitx.fcitx5.android.engine.host.Engines.Companion.TOLD
import org.fcitx.fcitx5.android.engine.host.Engines.Companion.USER_TABLES
import org.fcitx.fcitx5.android.engine.host.Engines.Companion.addedTableCache
import org.fcitx.fcitx5.android.engine.host.Engines.Companion.addedTableLog
import org.fcitx.fcitx5.android.engine.host.Engines.Companion.userTable
import org.fcitx.fcitx5.android.engine.host.Engines.UserTable
import org.fcitx.fcitx5.android.engine.table.CodedWords
import org.fcitx.fcitx5.android.engine.table.SharedWords
import org.fcitx.fcitx5.android.engine.table.TableConf
import org.fcitx.fcitx5.android.engine.table.TableDictionary
import org.fcitx.fcitx5.android.engine.table.TableOptions
import org.fcitx.fcitx5.android.engine.table.TableText
import org.fcitx.fcitx5.android.engine.table.TableUser
import org.fcitx.fcitx5.android.engine.user.UserModel
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.Executor

/**
 * The table input methods of [Engines], each loaded at first need and kept, with what the user
 * taught it: those of [TABLES] from the app's assets ([load]), and those the user added
 * ([userTables]), built from their text and kept built under [userDir] till their file changes.
 * It shares the user's words ([lexicon]) with each table, coded by its rules ([shared]), tells
 * pinyin what a table saved, and forgets in every table, loaded or not, what is forgotten
 * anywhere ([forgetInTables]). Run on the fcitx thread, as [Engines] is.
 */
internal class TableRegistry(
    private val load: (String) -> ByteBuffer,
    private val userDir: File?,
    private val onError: (IOException) -> Unit,
    private val userTables: (String) -> UserTable?,
    private val compactions: Executor,
    private val assets: EngineAssets,
    private val lexicon: UserLexicon,
) {

    /**
     * A table and what the user taught it: both outlive the sessions made over them. [options]:
     * those of a table the user added, as its `.conf` has them; null for one of [TABLES]. Not
     * [learns], as libime's table with Learning off, it keeps nothing, not even till the next start.
     */
    class Table(
        val dictionary: TableDictionary,
        val user: TableUser,
        val store: TableUser.Store?,
        val options: TableOptions? = null,
        val learns: Boolean = true,
    ) {
        var shared: SharedWords? = null

        // the user's words coded for this table: those of their dictionaries, made once a user
        // model, with those add() listed since apart ([later], from [listedFrom] in listedSince);
        // and those they made or typed, as of its changes then
        var listed: CodedWords? = null
        var later: CodedWords? = null
        var own: CodedWords? = null
        var codedFor: UserModel? = null
        var listedAt = -1
        var listedFrom = 0
        var laterTo = -1
        var ownAt = -1
    }

    private val listedSince get() = lexicon.listedSince

    /**
     * One user lexicon for every input method: [table] offers the words the user made, added or
     * typed with pinyin, coded by its rules, as of now; the phrases it saves become pinyin's
     * words (those saved before this, once); what is blocked or forgotten anywhere is so here.
     */
    fun shared(table: Table, im: String): SharedWords = table.shared ?: object : SharedWords {
        override fun words(prefix: String) = coded().flatMap { it.words(prefix) }
        override fun leadsAnywhere(prefix: String) = coded().any { it.leadsAnywhere(prefix) }
        override fun blocked(text: String) = lexicon.isBlockedWord(text)
        override fun blockable(text: String) = text.codePointCount(0, text.length) >= 2 && assets.pinyinOf(text) != null
        override fun block(text: String) = lexicon.blockText(text)
        override fun forget(text: String) = lexicon.forgetEverywhere(text)

        private fun coded(): List<CodedWords> {
            val model = lexicon.user()
            if (table.codedFor !== model) {
                table.listed = null
                table.own = null
                table.codedFor = model
            }
            // all coded again only once words were listed other than by add() (a reload's dictionaries)
            val listed = table.listed?.takeIf { model.listings - table.listedAt == listedSince.size - table.listedFrom }
                ?: CodedWords(table.dictionary, model.listedTexts()).also {
                    table.listed = it
                    table.listedAt = model.listings
                    table.listedFrom = listedSince.size
                    table.later = null
                }
            val later = table.later?.takeIf { table.laterTo == listedSince.size }
                ?: listedLater(table, listed).also {
                    table.later = it
                    table.laterTo = listedSince.size
                }
            val own = table.own?.takeIf { table.ownAt == model.changes }
                ?: CodedWords(table.dictionary, model.ownTexts()).also {
                    table.own = it
                    table.ownAt = model.changes
                }
            return listOf(own, listed, later)
        }
    }.also { shared ->
        table.shared = shared
        if (table.learns) {
            // what learnSaved looks a saved phrase up in, made in the background rather than on the input thread at the first save
            val data = assets.pinyinData
            compactions.execute { data.wordIndex }
            table.user.onSaved = { lexicon.learnSaved(listOf(it), again = true) }
            tellPinyinOnce(table, im)
        }
    }

    /** What add() listed since [table]'s [listed] were coded, coded alone; a text listed before in another reading left out, coded already. */
    private fun listedLater(table: Table, listed: CodedWords): CodedWords {
        val texts = listedSince.subList(table.listedFrom, listedSince.size).filterNot { text ->
            table.dictionary.encode(text)?.let { code -> listed.words(code).any { it.first == code && it.second == text } } == true
        }
        return CodedWords(table.dictionary, texts)
    }

    /**
     * What [table] saved before pinyin heard of its phrases, told it once: a phrase the user took
     * out of pinyin's words later is not put back.
     */
    private fun tellPinyinOnce(table: Table, im: String) {
        val log = if (im in TABLES) userTable(im) else addedTableLog(im)
        val told = userDir?.let { File(it, log + TOLD) }
        if (told?.exists() == true) return
        lexicon.learnSaved(table.user.savedTexts(), again = false)
        try {
            told?.createNewFile()
        } catch (_: IOException) {
            // told again next time, harmlessly: pinyin learns only those it lacks; where the log
            // cannot be kept either, that is reported already
        }
    }

    // the tables loaded, and those with a log on disk: a phrase saved there would be shared again
    fun forgetInTables(texts: Collection<String>) {
        val forgotten = texts.toSet()
        val logged = TABLES.keys.filter { im -> im in tables || userDir?.let { File(it, userTable(im)).exists() } == true }
        for (im in logged) {
            val user = table(im).user
            user.forgetTexts(forgotten)
        }
        for ((im, table) in tables) if (im !in TABLES) table.user.forgetTexts(forgotten)
        // those the user added, which reload() unloads: forgotten as each loads next, as building
        // one here would hold up the keys typed after it, for seconds where it changed
        val dir = userDir ?: return
        val names = File(dir, USER_TABLES).list().orEmpty().toSet()
        for (im in names.mapTo(HashSet()) { it.substringBeforeLast('.') }) {
            if (!forgetsAsItLoads(im, names)) continue
            keepForgotten(File(dir, addedTableLog(im) + FORGOTTEN), texts)
        }
    }

    /**
     * Whether [im] is an added table with its log among [names] that does not read it now (not
     * loaded, or loaded with no log open, as when it learns nothing) and may as it loads ([mayReadLog], last).
     */
    private fun forgetsAsItLoads(im: String, names: Set<String>): Boolean =
        im !in TABLES && tables[im]?.store == null && File(addedTableLog(im)).name in names && mayReadLog(im)

    /**
     * Whether the added table [im] may read its log as it loads, as its `.conf` (read alone) says:
     * one that learns nothing now does once Learning is on again; not one since removed, nor one
     * that names no table, for which what is forgotten would be kept for good.
     */
    private fun mayReadLog(im: String): Boolean = try {
        userTables(im)?.let { TableConf.parse(it.conf, it.settings) } != null
    } catch (_: IOException) {
        // unreadable now, it may read as it loads, which reports it if not
        true
    } catch (_: IllegalArgumentException) {
        false
    }

    /** Keeps [texts] in [file], each once, for an added table to forget as it loads ([forgetKept]). */
    private fun keepForgotten(file: File, texts: Collection<String>) {
        try {
            val text = if (file.isFile) file.readText() else ""
            val kept = forgottenIn(text)
            // a table's phrase holds no line break
            val more = texts.filter { '\n' !in it && it !in kept }.distinct()
            if (more.isEmpty()) return
            // not run on into a last line cut short by a kill, which would take it with it
            val start = if (text.isNotEmpty() && !text.endsWith('\n')) "\n" else ""
            file.appendText(more.joinToString("", prefix = start) { "$it\n" })
        } catch (e: IOException) {
            onError(e)
        }
    }

    /**
     * What was forgotten while [im]'s table did not read its log ([keepForgotten]), forgotten in [user] now;
     * kept for the next load too if the log reports trouble as it forgets ([troubled]), as what it forgot may not have reached it.
     */
    private fun forgetKept(im: String, user: TableUser, troubled: () -> Boolean) {
        val file = userDir?.let { File(it, addedTableLog(im) + FORGOTTEN) }?.takeIf { it.isFile } ?: return
        try {
            user.forgetTexts(forgottenIn(file.readText()))
            if (!troubled()) file.delete()
        } catch (e: IOException) {
            // forgotten at the next load
            onError(e)
        }
    }

    // whole lines only: one cut short by a kill is no text that was forgotten
    private fun forgottenIn(text: String): Set<String> = text.split('\n').dropLast(1).toSet()

    private val tables = HashMap<String, Table>()

    fun table(im: String): Table = tables.getOrPut(im) {
        val dictionary = TableDictionary(CodeTable.load(load("$TABLE_DIR/${TABLES.getValue(im).file}"), verify = false))
        val user = TableUser(dictionary)
        Table(dictionary, user, userDir?.let { openStore(File(it, userTable(im)), user) })
    }

    // a log that cannot be read or moved aside: learn in memory rather than not type
    private fun openStore(file: File, user: TableUser, report: (IOException) -> Unit = onError): TableUser.Store? {
        file.parentFile?.mkdirs()
        return try {
            TableUser.Store(file, user, report, background = compactions).apply { open() }
        } catch (e: IOException) {
            onError(e)
            null
        }
    }

    // added tables that failed, not tried again (on every key) until reload
    private val failed = HashSet<String>()

    /** A table the user added, built from its text unless built already; what fails is reported. */
    fun addedTable(im: String): Table = tables.getOrPut(im) {
        require(im !in failed) { "input method $im failed to load" }
        // until it loads: not tried again on every key
        failed += im
        val table = try {
            val source = userTables(im) ?: throw IllegalArgumentException("no input method $im")
            val conf = TableConf.parse(source.conf, source.settings)
            val dictionary = TableDictionary(built(im, conf.file, source))
            val user = TableUser(dictionary)
            // what the log reports as it forgets: it may not have taken what was kept to forget
            var troubled = false
            val report = { e: IOException ->
                troubled = true
                onError(e)
            }
            val store = if (conf.learning) userDir?.let { openStore(File(it, addedTableLog(im)), user, report) } else null
            // not what it reported as it opened (records skipped, a log moved aside), which may recur
            // at every open: the file kept for that would forget again what the user taught since
            troubled = false
            // with no log read there is nothing to forget: kept for a load that reads one
            if (store != null) forgetKept(im, user) { troubled }
            Table(dictionary, user, store, conf.options, conf.learning)
        } catch (e: IOException) {
            throw unreadable(im, e)
        } catch (e: SourceException) {
            throw unreadable(im, e)
        } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
            // a table file from anywhere (DataFormatException, say): no failure should go unsaid
            throw unreadable(im, e)
        }
        failed -= im
        table
    }

    private fun unreadable(im: String, e: Throwable): UnreadableInputMethod {
        onError(e as? IOException ?: IOException("cannot read input method $im", e))
        return UnreadableInputMethod(im, e)
    }

    /**
     * [source]'s table: as built before if its stamp is the same, else built from its text and
     * kept for next time. One that cannot be kept is built again at the next start.
     */
    private fun built(im: String, file: String, source: UserTable): CodeTable {
        // built again when the file changes, or how its text is read
        val stamp = "$BUILD $file ${source.stamp(file)}"
        val cache = userDir?.let { File(it, addedTableCache(im)) }
        if (cache != null && cache.isFile) {
            try {
                val table = CodeTable.load(map(cache))
                if (table.header[TableText.STAMP] == stamp) return table
            } catch (e: DataFormatException) {
                // cut short by a kill, or by a build that writes them otherwise: built again
                onError(IOException("cannot read $cache", e))
            } catch (e: IOException) {
                onError(e)
            }
        }
        val reader = CodeTableReader()
        source.text(file).use { reader.read(it, file) }
        reader.builder.header(TableText.STAMP, stamp)
        val bytes = TableText.build(reader, file)
        if (cache != null) {
            try {
                cache.parentFile?.mkdirs()
                val next = File(cache.path + ".new")
                next.writeBytes(bytes)
                if (!next.renameTo(cache)) throw IOException("cannot replace $cache")
                // mapped, as at the next start: pages the OS may drop, not a heap the size of the table
                return CodeTable.load(map(cache), verify = false)
            } catch (e: IOException) {
                onError(e)
            }
        }
        return CodeTable.load(ByteBuffer.wrap(bytes), verify = false)
    }

    private fun map(file: File): ByteBuffer = RandomAccessFile(file, "r").use {
        it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.length())
    }

    /** Unloads the tables the user added, for [Engines.reload]: each asked for again as it next is. */
    fun unloadAdded() {
        // the tables the user added are asked for again: built again only if they changed
        failed.clear()
        for (im in tables.keys.filter { it !in TABLES }) tables.remove(im)?.store?.let { closeQuietly(it, onError) }
    }

    /** The logs of the tables loaded, for [Engines.close] to close. */
    fun stores(): List<TableUser.Store> = tables.values.mapNotNull { it.store }

    /** After [stores] were closed: each table loads again, reads its log again, and appends to it. */
    fun closed() {
        tables.clear()
    }

    private companion object {
        // raised when a table's text is read otherwise (CodeTableReader, TableText, PhraseRules):
        // built again. 2: a key a rule names twice from the front is taken twice, as libime does
        const val BUILD = 2
    }
}
