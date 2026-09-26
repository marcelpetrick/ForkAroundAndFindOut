// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork.monitoring

import java.io.File
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPOutputStream

/**
 * Writes one training session as gzip JSON Lines ([SessionLog]) into app-private storage.
 * Lines are sync-flushed regularly, so a killed app leaves a readable log. Recording
 * stops, and [full] becomes true, once all logs together would exceed [limitBytes].
 */
class SessionRecorder(
    private val directory: File,
    session: String,
    app: String,
    settings: Settings,
    private val limitBytes: Long = LIMIT_BYTES,
    now: Date = Date(),
) : AutoCloseable {
    val file: File
    private val writer: Writer
    private var pending = 0
    var full = false
        private set
    var closed = false
        private set

    init {
        directory.mkdirs()
        check(SessionFiles.totalBytes(directory) < limitBytes) { "Session log storage is full. Export and delete logs first." }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(now)
        file = File(directory, "$stamp-${session.take(ID_PREFIX)}.jsonl.gz")
        writer = GZIPOutputStream(file.outputStream(), true).bufferedWriter()
        write(SessionLog.header(session, app, settings), flush = true)
    }

    fun frame(line: String) = write(line, flush = ++pending >= FLUSH_EVERY)

    fun label(line: String) = write(line, flush = true)

    private fun write(
        line: String,
        flush: Boolean,
    ) {
        if (closed || full) return
        writer.write(line)
        writer.write("\n")
        if (flush) {
            writer.flush()
            pending = 0
            if (SessionFiles.totalBytes(directory) >= limitBytes) full = true
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        writer.close()
    }

    companion object {
        const val LIMIT_BYTES = 100L * 1024 * 1024
        const val FLUSH_EVERY = 30
    }
}

/** Listing and deletion of recorded session logs. */
object SessionFiles {
    fun list(directory: File): List<File> =
        directory.listFiles { file -> file.name.endsWith(".jsonl.gz") }?.sortedByDescending { it.name } ?: emptyList()

    fun totalBytes(directory: File): Long = list(directory).sumOf { it.length() }

    fun deleteAll(directory: File) {
        list(directory).forEach { it.delete() }
    }
}

/** Characters of the session id kept in a log file name. */
private const val ID_PREFIX = 8
