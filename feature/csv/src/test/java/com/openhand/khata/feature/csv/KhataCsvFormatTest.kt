package com.openhand.khata.feature.csv

import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.TransactionRecord
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KhataCsvFormatTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private val chai = TransactionRecord(
        timestamp = at("2026-09-27T08:05:30"),
        amountPaise = 2_000,
        direction = Direction.DEBIT,
        account = "HDFC Savings",
        payee = "paytmqr281005050101@paytm",
        payeeName = "चाय वाला",
        category = "Food",
        tags = listOf("office", "snacks"),
        note = "Chai, samosa and \"biscuits\"\nfor the team",
        referenceNo = "426912345678"
    )
    private val cardBill = TransactionRecord(
        timestamp = at("2026-09-28T19:00:00"),
        amountPaise = 1_250_050,
        direction = Direction.TRANSFER,
        account = "HDFC Savings",
        category = "Uncategorized"
    )
    private val salary = TransactionRecord(
        timestamp = at("2026-09-30T09:00:00"),
        amountPaise = 8_500_000,
        direction = Direction.CREDIT,
        note = "Salary",
        countsIn = YearMonth.of(2026, 10)
    )

    private fun roundTrip(vararg records: TransactionRecord): List<ParsedRow> =
        KhataCsvFormat.parse(Csv.parse(KhataCsvFormat.write(records.toList(), zone)), zone)

    @Test
    fun writesMarkerHeaderAndRupeesWithTwoDecimals() {
        val lines = KhataCsvFormat.write(listOf(cardBill), zone).split("\r\n")
        assertEquals("${Csv.BOM}khata_csv,2", lines[0])
        assertEquals(
            "date,time,amount,direction,account,payee,payee_name,category,tags,note," +
                "reference_no,counts_in",
            lines[1]
        )
        assertEquals(
            "2026-09-28,19:00:00,12500.50,transfer,HDFC Savings,,,Uncategorized,,,,",
            lines[2]
        )
    }

    @Test
    fun readsBackTheSameRecords() {
        val rows = roundTrip(chai, cardBill, salary)
        assertEquals(
            listOf(
                ParsedRow.Valid(3, chai),
                ParsedRow.Valid(4, cardBill),
                ParsedRow.Valid(5, salary)
            ),
            rows
        )
    }

    @Test
    fun emptyFieldsAreNone() {
        val record = (roundTrip(cardBill).single() as ParsedRow.Valid).record
        assertNull(record.payee)
        assertNull(record.note)
        assertTrue(record.tags.isEmpty())
    }

    @Test
    fun recognisesTheHeaderWithOrWithoutTheMarker() {
        val header = KhataCsvFormat.HEADER
        assertEquals(2, KhataCsvFormat.headerRows(listOf(listOf("khata_csv", "1"), header)))
        assertEquals(1, KhataCsvFormat.headerRows(listOf(header.map { it.uppercase() })))
        assertEquals(1, KhataCsvFormat.headerRows(listOf(header + "a_later_column")))
        assertNull(KhataCsvFormat.headerRows(listOf(listOf("Date", "Amount", "Description"))))
    }

    @Test
    fun badRowsAreReportedOneByOne() {
        val text = KhataCsvFormat.HEADER.joinToString(",") + "\n" +
            "27/09/2026,,100,debit\n" +
            "2026-09-27,25:00,100,debit\n" +
            "2026-09-27,,abc,debit\n" +
            "2026-09-27,,0,debit\n" +
            "2026-09-27,,100,spent\n" +
            "2026-09-27,,100\n" +
            "2026-09-27,,100,CREDIT\n"
        val rows = KhataCsvFormat.parse(Csv.parse(text), zone)
        assertEquals(
            listOf(
                ParsedRow.Invalid(2, RowProblem.DATE),
                ParsedRow.Invalid(3, RowProblem.TIME),
                ParsedRow.Invalid(4, RowProblem.AMOUNT),
                ParsedRow.Invalid(5, RowProblem.AMOUNT),
                ParsedRow.Invalid(6, RowProblem.DIRECTION),
                ParsedRow.Invalid(7, RowProblem.COLUMNS)
            ),
            rows.dropLast(1)
        )
        val last = rows.last() as ParsedRow.Valid
        assertEquals(Direction.CREDIT, last.record.direction)
        assertEquals(at("2026-09-27T00:00:00"), last.record.timestamp)
    }

    @Test
    fun acceptsTimeWithoutSecondsAndSpacesAroundTags() {
        val text = KhataCsvFormat.HEADER.joinToString(",") + "\n" +
            "2026-09-27,08:05,20,debit,,,,,office | snacks ||,,\n"
        val record = (
            KhataCsvFormat.parse(
                Csv.parse(text),
                zone
            ).single() as ParsedRow.Valid
            ).record
        assertEquals(at("2026-09-27T08:05:00"), record.timestamp)
        assertEquals(listOf("office", "snacks"), record.tags)
    }

    @Test
    fun writesTheCountsInMonth() {
        val lines = KhataCsvFormat.write(listOf(salary), zone).split("\r\n")
        assertEquals("2026-09-30,09:00:00,85000.00,credit,,,,,,Salary,,2026-10", lines[2])
    }

    @Test
    fun aVersion1FileWithoutCountsInStillImports() {
        val text = "khata_csv,1\n" +
            "date,time,amount,direction,account,payee,payee_name,category,tags,note," +
            "reference_no\n" +
            "2026-09-30,09:00:00,85000.00,credit,,,,,,Salary,\n"
        val rows = KhataCsvFormat.parse(Csv.parse(text), zone)
        assertEquals(listOf(ParsedRow.Valid(3, salary.copy(countsIn = null))), rows)
    }

    @Test
    fun aCountsInThatIsntAMonthIsReported() {
        val text = KhataCsvFormat.HEADER.joinToString(",") + "\n" +
            "2026-09-30,,100,credit,,,,,,,,October\n"
        assertEquals(
            listOf(ParsedRow.Invalid(2, RowProblem.COUNTS_IN)),
            KhataCsvFormat.parse(Csv.parse(text), zone)
        )
    }
}
