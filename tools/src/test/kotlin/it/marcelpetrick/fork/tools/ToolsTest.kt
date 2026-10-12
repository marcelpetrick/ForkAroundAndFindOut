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
    fun labelsCountKindsPerSessionAndAcrossSessions() {
        val demo = SyntheticDemo.sessionLog(loops = 2).joinToString("\n")

        fun log(
            name: String,
            text: String,
        ) = folder.root
            .resolve(name)
            .apply { writeText(text) }
            .path
        val first = log("a.jsonl", demo.replace("\"synthetic-demo\"", "\"meal-1\""))
        val second = log("b.jsonl", demo.replace("\"synthetic-demo\"", "\"meal-2\""))
        val (code, out, _) = call("labels", first, second)
        assertEquals(0, code)
        // Two loops label NORMAL and LEFT once each.
        assertTrue(out, out.contains("meal-1: 4 labels — NORMAL 2, LEFT 2, RIGHT 0, BOTH 0, FALSE_ALARM 0, MISSED_VIOLATION 0"))
        assertTrue(out, out.contains("TOTAL: 2 sessions, 8 labels — NORMAL 4 in 2 sessions, LEFT 4 in 2 sessions, RIGHT 0 in 0 sessions"))
        assertTrue(out, out.contains("Fewer than 3 sessions (too few for a whole-session split): NORMAL, LEFT, RIGHT, BOTH"))
        // Two logs of meal-2 are one session; an unknown label kind is listed after the known ones.
        val third = log("c.jsonl", demo.replace("\"synthetic-demo\"", "\"meal-3\"").replace("\"LEFT\"", "\"SNACK\""))
        val more = call("labels", first, second, second, third).second
        assertTrue(more, more.contains("meal-2: 8 labels — NORMAL 4, LEFT 4"))
        assertTrue(more, more.contains("meal-3: 4 labels — NORMAL 2, LEFT 0, RIGHT 0, BOTH 0, FALSE_ALARM 0, MISSED_VIOLATION 0, SNACK 2"))
        assertTrue(more, more.contains("TOTAL: 3 sessions, 16 labels — NORMAL 8 in 3 sessions, LEFT 6 in 2 sessions"))
        assertEquals(
            "Fewer than 3 sessions (too few for a whole-session split): LEFT, RIGHT, BOTH, FALSE_ALARM, MISSED_VIOLATION, SNACK",
            more.trimEnd().lines().last(),
        )
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
            arrayOf("labels"),
            arrayOf("demo-log"),
        )) {
            val (code, _, err) = call(*args)
            assertEquals(args.joinToString(), 2, code)
            assertTrue(err.contains("Usage:"))
        }
    }
}
