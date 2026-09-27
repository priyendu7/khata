package com.openhand.khata.feature.csv

/**
 * Plain RFC 4180 CSV, small enough not to need a library: fields with a comma, quote or line
 * break are quoted, and a quote inside one is doubled. Reading also accepts LF line endings, a
 * UTF-8 byte order mark and a missing line break at the end.
 */
object Csv {
    /** Written first so Excel reads the file as UTF-8 (Hindi text is garbled without it). */
    const val BOM = '\uFEFF'
    private const val QUOTE = '"'
    private const val CRLF = "\r\n"

    fun write(rows: List<List<String>>): String = buildString {
        rows.forEach { row ->
            row.forEachIndexed { index, field ->
                if (index > 0) append(',')
                append(quoted(field))
            }
            append(CRLF)
        }
    }

    private fun quoted(field: String): String =
        if (field.any { it in SPECIAL }) QUOTE + field.replace("\"", "\"\"") + QUOTE else field

    private val SPECIAL = setOf(',', QUOTE, '\r', '\n')

    /**
     * Every record in [text], each a list of fields. Blank lines are skipped. A quote left open
     * at the end of the text closes there rather than failing the whole file.
     */
    fun parse(text: String): List<List<String>> = Reader(text.removePrefix(BOM.toString())).read()

    /** Reads one character at a time, inside or outside a quoted field. */
    private class Reader(private val text: String) {
        private val rows = mutableListOf<List<String>>()
        private val row = mutableListOf<String>()
        private val field = StringBuilder()
        private var quoted = false
        private var index = 0

        fun read(): List<List<String>> {
            while (index < text.length) {
                if (quoted) readQuoted(text[index]) else readPlain(text[index])
                index++
            }
            if (field.isNotEmpty() || row.isNotEmpty()) endRow()
            return rows
        }

        private fun readQuoted(char: Char) {
            when {
                char != QUOTE -> field.append(char)
                next() == QUOTE -> {
                    field.append(QUOTE)
                    index++
                }
                else -> quoted = false
            }
        }

        private fun readPlain(char: Char) {
            when {
                char == QUOTE && field.isEmpty() -> quoted = true
                char == ',' -> endField()
                char == '\r' && next() == '\n' -> Unit
                char == '\r' || char == '\n' -> endRow()
                else -> field.append(char)
            }
        }

        private fun next() = text.getOrNull(index + 1)

        private fun endField() {
            row += field.toString()
            field.clear()
        }

        private fun endRow() {
            endField()
            if (row.size > 1 || row[0].isNotEmpty()) rows += row.toList()
            row.clear()
        }
    }
}
