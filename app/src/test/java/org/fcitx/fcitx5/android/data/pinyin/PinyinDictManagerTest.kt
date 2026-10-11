/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.pinyin

import kotlinx.coroutines.runBlocking
import org.fcitx.fcitx5.android.FcitxApplication
import org.fcitx.fcitx5.android.data.pinyin.dict.PinyinDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.io.InputStream

// the real application: the manager and its error messages go through appContext
@RunWith(RobolectricTestRunner::class)
@Config(application = FcitxApplication::class)
class PinyinDictManagerTest {

    // the manager's own, made once for the class: each test's application has another external dir
    private val dir get() = PinyinDictManager.pinyinDicDir
    private val pack = "# youmo words 1\n# layer: 2026q1\n搭子 da'zi -5.6\n"

    // the dictionaries, not the dot files kept among them (.new-words, .libime)
    private fun names() = dir.list()!!.filter { !it.startsWith(".") }.sorted()

    @Before
    fun empty() {
        dir.listFiles()?.forEach { it.deleteRecursively() }
        // made when the manager first ran, under that test's application: it may be gone since
        dir.mkdirs()
    }

    @Test
    fun aPackReplacesTheLastOfItsNameOnOrOffAsItWas() {
        val first = PinyinDictManager.importPack("2026q1", pack).getOrThrow()
        assertEquals(PinyinDictionary.Type.Words, first.type)
        assertEquals(listOf("2026q1.words"), names())
        // new words: merged into the new words dictionary
        assertTrue(PinyinDictManager.isIntoNew("2026q1"))
        assertEquals(setOf("2026q1"), PinyinDictManager.intoNew())
        PinyinDictManager.setIntoNew("2026q1", false).getOrThrow()
        assertTrue(first.disable())
        PinyinDictManager.importPack("2026q1", pack + "智驾 zhi'jia -5.9\n").getOrThrow()
        assertEquals(listOf("2026q1.words.disable"), names())
        assertTrue(File(dir, "2026q1.words.disable").readText().contains("智驾"))
        // the next one stays where the user moved the last
        assertFalse(PinyinDictManager.isIntoNew("2026q1"))
        // the imported dictionaries' list has it
        assertEquals(listOf("2026q1"), PinyinDictManager.listDictionaries().map { it.name })
    }

    @Test
    fun aPackThatIsNoPackOrNamedLikeAPlainDictionaryIsRefused() {
        assertTrue(PinyinDictManager.importPack("../x", pack).isFailure)
        assertTrue(PinyinDictManager.importPack("bad", "你 ni 0\n").isFailure)
        assertTrue(PinyinDictManager.importPack("empty", "# youmo words 1\n").isFailure)
        File(dir, "mine.txt").writeText("你 ni 0\n")
        assertTrue(PinyinDictManager.importPack("mine", pack).isFailure)
        assertEquals(listOf("mine.txt"), names())
    }

    @Test
    fun aTextFileThatStartsAsAPackIsImportedAsOne() {
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "new.txt").apply { writeText("\uFEFF$pack") }
        val imported = PinyinDictManager.importFromFile(file).getOrThrow()
        assertEquals(PinyinDictionary.Type.Words, imported.type)
        assertEquals("new", imported.name)
        assertEquals(pack, imported.file.readText())
        // a second of the name, as a plain dictionary, is refused
        file.writeText("你 ni 0\n")
        assertTrue(PinyinDictManager.importFromFile(file).isFailure)
    }

    @Test
    fun aDictionaryWhoseExtensionIsInCapitalsIsImported() {
        val imported = PinyinDictManager.importFromInputStream("搭子 da'zi 0\n".byteInputStream(), "MINE.TXT").getOrThrow()
        assertEquals("MINE", imported.name)
        assertEquals(listOf("MINE.txt"), names())
        assertEquals(listOf("搭子 da'zi 0"), runBlocking { PinyinDictManager.readWords("搭子 da'zi 0\n".byteInputStream(), "WORDS.TXT") { it.toList() } })
    }

    @Test
    fun theStreamImportedFromIsClosedAndNothingIsLeftOfACopyThatFailed() {
        val imported = Provided("搭子 da'zi 0\n".toByteArray())
        PinyinDictManager.importFromInputStream(imported, "mine.txt").getOrThrow()
        assertTrue(imported.closed)
        val broken = Provided(null)
        assertThrows(IOException::class.java) { PinyinDictManager.importFromInputStream(broken, "theirs.txt") }
        assertTrue(broken.closed)
        assertFalse(File(RuntimeEnvironment.getApplication().cacheDir, "theirs.txt").exists())
        val cache = RuntimeEnvironment.getApplication().cacheDir
        val cached = cache.list()!!.toSet()
        val read = Provided("搭子 da'zi 0\n".toByteArray())
        runBlocking { PinyinDictManager.readWords(read, "words.txt") { it.toList() } }
        assertTrue(read.closed)
        val unread = Provided(null)
        assertThrows(IOException::class.java) { runBlocking { PinyinDictManager.readWords(unread, "words.txt") { it.toList() } } }
        assertTrue(unread.closed)
        // nor of the copy readWords makes and its text, read or not
        assertEquals(cached, cache.list()!!.toSet())
        assertEquals(listOf("mine.txt"), names())
    }

    @Test
    fun theProvidersStreamIsClosedBeforeTheWordsAreTaken() {
        val provided = Provided("搭子 da'zi 0\n".toByteArray())
        val words = runBlocking {
            PinyinDictManager.readWords(provided, "words.txt") {
                // the engine takes them on its own thread: a slow provider would hold up the keyboard
                assertTrue(provided.closed)
                it.toList()
            }
        }
        assertEquals(listOf("搭子 da'zi 0"), words)
    }

    @Test
    fun aProvidersNameIsNoPathOutOfTheCache() {
        val cache = RuntimeEnvironment.getApplication().cacheDir
        val outside = File(cache.parentFile, "outside.txt").apply { writeText("theirs") }
        try {
            val imported = PinyinDictManager.importFromInputStream("搭子 da'zi 0\n".byteInputStream(), "../outside.txt").getOrThrow()
            assertEquals("outside", imported.name)
            assertEquals("theirs", outside.readText())
            val dots = Provided("搭子 da'zi 0\n".toByteArray())
            assertTrue(PinyinDictManager.importFromInputStream(dots, "..").isFailure)
            assertTrue(dots.closed)
            assertTrue(cache.isDirectory)
            assertEquals(listOf("outside.txt"), names())
        } finally {
            outside.delete()
        }
    }

    @Test
    fun aPackWhoseNameCannotBeListedIsNotKept() {
        assertTrue(PinyinDictManager.importPack("a\nb", pack).isFailure)
        assertEquals(emptyList<String>(), names())
        // nor put in the base when tried again, as one already there would be
        assertTrue(PinyinDictManager.importPack("a\nb", pack).isFailure)
        assertEquals(emptyList<String>(), names())
    }

    @Test(timeout = 10_000)
    fun aConverterIsReadThroughAsItRunsAndWhatItPrintedIsTheError() {
        assumeTrue(File("/bin/sh").canExecute())
        val lib = RuntimeEnvironment.getApplication().cacheDir
        // 100 000 bytes, more than a pipe holds: read only after the wait, it would never end
        val loud = "i=0; while [ \$i -lt 2000 ]; do echo 0123456789012345678901234567890123456789012345678 >&2; i=\$((i+1)); done; exit 3"
        val e = assertThrows(IOException::class.java) { PinyinDictManager.runConverter(listOf("/bin/sh", "-c", loud), lib) }
        assertEquals(100_000, e.message!!.length)
        val path = assertThrows(IOException::class.java) {
            PinyinDictManager.runConverter(listOf("/bin/sh", "-c", "echo \"\$LD_LIBRARY_PATH\"; exit 1"), lib)
        }
        assertEquals("${lib.absolutePath}\n", path.message)
        PinyinDictManager.runConverter(listOf("/bin/sh", "-c", "exit 0"), lib)
    }

    @Test
    fun aPackFirstImportedUnderAnyNameIsReplacedByItsNext() {
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "新词 2026.words").apply { writeText(pack) }
        PinyinDictManager.importFromFile(file).getOrThrow()
        PinyinDictManager.importPack("新词 2026", pack + "智驾 zhi'jia -5.9\n").getOrThrow()
        assertEquals(listOf("新词 2026.words"), names())
        assertTrue(File(dir, "新词 2026.words").readText().contains("智驾"))
    }

    @Test
    fun packsFromBeforeTheLayersWereChosenStayWhereTheirHeadersPutThem() {
        File(dir, "cloud.words").writeText("# youmo words 1\n# layer: new\n搭子 da'zi -5.6\n")
        File(dir, "plain.words.disable").writeText("# youmo words 1\n# layer: base\n搭子 da'zi -5.6\n")
        File(dir, "mine.txt").writeText("搭子 da'zi 0\n")
        assertEquals(setOf("cloud"), ImportedDictionaries.intoNew(dir))
        // the first change keeps them so
        PinyinDictManager.setIntoNew("plain", true).getOrThrow()
        assertEquals(setOf("cloud", "plain"), ImportedDictionaries.intoNew(dir))
    }

    @Test
    fun aDictionaryIsMergedWhereAskedAndForgottenWhenDeleted() {
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "mine.txt").apply { writeText("搭子 da'zi 0\n") }
        val base = PinyinDictManager.importFromFile(file).getOrThrow()
        assertFalse(PinyinDictManager.isIntoNew("mine"))
        file.renameTo(File(file.parentFile, "theirs.txt"))
        val new = PinyinDictManager.importFromFile(File(file.parentFile, "theirs.txt"), intoNew = true).getOrThrow()
        assertTrue(PinyinDictManager.isIntoNew("theirs"))
        assertEquals(setOf("theirs"), ImportedDictionaries.intoNew(dir))
        assertTrue(PinyinDictManager.delete(new))
        assertEquals(emptySet<String>(), ImportedDictionaries.intoNew(dir))
        assertEquals(listOf("mine.txt"), names())
        assertEquals(1, PinyinDictManager.wordCount(base))
    }

    // a provider's stream: [bytes], or none that can be read
    private class Provided(bytes: ByteArray?) : InputStream() {
        private val input = bytes?.inputStream()
        var closed = false

        override fun read(): Int = input?.read() ?: throw IOException("I/O error")

        override fun close() {
            closed = true
        }
    }
}
