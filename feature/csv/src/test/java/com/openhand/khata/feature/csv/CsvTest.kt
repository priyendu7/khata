package com.openhand.khata.feature.csv

import org.junit.Assert.assertEquals
import org.junit.Test

/** RFC 4180 writing and reading, including what spreadsheets and phones do to files. */
class CsvTest {
    @Test
    fun quotesOnlyFieldsThatNeedIt() {
        val text = Csv.write(listOf(listOf("plain", "a,b", "say \"hi\"", "two\nlines", "")))
        assertEquals("plain,\"a,b\",\"say \"\"hi\"\"\",\"two\nlines\",\r\n", text)
    }

    @Test
    fun readsBackWhatItWrites() {
        val rows = listOf(
            listOf("date", "note"),
            listOf("2026-09-27", "चाय, समोसा"),
            listOf("2026-09-28", "line one\r\nline two"),
            listOf("", "\"quoted\""),
            listOf("only", "")
        )
        assertEquals(rows, Csv.parse(Csv.write(rows)))
    }

    @Test
    fun acceptsBomLfEndingsAndNoFinalLineBreak() {
        val rows = Csv.parse("${Csv.BOM}a,b\nc,d")
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), rows)
    }

    @Test
    fun readsCrlfAndSkipsBlankLines() {
        val rows = Csv.parse("a,b\r\n\r\nc,\r\n")
        assertEquals(listOf(listOf("a", "b"), listOf("c", "")), rows)
    }

    @Test
    fun keepsEmptyFieldsInTheMiddleAndAtTheEnd() {
        assertEquals(listOf(listOf("", "x", "", "")), Csv.parse(",x,,\r\n"))
    }

    @Test
    fun unclosedQuoteEndsAtEndOfText() {
        assertEquals(listOf(listOf("a", "open\nfield")), Csv.parse("a,\"open\nfield"))
    }
}
