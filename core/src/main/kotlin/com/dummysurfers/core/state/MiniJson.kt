package com.dummysurfers.core.state

/**
 * Minimal JSON writer/parser for the fixed save schema (no reflection,
 * no kotlinx dependency — deterministic and allocation-light).
 */
object MiniJson {
    fun esc(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (c in s) when (c) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (c < ' ') sb.append(' ') else sb.append(c)
        }
        return sb.toString()
    }

    fun str(s: String) = "\"${esc(s)}\""

    // ── Tiny recursive-descent parser ──────────────────────────────────
    class Parser(private val s: String) {
        private var i = 0
        fun parse(): Any? {
            val v = value()
            skipWs()
            return v
        }

        private fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun value(): Any? {
            skipWs()
            if (i >= s.length) return null
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> string()
                't' -> { i += 4; true }
                'f' -> { i += 5; false }
                'n' -> { i += 4; null }
                else -> number()
            }
        }

        private fun obj(): HashMap<String, Any?> {
            val m = HashMap<String, Any?>()
            i++ // {
            skipWs()
            if (i < s.length && s[i] == '}') { i++; return m }
            while (true) {
                skipWs()
                val k = string()
                skipWs()
                i++ // :
                m[k] = value()
                skipWs()
                if (i < s.length && s[i] == ',') { i++; continue }
                i++ // }
                return m
            }
        }

        private fun arr(): ArrayList<Any?> {
            val a = ArrayList<Any?>()
            i++ // [
            skipWs()
            if (i < s.length && s[i] == ']') { i++; return a }
            while (true) {
                a.add(value())
                skipWs()
                if (i < s.length && s[i] == ',') { i++; continue }
                i++ // ]
                return a
            }
        }

        private fun string(): String {
            i++ // "
            val sb = StringBuilder()
            while (s[i] != '"') {
                if (s[i] == '\\') {
                    i++
                    when (s[i]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        else -> sb.append(s[i])
                    }
                } else sb.append(s[i])
                i++
            }
            i++ // "
            return sb.toString()
        }

        private fun number(): Float {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '-' || s[i] == '+' || s[i] == '.' || s[i] == 'e' || s[i] == 'E')) i++
            return s.substring(start, i).toFloatOrNull() ?: 0f
        }
    }

    fun parse(s: String): Any? = try { Parser(s).parse() } catch (_: Exception) { null }
}
