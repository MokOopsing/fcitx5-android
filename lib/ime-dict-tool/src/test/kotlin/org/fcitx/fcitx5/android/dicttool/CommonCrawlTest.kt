/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.dicttool

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream

class CommonCrawlTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val chinese = "今天天气很好我们一起去公园散步吧".repeat(4) // 64 Han characters

    /** A WARC record as a WET file has it: a gzip member of its own. */
    private fun record(type: String, language: String?, url: String, text: String): ByteArray {
        val content = text.toByteArray(Charsets.UTF_8)
        val headers = buildString {
            append("WARC/1.0\r\nWARC-Type: $type\r\nWARC-Target-URI: $url\r\nWARC-Date: 2026-09-04T13:16:03Z\r\n")
            if (language != null) append("WARC-Identified-Content-Language: $language\r\n")
            append("Content-Type: text/plain\r\nContent-Length: ${content.size}\r\n\r\n")
        }
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use {
            it.write(headers.toByteArray(Charsets.UTF_8))
            it.write(content)
            it.write("\r\n\r\n".toByteArray())
        }
        return bytes.toByteArray()
    }

    private fun wet(vararg pages: Pair<String, String>): ByteArray =
        record("warcinfo", null, "", "software: test\r\n") + pages.map { (url, text) -> record("conversion", "zho", url, text) }.reduce(ByteArray::plus)

    private fun pages(bytes: ByteArray) = ArrayList<WebText.Page>().also { CommonCrawl.pages(ByteArrayInputStream(bytes), it::add) }

    @Test
    fun theChinesePagesOfAWetFileAreTaken() {
        val bytes = record("warcinfo", null, "", "software: test\r\n") +
            record("conversion", "zho", "https://a.example.com/", chinese) +
            // Chinese not first: an English page with a Chinese line
            record("conversion", "eng,zho", "https://b.example.com/", chinese) +
            record("conversion", "zho,eng", "https://c.example.com/", "Hello\n$chinese") +
            // too little Chinese to count, and not a page
            record("conversion", "zho", "https://d.example.com/", "今天天气很好") +
            record("metadata", "zho", "https://e.example.com/", chinese)
        val found = pages(bytes)
        assertEquals(listOf("https://a.example.com/", "https://c.example.com/"), found.map { it.url })
        assertEquals(chinese, found[0].text)
        assertEquals("Hello\n$chinese", found[1].text)
        assertEquals("2026-09-04T13:16:03Z", found[0].date)
    }

    @Test
    fun aFileThatIsNotWarcIsAnError() {
        val bytes = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write("<html></html>\n".toByteArray()) } }.toByteArray()
        assertThrows(IOException::class.java) { pages(bytes) }
        // cut short: the length says more than there is
        val cut = record("conversion", "zho", "https://a.example.com/", chinese).let { full ->
            ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write("WARC/1.0\r\nContent-Length: 999\r\n\r\nabc".toByteArray()) } }.toByteArray() + full
        }
        assertThrows(IOException::class.java) { pages(cut) }
    }

    @Test
    fun aRangeOfACrawlsFilesBecomesAShardInTheirOrder() {
        val base = tmp.newFolder("cc")
        val names = listOf("a", "b", "c").map { "crawl-data/CC-TEST/segments/1/wet/$it.warc.wet.gz" }
        names.forEach { name ->
            File(base, name).apply { parentFile.mkdirs() }.writeBytes(wet("https://$name/1" to chinese, "https://$name/2" to chinese))
        }
        File(base, "crawl-data/CC-TEST/wet.paths.gz").outputStream().use { out -> GZIPOutputStream(out).use { it.write(names.joinToString("\n", postfix = "\n").toByteArray()) } }
        val out = tmp.newFolder("out")
        val log = ArrayList<String>()
        CommonCrawl.extract("CC-TEST", 1, 2, out, base.path) { log += it }
        val shard = File(out, "CC-TEST-00001-00003.parquet")
        val urls = ArrayList<String>().also { list -> WebText.pages(shard) { list += it.url } }
        assertEquals(listOf(names[1], names[1], names[2], names[2]).mapIndexed { i, n -> "https://$n/${i % 2 + 1}" }, urls)
        assertEquals(listOf("CC-TEST-00001-00003.parquet: 2 files, 4 pages, 256 Han characters"), log)
        // run again, the shard is kept as it is
        val written = shard.lastModified()
        CommonCrawl.extract("CC-TEST", 1, 2, out, base.path) { log += it }
        assertEquals("CC-TEST-00001-00003.parquet: there already", log.last())
        assertEquals(written, shard.lastModified())
        assertTrue(out.list()!!.none { it.endsWith(".tmp") })
        // past the crawl's files
        assertThrows(IllegalArgumentException::class.java) { CommonCrawl.extract("CC-TEST", 2, 2, out, base.path) {} }
    }

    @Test
    fun severalShardsAreEachWrittenInTurnTheLastToo() {
        val base = tmp.newFolder("cc")
        val names = (0..CommonCrawl.FILES_PER_SHARD).map { "crawl-data/CC-TEST/segments/1/wet/$it.warc.wet.gz" }
        names.forEach { name -> File(base, name).apply { parentFile.mkdirs() }.writeBytes(wet("https://$name/" to chinese)) }
        File(base, "crawl-data/CC-TEST/wet.paths.gz").outputStream().use { out -> GZIPOutputStream(out).use { it.write(names.joinToString("\n").toByteArray()) } }
        val out = tmp.newFolder("out")
        val log = ArrayList<String>()
        CommonCrawl.extract("CC-TEST", 0, names.size, out, base.path) { log += it }
        assertEquals(
            listOf("CC-TEST-00000-00100.parquet: 100 files, 100 pages, 6400 Han characters", "CC-TEST-00100-00101.parquet: 1 files, 1 pages, 64 Han characters"),
            log,
        )
        val urls = ArrayList<String>().also { list ->
            for (shard in listOf("CC-TEST-00000-00100.parquet", "CC-TEST-00100-00101.parquet")) WebText.pages(File(out, shard)) { list += it.url }
        }
        assertEquals(names.map { "https://$it/" }, urls)
        // the first gone, the last there: the first is still written
        assertTrue(File(out, "CC-TEST-00000-00100.parquet").delete())
        log.clear()
        CommonCrawl.extract("CC-TEST", 0, names.size, out, base.path) { log += it }
        assertEquals(listOf("CC-TEST-00100-00101.parquet: there already", "CC-TEST-00000-00100.parquet: 100 files, 100 pages, 6400 Han characters"), log)
    }

    /** A server on this machine answering each request with the next of [statuses], a 200 with "body". */
    private fun serve(vararg statuses: Int, test: (base: String, asked: () -> Int) -> Unit) {
        val left = ArrayDeque(statuses.toList())
        val asked = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            asked.incrementAndGet()
            val status = synchronized(left) { left.removeFirst() }
            val body = if (status == 200) "body".toByteArray() else ByteArray(0)
            exchange.sendResponseHeaders(status, if (body.isEmpty()) -1L else body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            test("http://127.0.0.1:${server.address.port}/") { asked.get() }
        } finally {
            server.stop(0)
        }
    }

    /** Each of [pauses] seconds as it was slept: that long, and up to half as long again at random. */
    private fun assertPauses(pauses: List<Long>, slept: List<Long>) {
        assertEquals(pauses.size, slept.size)
        pauses.zip(slept).forEach { (seconds, millis) -> assertTrue("$millis ms for $seconds s", millis in seconds * 1000 until seconds * 1500) }
    }

    @Test
    fun aFileIsFetchedAgainAfterAPauseEachTimeTheServerSaysToWait() {
        serve(503, 429, 500, 200) { base, asked ->
            val log = ArrayList<String>()
            val slept = ArrayList<Long>()
            assertEquals("body", String(CommonCrawl.fetch(base, "a.gz", log::add, slept::add)))
            assertEquals(4, asked())
            // twice as long each time
            assertPauses(listOf(10L, 20L, 40L), slept)
            assertEquals(listOf(503, 429, 500).mapIndexed { i, status -> "a.gz: HTTP $status, try ${i + 2} of 8 in ${slept[i] / 1000} s" }, log)
        }
    }

    @Test
    fun aFileThatIsNotThereFailsAtOnce() {
        serve(404) { base, asked ->
            val log = ArrayList<String>()
            val slept = ArrayList<Long>()
            val e = assertThrows(IOException::class.java) { CommonCrawl.fetch(base, "a.gz", log::add, slept::add) }
            assertEquals("a.gz: HTTP 404", e.message)
            assertEquals(1, asked())
            assertEquals(emptyList<Long>(), slept)
            assertEquals(emptyList<String>(), log)
        }
    }

    @Test
    fun aFileIsGivenUpAfterEightTries() {
        serve(*IntArray(8) { 503 }) { base, asked ->
            val slept = ArrayList<Long>()
            val e = assertThrows(IOException::class.java) { CommonCrawl.fetch(base, "a.gz", {}, slept::add) }
            assertEquals("a.gz: HTTP 503", e.message)
            assertEquals(8, asked())
            // up to five minutes
            assertPauses(listOf(10L, 20L, 40L, 80L, 160L, 300L, 300L), slept)
        }
        // nothing listening: the network's own error is tried as often
        val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val log = ArrayList<String>()
        val slept = ArrayList<Long>()
        assertThrows(IOException::class.java) { CommonCrawl.fetch("http://127.0.0.1:$port/", "a.gz", log::add, slept::add) }
        assertEquals(7, slept.size)
        assertEquals(7, log.size)
    }
}
