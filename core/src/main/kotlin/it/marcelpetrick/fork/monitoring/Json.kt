// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork.monitoring

/**
 * Minimal JSON reader for the session-log format (objects, arrays, numbers, strings,
 * booleans, null). Pure Kotlin so the app, tests and replay tool share it on any JVM.
 * Numbers become Double; objects keep key order.
 */
object Json {
    fun parse(text: String): Any? = Reader(text).run { value().also { end() } }

    fun quote(value: String): String =
        buildString {
            append('"')
            for (c in value) {
                when {
                    c == '"' -> append("\\\"")
                    c == '\\' -> append("\\\\")
                    c < ' ' -> append("\\u%04x".format(c.code))
                    else -> append(c)
                }
            }
            append('"')
        }

    private class Reader(
        private val text: String,
    ) {
        private var at = 0

        fun end() {
            space()
            require(at == text.length) { "Unexpected trailing content at $at" }
        }

        fun value(): Any? {
            space()
            require(at < text.length) { "Unexpected end of JSON" }
            return when (text[at]) {
                '{' -> obj()
                '[' -> array()
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> number()
            }
        }

        private fun obj(): Map<String, Any?> {
            val result = LinkedHashMap<String, Any?>()
            at++
            space()
            if (peek('}')) return result
            do {
                space()
                val key = string()
                space()
                expect(':')
                result[key] = value()
                space()
            } while (peek(','))
            expect('}')
            return result
        }

        private fun array(): List<Any?> {
            val result = ArrayList<Any?>()
            at++
            space()
            if (peek(']')) return result
            do {
                result += value()
                space()
            } while (peek(','))
            expect(']')
            return result
        }

        private fun string(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                require(at < text.length) { "Unterminated string" }
                val c = text[at++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        val escaped = text[at++]
                        out.append(
                            when (escaped) {
                                'n' -> '\n'
                                't' -> '\t'
                                'r' -> '\r'
                                'b' -> '\b'
                                'f' -> '\u000c'
                                'u' ->
                                    text
                                        .substring(at, at + UNICODE_DIGITS)
                                        .toInt(HEX)
                                        .toChar()
                                        .also { at += UNICODE_DIGITS }
                                else -> escaped
                            },
                        )
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun number(): Double {
            val start = at
            while (at < text.length && text[at] in "+-0123456789.eE") at++
            return text.substring(start, at).toDoubleOrNull() ?: throw IllegalArgumentException("Invalid value at $start")
        }

        private fun literal(
            word: String,
            result: Any?,
        ): Any? {
            require(text.startsWith(word, at)) { "Invalid literal at $at" }
            at += word.length
            return result
        }

        private fun space() {
            while (at < text.length && text[at].isWhitespace()) at++
        }

        /** Consumes [c] if it is next. */
        private fun peek(c: Char): Boolean = (at < text.length && text[at] == c).also { if (it) at++ }

        private fun expect(c: Char) {
            require(at < text.length && text[at] == c) { "Expected '$c' at $at" }
            at++
        }
    }
}

private const val UNICODE_DIGITS = 4
private const val HEX = 16
