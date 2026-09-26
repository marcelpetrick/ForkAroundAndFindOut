// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork.monitoring

import it.marcelpetrick.fork.detection.table
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Date
import java.util.zip.GZIPInputStream

class SessionRecorderTest {
    @get:Rule val folder = TemporaryFolder()

    private fun lines(recorder: SessionRecorder) = GZIPInputStream(recorder.file.inputStream()).bufferedReader().readLines()

    @Test
    fun writesReadableGzipLinesFlushesAndStopsWhenStorageIsFull() {
        val directory = folder.root.resolve("sessions")
        val settings = Settings(table = table)
        val recorder = SessionRecorder(directory, "0123456789abcdef", "1.0", settings, now = Date(0))
        assertTrue(recorder.file.name.endsWith("-01234567.jsonl.gz"))
        repeat(SessionRecorder.FLUSH_EVERY) { recorder.frame("""{"type":"frame","t":$it,"aspect":1,"poses":[],"arms":[]}""") }
        recorder.label(SessionLog.label(5, "BOTH", 1))
        recorder.close()
        recorder.close()
        recorder.frame("ignored after close")
        val written = lines(recorder)
        assertEquals(SessionRecorder.FLUSH_EVERY + 2, written.size)
        assertEquals(1, SessionLog.read(written.asSequence()).labels.size)
        assertEquals(listOf(recorder.file), SessionFiles.list(directory))
        assertEquals(recorder.file.length(), SessionFiles.totalBytes(directory))

        // Room for the header only: the recorder reports itself full and drops further lines.
        val tiny = SessionRecorder(directory, "second", "1.0", settings, limitBytes = recorder.file.length() + 1)
        assertTrue(tiny.full)
        tiny.label(SessionLog.label(1, "NORMAL", 1))
        tiny.close()
        assertEquals(1, lines(tiny).size)
        assertThrows(IllegalStateException::class.java) { SessionRecorder(directory, "third", "1.0", settings, limitBytes = 10) }

        SessionFiles.deleteAll(directory)
        assertTrue(SessionFiles.list(directory).isEmpty())
        assertTrue(SessionFiles.list(folder.root.resolve("missing")).isEmpty())
        assertFalse(recorder.file.exists())
    }
}
