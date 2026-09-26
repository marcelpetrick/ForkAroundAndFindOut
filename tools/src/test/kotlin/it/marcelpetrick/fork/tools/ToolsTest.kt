// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork.tools

import it.marcelpetrick.fork.demo.SyntheticDemo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.zip.GZIPOutputStream

class ToolsTest {
    @get:Rule val folder = TemporaryFolder()

    private fun call(vararg args: String): Triple<Int, String, String> {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val code = run(args.toList(), PrintStream(out), PrintStream(err))
        return Triple(code, out.toString(), err.toString())
    }

    @Test
    fun demoLogReplaysWithDefaultAndTunedTiming() {
        val log = folder.root.resolve("demo.jsonl.gz").path
        assertEquals(0, call("demo-log", log, "2").first)
        val (code, out, _) = call("replay", log)
        assertEquals(0, code)
        assertTrue(out, out.contains("2 reminders (0 near FALSE_ALARM/NORMAL labels), positive labels detected 2/2"))
        assertTrue(out, out.contains("live/replay agreement 100.0%"))
        assertTrue(out, out.contains("TOTAL: 1 sessions, 2 reminders"))
        val tuned =
            call(
                "replay",
                "--trigger-ms",
                "10000",
                "--clear-ms",
                "250",
                "--cooldown-ms",
                "0",
                "--hold-ms",
                "300",
                "--window-ms",
                "2000",
                log,
                log,
            ).second
        assertTrue(tuned, tuned.contains("TOTAL: 2 sessions, 0 reminders"))
        assertTrue(call("demo-log", folder.root.resolve("default.jsonl.gz").path).second.contains("3 synthetic loops"))
    }

    @Test
    fun plainAndTruncatedGzipLogsAreRead() {
        val lines = SyntheticDemo.sessionLog(loops = 1)
        val plain = folder.root.resolve("plain.jsonl").apply { writeText(lines.joinToString("\n")) }
        assertEquals(2, load(plain).labels.size) // NORMAL and LEFT in one loop
        // A phone killed mid-meal leaves a sync-flushed gzip stream without its trailer.
        val bytes = ByteArrayOutputStream()
        val gzip = GZIPOutputStream(bytes, true)
        gzip.write(lines.joinToString("\n", postfix = "\n").toByteArray())
        gzip.flush()
        val cut = folder.root.resolve("cut.jsonl.gz").apply { writeBytes(bytes.toByteArray()) }
        assertEquals(lines.size - 1 - 2, load(cut).frames.size) // header and labels excluded
    }

    @Test
    fun invalidInvocationsExplainUsage() {
        for (args in listOf(
            arrayOf(),
            arrayOf("nonsense"),
            arrayOf("replay"),
            arrayOf("replay", "--trigger-ms"),
            arrayOf("replay", "--trigger-ms", "soon", "x"),
            arrayOf("replay", "--speed", "3", "x"),
            arrayOf("replay", folder.root.resolve("missing.jsonl").path),
            arrayOf("demo-log"),
        )) {
            val (code, _, err) = call(*args)
            assertEquals(args.joinToString(), 2, code)
            assertTrue(err.contains("Usage:"))
        }
    }
}
