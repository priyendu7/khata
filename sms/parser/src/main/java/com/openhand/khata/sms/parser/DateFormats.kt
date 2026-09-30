package com.openhand.khata.sms.parser

import java.time.DateTimeException
import java.time.LocalDate
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/** Date formats for a marked date in [RuleMaker]: the common ones, and the regex for each. */
object DateFormats {
    /** Common SMS date formats, offered when the user marks a date. */
    val COMMON = listOf(
        "dd-MM-yy",
        "dd-MM-yyyy",
        "dd/MM/yy",
        "dd/MM/yyyy",
        "dd-MMM-yy",
        "dd-MMM-yyyy",
        "dd MMM yyyy",
        "ddMMMyy",
        "yyyy-MM-dd",
        "dd-MM-yyyy HH:mm:ss",
        "dd-MMM-yy HH:mm",
        "yyyy-MM-dd HH:mm:ss"
    )

    private const val SHORT_NAME = 3
    private const val TWO_DIGITS = 2

    /** The [COMMON] formats that read [text], best guess first. */
    fun matching(text: String): List<String> {
        val trimmed = trimEdges(text)
        return COMMON.filter { reads(it, trimmed) }
    }

    /** Whether [format] reads [text], the same way [SmsParser] reads a date. */
    fun reads(format: String, text: String): Boolean = try {
        val formatter = DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern(format)
            .toFormatter(Locale.ENGLISH)
        LocalDate.from(formatter.parse(text))
        true
    } catch (_: DateTimeException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    /**
     * A regex for the text a `java.time` [format] reads: `dd-MMM-yy` → `\d{2}-[a-z]{3}-\d{2}`.
     * Null for a format with letters it doesn't know.
     */
    fun regex(format: String): String? {
        val out = StringBuilder()
        var i = 0
        var known = true
        while (known && i < format.length) {
            val c = format[i]
            val quoteEnd = if (c == '\'') format.indexOf('\'', i + 1) else -1
            var run = 1
            while (c.isLetter() && format.getOrNull(i + run) == c) run++
            val part = when {
                c == '\'' -> format.substring(i + 1, quoteEnd.coerceAtLeast(i + 1))
                    .takeIf { quoteEnd > 0 }
                    ?.let(PatternParts::literal)
                c.isLetter() -> field(c, run)
                else -> PatternParts.literal(c.toString())
            }
            known = part != null
            out.append(part.orEmpty())
            i = if (c == '\'') quoteEnd + 1 else i + run
        }
        return out.toString().takeIf { known }
    }

    private fun field(letter: Char, count: Int): String? = when (letter) {
        'd', 'M', 'H', 'h', 'm', 's' -> when {
            letter == 'M' && count == SHORT_NAME -> "[a-z]{3}"
            letter == 'M' && count > SHORT_NAME -> "[a-z]+"
            count == 1 -> "\\d{1,2}"
            else -> "\\d{$count}"
        }
        'y', 'u' -> if (count == TWO_DIGITS) "\\d{2}" else "\\d{4}"
        'E' -> if (count > SHORT_NAME) "[a-z]+" else "[a-z]{3}"
        'a' -> "[ap]m"
        else -> null
    }
}
