// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork.tools

import it.marcelpetrick.fork.demo.SyntheticDemo
import it.marcelpetrick.fork.monitoring.Label
import it.marcelpetrick.fork.monitoring.Recording
import it.marcelpetrick.fork.monitoring.Replay
import it.marcelpetrick.fork.monitoring.SessionLog
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.PrintStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.system.exitProcess

const val USAGE = """Usage:
  replay [--trigger-ms N] [--clear-ms N] [--cooldown-ms N] [--hold-ms N] [--window-ms N] SESSION.jsonl[.gz]...
      Re-run the detector over recorded sessions and print reminders, reminders near
      FALSE_ALARM/NORMAL labels, detected positive labels, UNKNOWN fraction and
      live/replay agreement per session and in total. Split train/validation/test by
      passing whole session files, never frames (vision §22).
  labels SESSION.jsonl[.gz]...
      Count the labels of each kind per session and in total, and in how many sessions
      each kind occurs: a whole-session train/validation/test split needs every kind in
      at least three sessions before any learned classifier is worth training.
  demo-log OUT.jsonl.gz [LOOPS]
      Write the labelled synthetic demo session (no real data) for trying the tools."""

fun main(args: Array<String>) {
    exitProcess(run(args.toList(), System.out, System.err))
}

fun run(
    args: List<String>,
    out: PrintStream,
    err: PrintStream,
): Int =
    try {
        when (args.firstOrNull()) {
            "replay" -> {
                replay(args.drop(1), out)
            }

            "labels" -> {
                labels(args.drop(1), out)
            }

            "demo-log" -> {
                demoLog(args.drop(1), out)
            }

            else -> {
                err.println(USAGE)
                2
            }
        }
    } catch (error: IllegalArgumentException) {
        err.println("Error: ${error.message}")
        err.println(USAGE)
        2
    }

private fun replay(
    args: List<String>,
    out: PrintStream,
): Int {
    val options = mutableMapOf<String, Long>()
    val files = mutableListOf<File>()
    var i = 0
    while (i < args.size) {
        val argument = args[i]
        if (argument.startsWith("--")) {
            require(i + 1 < args.size) { "$argument needs a value" }
            options[argument] = requireNotNull(args[i + 1].toLongOrNull()) { "$argument needs a number" }
            i += 2
        } else {
            files += File(argument)
            i++
        }
    }
    require(
        options.keys.all {
            it in setOf("--trigger-ms", "--clear-ms", "--cooldown-ms", "--hold-ms", "--window-ms")
        },
    ) { "Unknown option in ${options.keys}" }
    require(files.isNotEmpty()) { "No session files given" }
    var reminders = 0
    var near = 0
    var positives = 0
    var detected = 0
    for (file in files) {
        val recording = load(file)
        val base = recording.settings.timing
        val timing =
            base.copy(
                triggerMs = options["--trigger-ms"] ?: base.triggerMs,
                clearMs = options["--clear-ms"] ?: base.clearMs,
                cooldownMs = options["--cooldown-ms"] ?: base.cooldownMs,
                holdMs = options["--hold-ms"] ?: base.holdMs,
            )
        val report = Replay.run(recording, timing, options["--window-ms"] ?: DEFAULT_WINDOW_MS)
        out.println(report.describe())
        reminders += report.reminders
        near += report.remindersNearNegativeLabels
        positives += report.positiveLabels
        detected += report.positiveDetected
    }
    out.println(
        "TOTAL: ${files.size} sessions, $reminders reminders ($near near FALSE_ALARM/NORMAL labels), " +
            "positive labels detected $detected/$positives",
    )
    return 0
}

private fun labels(
    args: List<String>,
    out: PrintStream,
): Int {
    require(args.isNotEmpty()) { "No session files given" }
    val counts = args.map { path -> load(File(path)).let { it.session to it.labels.groupingBy(Label::label).eachCount() } }
    val kinds = SessionLog.LABELS + counts.flatMap { it.second.keys }.distinct().filterNot { it in SessionLog.LABELS }

    fun line(count: (String) -> String) = kinds.joinToString(", ") { "$it ${count(it)}" }
    for ((session, perKind) in counts) {
        out.println("$session: ${perKind.values.sum()} labels — ${line { (perKind[it] ?: 0).toString() }}")
    }
    val sessions = kinds.associateWith { kind -> counts.count { (it.second[kind] ?: 0) > 0 } }
    val total = counts.sumOf { it.second.values.sum() }
    out.println(
        "TOTAL: ${counts.size} sessions, $total labels — " +
            line { kind -> "${counts.sumOf { it.second[kind] ?: 0 }} in ${sessions.getValue(kind)} sessions" },
    )
    val thin = kinds.filter { sessions.getValue(it) < SPLIT_SESSIONS }
    if (thin.isNotEmpty()) {
        out.println("Fewer than $SPLIT_SESSIONS sessions (too few for a whole-session split): ${thin.joinToString(", ")}")
    }
    return 0
}

private fun demoLog(
    args: List<String>,
    out: PrintStream,
): Int {
    val target = File(requireNotNull(args.firstOrNull()) { "demo-log needs an output file" })
    val loops = args.getOrNull(1)?.toIntOrNull() ?: DEFAULT_LOOPS
    GZIPOutputStream(target.outputStream()).bufferedWriter().use { writer ->
        SyntheticDemo.sessionLog(loops).forEach { writer.appendLine(it) }
    }
    out.println("Wrote ${target.path} ($loops synthetic loops)")
    return 0
}

/** Copies everything readable; a truncated gzip trailer ends the copy with what was flushed. */
private fun copyUntilTruncated(
    input: InputStream,
    output: ByteArrayOutputStream,
) {
    val buffer = ByteArray(BUFFER_BYTES)
    try {
        var count = input.read(buffer)
        while (count >= 0) {
            output.write(buffer, 0, count)
            count = input.read(buffer)
        }
    } catch (_: EOFException) {
        // Truncated gzip trailer: keep what was flushed.
    }
}

/** Reads plain or gzip logs; a gzip stream cut off by a killed app yields its complete lines. */
fun load(file: File): Recording {
    require(file.isFile) { "Not a file: ${file.path}" }
    val input: InputStream = if (file.name.endsWith(".gz")) GZIPInputStream(file.inputStream()) else file.inputStream()
    // Copy raw bytes first: a character reader would discard bytes it had read ahead
    // when the missing gzip trailer raises EOFException.
    val bytes = ByteArrayOutputStream()
    input.use { copyUntilTruncated(it, bytes) }
    val lines = bytes.toString(Charsets.UTF_8).lines()
    return SessionLog.read(lines.asSequence())
}

/** Labels this close to a reminder count as "near" it. */
private const val DEFAULT_WINDOW_MS = 5_000L
private const val DEFAULT_LOOPS = 3
private const val BUFFER_BYTES = 64 * 1024

/** Train, validation and test each need at least one session with the label. */
private const val SPLIT_SESSIONS = 3
