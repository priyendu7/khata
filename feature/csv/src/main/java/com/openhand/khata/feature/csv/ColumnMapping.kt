package com.openhand.khata.feature.csv

import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.model.TransactionRecord
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.util.Locale
import kotlin.math.abs

/** How a CSV from another app writes dates; [example] is 27 September 2026 in that format. */
enum class DateFormat(pattern: String, val example: String) {
    DAY_MONTH_YEAR("d/M/uuuu", "27/09/2026"),
    MONTH_DAY_YEAR("M/d/uuuu", "09/27/2026"),
    YEAR_MONTH_DAY("uuuu/M/d", "2026-09-27"),
    DAY_MONTH_NAME_YEAR("d/MMM/uuuu", "27-Sep-2026"),
    DAY_MONTH_SHORT_YEAR("d/M/uu", "27/09/26");

    private val formatter: DateTimeFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern(pattern)
        .toFormatter(Locale.ENGLISH)
        .withResolverStyle(ResolverStyle.STRICT)

    /**
     * The date in [text], or null. Any of `/ - .` or spaces may separate the parts, and a time
     * after the date (as in `27/09/2026 14:30`) is ignored.
     */
    fun parse(text: String): LocalDate? {
        val date = text.trim().replace(TIME_AFTER, "").replace(SEPARATORS, "/")
        return parseOrNull { LocalDate.parse(date, formatter) }
    }

    private companion object {
        val TIME_AFTER = Regex("""[\sT]+\d{1,2}:\d{2}.*$""")
        val SEPARATORS = Regex("""[\s./-]+""")
    }
}

/** How a CSV from another app tells spending from income. */
enum class AmountStyle {
    /** One amount column: negative is spending, positive is income. */
    SIGNED,

    /** One amount column, and every row is spending (a hand-kept expense sheet). */
    ALL_SPENDING,

    /** A spending (debit) column and an income (credit) column. */
    SEPARATE_COLUMNS
}

/**
 * Which column holds what, in a CSV from another app (PRD feature 6: date, amount, description,
 * category). Columns are indexes into the header row; null means "not in this file".
 */
data class ColumnMapping(
    val date: Int? = null,
    val amount: Int? = null,
    val debit: Int? = null,
    val credit: Int? = null,
    val description: Int? = null,
    val category: Int? = null,
    val dateFormat: DateFormat = DateFormat.DAY_MONTH_YEAR,
    val amountStyle: AmountStyle = AmountStyle.SIGNED
) {
    /** Whether there's enough to read a transaction: a date and an amount. */
    val isComplete: Boolean
        get() = date != null &&
            if (amountStyle == AmountStyle.SEPARATE_COLUMNS) {
                debit != null || credit != null
            } else {
                amount != null
            }

    /** Reads every row after the header. The description becomes the note. */
    fun parse(rows: List<List<String>>, zone: ZoneId): List<ParsedRow> =
        rows.drop(1).mapIndexed { index, fields ->
            parsedRow(line = index + 2) { record(fields, zone) }
        }

    private fun record(fields: List<String>, zone: ZoneId): TransactionRecord {
        val cell = { column: Int? -> column?.let { fields.getOrNull(it) }?.trim().orEmpty() }
        val day = dateFormat.parse(cell(date)).orFail(RowProblem.DATE)
        val (direction, paise) = when (amountStyle) {
            AmountStyle.SIGNED -> signed(cell(amount)).let {
                (if (it < 0) Direction.DEBIT else Direction.CREDIT) to it
            }
            AmountStyle.ALL_SPENDING -> Direction.DEBIT to signed(cell(amount))
            AmountStyle.SEPARATE_COLUMNS -> {
                // Banks often write 0.00 or nothing in the column that doesn't apply.
                val spent = cell(debit).ifEmpty { null }?.let(::signed) ?: 0
                val income = cell(credit).ifEmpty { null }?.let(::signed) ?: 0
                if (spent != 0L) Direction.DEBIT to spent else Direction.CREDIT to income
            }
        }
        if (paise == 0L) throw RowError(RowProblem.AMOUNT)
        return TransactionRecord(
            timestamp = day.atStartOfDay(zone).toInstant().toEpochMilli(),
            amountPaise = abs(paise),
            direction = direction,
            category = cell(category).ifEmpty { null },
            note = cell(description).ifEmpty { null }
        )
    }

    companion object {
        private const val SAMPLE_ROWS = 20

        /**
         * A first guess from the header's names (English, as banks and spreadsheets write them)
         * and the first rows' dates, for the user to check.
         */
        fun guess(rows: List<List<String>>): ColumnMapping {
            val header = rows.firstOrNull().orEmpty().map { it.trim().lowercase() }
            fun find(vararg words: String) =
                header.indexOfFirst { name -> words.any { name.contains(it) } }.takeIf { it >= 0 }
            val date = find("date")
            val debit = find("debit", "withdrawal", "spent", "expense")
            val credit = find("credit", "deposit", "income")
            // Bank statements name these "Withdrawal Amt." and "Deposit Amt.", so they come first.
            val separate = debit != null || credit != null
            val amount = if (separate) null else find("amount", "amt")
            val samples = date?.let { column ->
                rows.drop(1).mapNotNull { it.getOrNull(column)?.trim()?.ifEmpty { null } }
                    .take(SAMPLE_ROWS)
            }.orEmpty()
            return ColumnMapping(
                date = date,
                amount = amount,
                debit = debit,
                credit = credit,
                description = find(
                    "description",
                    "narration",
                    "particulars",
                    "details",
                    "remark",
                    "note",
                    "payee"
                ),
                category = find("category"),
                dateFormat = DateFormat.entries.firstOrNull { format ->
                    samples.isNotEmpty() && samples.all { format.parse(it) != null }
                } ?: DateFormat.DAY_MONTH_YEAR,
                amountStyle = if (separate) AmountStyle.SEPARATE_COLUMNS else AmountStyle.SIGNED
            )
        }

        /**
         * A signed amount in paise, from text such as `-250`, `(250.00)`, `₹1,250.50`,
         * `Rs. 99` or `1,000.00 Dr`. Blank or anything else that isn't an amount fails the row.
         */
        internal fun signed(text: String): Long {
            var clean = text.trim().replace(CURRENCY, "").trim()
            var negative = false
            if (clean.startsWith("(") && clean.endsWith(")")) {
                negative = true
                clean = clean.substring(1, clean.length - 1).trim()
            }
            SUFFIX.find(clean)?.let { match ->
                negative = negative || match.groupValues[1].equals("dr", ignoreCase = true)
                clean = clean.substring(0, match.range.first).trim()
            }
            if (clean.startsWith("-") || clean.endsWith("-")) {
                negative = true
                clean = clean.trim('-').trim()
            }
            clean = clean.removePrefix("+").trim()
            val paise = Money.parsePaise(clean).orFail(RowProblem.AMOUNT)
            return if (negative) -paise else paise
        }

        private val CURRENCY = Regex("""₹|(?i)\b(?:rs\.?|inr)(?=\s|\d|$)""")
        private val SUFFIX = Regex("""\s*(dr|cr)\.?$""", RegexOption.IGNORE_CASE)
    }
}
