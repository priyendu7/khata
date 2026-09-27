package com.openhand.khata.feature.csv

import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.model.TransactionRecord
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Khata's own CSV format, version [VERSION]; `docs/csv-format.md` is its specification. A first
 * line names the format and version, then a header row, then one row per transaction. Dates and
 * times are local to the phone's time zone.
 */
object KhataCsvFormat {
    const val VERSION = 1
    const val MARKER = "khata_csv"

    /** Version 1 columns. A later version may only add columns after these. */
    val HEADER = listOf(
        "date",
        "time",
        "amount",
        "direction",
        "account",
        "payee",
        "payee_name",
        "category",
        "tags",
        "note",
        "reference_no"
    )

    const val TAG_SEPARATOR = "|"

    // Columns every row must have; a program that drops empty fields at the end still works.
    private const val REQUIRED_COLUMNS = 4
    private const val TWO_DIGITS = 2
    private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")

    /** The whole file, with a byte order mark so Excel reads it as UTF-8. */
    fun write(records: List<TransactionRecord>, zone: ZoneId): String = Csv.BOM + Csv.write(
        listOf(listOf(MARKER, VERSION.toString()), HEADER) + records.map { row(it, zone) }
    )

    private fun row(record: TransactionRecord, zone: ZoneId): List<String> {
        val time = Instant.ofEpochMilli(record.timestamp).atZone(zone)
        return listOf(
            time.toLocalDate().toString(),
            time.format(TIME),
            rupees(record.amountPaise),
            record.direction.name.lowercase(),
            record.account.orEmpty(),
            record.payee.orEmpty(),
            record.payeeName.orEmpty(),
            record.category.orEmpty(),
            record.tags.joinToString(TAG_SEPARATOR),
            record.note.orEmpty(),
            record.referenceNo.orEmpty()
        )
    }

    /** Always two decimals and no grouping, e.g. `1250.50`, so spreadsheets read it as a number. */
    fun rupees(paise: Long): String = "${paise / Money.PAISE_PER_RUPEE}." +
        (paise % Money.PAISE_PER_RUPEE).toString().padStart(TWO_DIGITS, '0')

    /**
     * How many rows at the top of [rows] are Khata's marker and header, or null when the file isn't
     * in Khata's format. The marker line is optional, in case a spreadsheet dropped it.
     */
    fun headerRows(rows: List<List<String>>): Int? {
        val hasMarker = rows.firstOrNull()?.firstOrNull()?.trim()?.lowercase() == MARKER
        val skip = if (hasMarker) 2 else 1
        val names = rows.getOrNull(skip - 1).orEmpty().map { it.trim().lowercase() }
        return skip.takeIf { names.size >= HEADER.size && names.take(HEADER.size) == HEADER }
    }

    /** Reads the data rows of a file that [headerRows] recognised. */
    fun parse(rows: List<List<String>>, zone: ZoneId): List<ParsedRow> {
        val skip = headerRows(rows) ?: return emptyList()
        return rows.drop(skip).mapIndexed { index, fields ->
            parsedRow(line = skip + index + 1) { record(fields, zone) }
        }
    }

    private fun record(fields: List<String>, zone: ZoneId): TransactionRecord {
        if (fields.size < REQUIRED_COLUMNS) throw RowError(RowProblem.COLUMNS)
        val column = { name: String -> fields.getOrNull(HEADER.indexOf(name))?.trim().orEmpty() }
        val text = { name: String -> column(name).ifEmpty { null } }
        val date = parseOrNull { LocalDate.parse(column("date")) }.orFail(RowProblem.DATE)
        val time = text("time")
            ?.let { parseOrNull { LocalTime.parse(it) }.orFail(RowProblem.TIME) }
            ?: LocalTime.MIDNIGHT
        val amount = Money.parsePaise(column("amount"))?.takeIf { it > 0 }
        val direction = column("direction")
        return TransactionRecord(
            timestamp = LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli(),
            amountPaise = amount.orFail(RowProblem.AMOUNT),
            direction = Direction.entries.firstOrNull {
                it.name.equals(direction, ignoreCase = true)
            }
                .orFail(RowProblem.DIRECTION),
            account = text("account"),
            payee = text("payee"),
            payeeName = text("payee_name"),
            category = text("category"),
            tags = column("tags").split(TAG_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() },
            note = text("note"),
            referenceNo = text("reference_no")
        )
    }
}

/** [block]'s result, or null when it can't parse its text. */
internal inline fun <T> parseOrNull(block: () -> T): T? = try {
    block()
} catch (_: DateTimeParseException) {
    null
}
