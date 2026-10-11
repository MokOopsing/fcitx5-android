/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.engine.store

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException

/**
 * Keeps what the user taught an input method in [file], a log of [format]: [open] replays it into
 * memory, then each record [append]ed is one write. Once the log passes [compactAt] bytes (or
 * twice what it last compacted to) it is rewritten as the counts alone, into a new file renamed
 * over the old, so a crash leaves one or the other whole. That happens on the thread that learns,
 * at most every few thousand records; given a [background] executor, that thread only reads the
 * counts when an append (or [open]) makes it due, and the executor writes and syncs them, so a
 * keystroke does not wait on the disk. The next append then puts them in place, with what was
 * appended meanwhile behind them.
 *
 * A log cut short loses its last record, which is cut from the file so appends go on from a
 * record's end. A file of another format or version is kept aside as `<name>.unreadable` rather
 * than overwritten, and learning starts afresh. A log that stops reading anywhere but in a last
 * record cut short, as a torn append leaves it, is damaged: it is kept aside whole as well, and
 * goes on from what read.
 *
 * Writing never fails the learning that asked for it: text the user picked must reach the app
 * whatever the disk does. A failed write is reported to [onError] and cut back off the file, so
 * later appends do not land behind torn bytes; learning goes on in memory regardless.
 */
class RecordStore internal constructor(
    private val file: File,
    private val format: RecordFormat,
    private val compactAt: Long,
    private val onError: (IOException) -> Unit,
    // a seam for tests: a disk that fails part-way through a write
    private val openAppend: (File) -> OutputStream,
    // writes a compaction an append made due; null: the thread that learns does
    private val background: Executor? = null,
) : Closeable {
    constructor(
        file: File,
        format: RecordFormat,
        compactAt: Long = DEFAULT_COMPACT_AT,
        onError: (IOException) -> Unit = {},
        background: Executor? = null,
    ) : this(file, format, compactAt, onError, { FileOutputStream(it, true) }, background)

    private var out: OutputStream? = null
    // out is null before open and once a failed write stopped the appends: only the first is not open
    private var opened = false
    private var closed = false
    // the end of the last record known written whole
    private var end = 0L
    // the counts alone may outgrow compactAt: then compact again only once the log doubles
    private var compacted = 0L
    private var counts: ((ByteArray) -> Unit) -> Unit = {}
    private var pending: Pending? = null

    /** A compaction being written by [background]: the counts it was given, and the records appended to the log since. */
    private class Pending(val task: FutureTask<Unit>, val size: Long) {
        val since = ArrayList<ByteArray>()
    }

    /**
     * Hands each record of the log to [replay], and appends from now on. Once only. [counts]
     * writes what is in memory as records, the log compacted.
     *
     * A log with nothing in it (none yet, or a header alone) is first filled by [seed], from what
     * another input method learned, and written as the counts it leaves, all or nothing: until
     * that is written whole, the log stays empty and the next open seeds again. A seed that
     * cannot be written fails the open. A log compacted to no counts is not empty: what the user
     * had forgotten is not seeded back.
     */
    fun open(replay: (Byte, DataInputStream) -> Unit, counts: ((ByteArray) -> Unit) -> Unit, seed: (() -> Unit)? = null) {
        check(out == null && !closed) { "opened already" }
        this.counts = counts
        if (file.exists()) {
            val bytes = file.readBytes()
            when {
                format.hasHeader(bytes) -> {
                    var unread = 0
                    var cause: Exception? = null
                    val end = format.read(bytes, skipped = {
                        unread++
                        cause = cause ?: it
                    }) { type, input -> if (type != EMPTIED) replay(type, input) }
                    if (unread > 0) onError(IOException("$file: $unread unreadable records skipped", cause))
                    if (end < bytes.size) cut(bytes, end)
                }
                // killed before the header was all written: nothing to keep
                format.header().copyOf(bytes.size).contentEquals(bytes) -> file.writeBytes(format.header())
                else -> moveAside()
            }
        }
        val seeding = seed != null && (!file.exists() || file.length() <= RecordFormat.HEADER_SIZE)
        if (seeding) {
            seed?.invoke()
            writeCounts(seeding = true)
        }
        if (!file.exists()) file.writeBytes(format.header())
        end = file.length()
        // a seed already written as counts: no call to write them again yet
        if (seeding) compacted = end
        out = openAppend(file)
        opened = true
        // due, say, after a kill before a compaction was put in place: open runs at the first keystroke
        if (tooLong()) compactDue()
    }

    /**
     * Cuts the log back to [end], where its records stop reading whole. Anywhere but in a last
     * record cut short, that is damage: the log is first kept whole aside, and, left with no
     * record, it is marked emptied so that no seed is written over what the user had learned.
     */
    private fun cut(bytes: ByteArray, end: Int) {
        val damaged = !tornAppend(bytes, end)
        if (damaged) {
            val aside = aside()
            // on disk before the log is cut: else a power cut could lose both
            writeSynced(aside) { it.write(bytes) }
            onError(IOException("$file damaged at byte $end: kept whole as ${aside.name}"))
        }
        RandomAccessFile(file, "rw").use {
            it.setLength(end.toLong())
            if (damaged && end <= RecordFormat.HEADER_SIZE) {
                it.seek(end.toLong())
                it.write(format.record(EMPTIED) {})
            }
        }
    }

    /** Whether the records stop at [end] as a torn append leaves them: in a last record whose length, or the bytes it says, run past the end. */
    private fun tornAppend(bytes: ByteArray, end: Int): Boolean {
        val left = bytes.size - end
        if (left < 4) return true
        val length = ByteBuffer.wrap(bytes).getInt(end)
        // append writes none longer: a length past it, or none at all, is garbage
        return length in 1..RecordFormat.MAX_RECORD && left < 4 + length + 4
    }

    private fun moveAside() {
        val aside = aside()
        if (!file.renameTo(aside)) throw IOException("cannot move $file aside")
        onError(IOException("$file is not a log this reads: kept as ${aside.name}"))
    }

    // never over an earlier file kept aside: that may be a newer version's log, still wanted
    private fun aside(): File {
        var aside = File(file.path + UNREADABLE)
        var n = 1
        while (aside.exists()) aside = File(file.path + UNREADABLE + "." + n++)
        return aside
    }

    private fun tooLong() = end > maxOf(compactAt, 2 * compacted)

    /** Appends [record], unless not open, or longer than a reader would take for a record ([onError] hears of that). */
    fun append(record: ByteArray) {
        val stream = out ?: return
        // the reader would take it for garbage and drop the rest of the log with it
        if (!fits(record)) return
        try {
            stream.write(record)
            end += record.size
        } catch (e: IOException) {
            onError(e)
            recover()
            return
        }
        val compacting = pending
        if (compacting != null) {
            compacting.since += record
            if (compacting.task.isDone) finish(compacting)
        } else if (tooLong()) {
            compactDue()
        }
    }

    /** Compacts the log now due: only its counts are read here if there is a [background] to write them. */
    private fun compactDue() {
        if (background != null) compactInBackground(background) else compact()
    }

    /**
     * Whether a reader takes [record]: one longer reads as garbage, and so does the rest of the
     * log after it; one of [EMPTIED]'s type, which no format may use, [open] reads past. One that
     * does not fit is reported, and kept in memory alone.
     */
    private fun fits(record: ByteArray): Boolean {
        // the reader limits the fields alone, not the length before them and the CRC after
        if (record.size > 4 + RecordFormat.MAX_RECORD + 4) {
            onError(IOException("$file: a record of ${record.size} bytes not written, longer than a reader takes"))
            return false
        }
        // the type follows the length
        if (record.size > 4 && record[4] == EMPTIED) {
            onError(IOException("$file: a record of type $EMPTIED not written, the type that marks a log emptied"))
            return false
        }
        return true
    }

    /** After a failed write: cuts the file back to its last whole record, or stops writing. */
    private fun recover() {
        try {
            out?.close()
            RandomAccessFile(file, "rw").use { it.setLength(end) }
            out = openAppend(file)
        } catch (e: IOException) {
            onError(e)
            out = null
        }
    }

    /** Rewrites the log as the counts; not once it stopped taking writes after a failed one, as [append]. */
    internal fun compact() {
        check(opened && !closed) { "not open" }
        if (out == null) return
        // one being written in the background goes first: both would write the same file
        pending?.let { finish(it) }
        try {
            writeCounts()
        } catch (e: IOException) {
            onError(e)
            // the log as it was still holds everything; try again once it has doubled
            compacted = end
            return
        }
        reopen(compactedTo = file.length())
    }

    /**
     * As [compact], but only the counts are read here: [executor] writes and syncs them, and
     * [finish] puts them in place.
     */
    private fun compactInBackground(executor: Executor) {
        // as long as the log, which open read whole, so seldom grown; handed on as it is, not copied
        val bytes = ByteArrayOutputStream(end.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).also { countsTo(it, seeding = false) }
        val next = File(file.path + COMPACTING)
        val compacting = Pending(FutureTask(Callable { writeSynced(next) { bytes.writeTo(it) } }), bytes.size().toLong())
        pending = compacting
        try {
            executor.execute(compacting.task)
        } catch (_: RejectedExecutionException) {
            // an executor shut down must not fail the learning: written here after all
            compacting.task.run()
        }
    }

    /** Puts the counts [compacting] wrote in place of the log, with what was appended since behind them. */
    private fun finish(compacting: Pending) {
        pending = null
        val next = File(file.path + COMPACTING)
        try {
            written(compacting.task)?.let { throw it }
            appendSince(next, compacting)
            if (!next.renameTo(file)) throw IOException("cannot replace $file")
        } catch (e: IOException) {
            next.delete()
            onError(e)
            // the log as it was still holds everything, what was appended since too
            compacted = end
            return
        }
        reopen(compactedTo = compacting.size)
    }

    /** Appends to the counts [compacting] wrote in [next] what was appended to the log since. */
    private fun appendSince(next: File, compacting: Pending) {
        // one gone would be made anew, of no header nor counts, and renamed over the log
        if (next.length() != compacting.size) throw IOException("$next is not the ${compacting.size} bytes of counts written")
        FileOutputStream(next, true).use { stream -> compacting.since.forEach { stream.write(it) } }
    }

    /**
     * Waits for [task] to have written its file, running it here if no thread has started it.
     *
     * @return why it failed, or null
     */
    private fun written(task: FutureTask<Unit>): IOException? {
        task.run()
        var interrupted = false
        try {
            while (true) {
                try {
                    task.get()
                    return null
                } catch (_: InterruptedException) {
                    // its file is still being written: not to be renamed or deleted under it
                    interrupted = true
                }
            }
        } catch (e: ExecutionException) {
            return e.cause as? IOException ?: IOException(e.cause)
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    /** Appends from the end of the log just renamed over the old one, of which [compactedTo] bytes are counts. */
    private fun reopen(compactedTo: Long) {
        // the old stream writes to the file renamed over, now unlinked
        try {
            out?.close()
        } catch (e: IOException) {
            onError(e)
        }
        end = file.length()
        compacted = compactedTo
        out = try {
            openAppend(file)
        } catch (e: IOException) {
            onError(e)
            null
        }
    }

    /** Writes the counts into a new file, renamed over [file] once it is all on disk. */
    private fun writeCounts(seeding: Boolean = false) {
        replaceFile(file, File(file.path + COMPACTING)) { next ->
            FileOutputStream(next).use { raw ->
                val stream = BufferedOutputStream(raw)
                countsTo(stream, seeding)
                stream.flush()
            }
        }
    }

    /** Writes the log compacted to [stream]: the header, then the counts. */
    private fun countsTo(stream: OutputStream, seeding: Boolean) {
        stream.write(format.header())
        var none = true
        counts {
            // one a reader would not take would cost every count after it
            if (fits(it)) {
                stream.write(it)
                none = false
            }
        }
        // a header alone is seeded again: meant for a seed that found nothing, not for all forgotten
        if (none && !seeding) stream.write(format.record(EMPTIED) {})
    }

    /** Writes to [to] what [write] puts in its stream, and syncs it, or leaves no [to] at all. */
    private fun writeSynced(to: File, write: (OutputStream) -> Unit) {
        try {
            FileOutputStream(to).use {
                write(it)
                it.fd.sync()
            }
        } catch (e: IOException) {
            // a copy aside cut short (the disk full) is no copy, and each start would leave another
            to.delete()
            throw e
        }
    }

    override fun close() {
        closed = true
        // counts written in the background are not to be written again at the next open
        pending?.let { finish(it) }
        out?.close()
        out = null
    }

    companion object {
        const val DEFAULT_COMPACT_AT = 1L shl 20
        const val UNREADABLE = ".unreadable"
        private const val COMPACTING = ".compacting"
        // of no fields, and a type no format's records have: they count from 1, and skip types they do not know
        private const val EMPTIED: Byte = 0
    }
}
