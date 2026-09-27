package com.openhand.khata.feature.csv

import com.openhand.khata.core.model.Direction
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading CSV files from banks and hand-kept spreadsheets. */
class ColumnMappingTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    private fun day(text: String) =
        LocalDate.parse(text).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun valid(rows: List<ParsedRow>) = rows.map { (it as ParsedRow.Valid).record }

    @Test
    fun guessesABankStatementWithDebitAndCreditColumns() {
        val rows = Csv.parse(
            "Txn Date,Narration,Withdrawal Amt,Deposit Amt,Closing Balance\n" +
                "05/09/26,UPI-SWIGGY,450.00,,10000.00\n" +
                "28/09/26,SALARY SEP,,85000.00,95000.00\n"
        )
        val mapping = ColumnMapping.guess(rows)
        assertEquals(0, mapping.date)
        assertEquals(1, mapping.description)
        // "Withdrawal Amt" contains "amt", but it's the spending column, not a signed amount.
        assertNull(mapping.amount)
        assertEquals(2, mapping.debit)
        assertEquals(3, mapping.credit)
        assertEquals(AmountStyle.SEPARATE_COLUMNS, mapping.amountStyle)
        assertEquals(DateFormat.DAY_MONTH_SHORT_YEAR, mapping.dateFormat)
        val records = valid(mapping.parse(rows, zone))
        assertEquals(listOf(Direction.DEBIT, Direction.CREDIT), records.map { it.direction })
        assertEquals(listOf("UPI-SWIGGY", "SALARY SEP"), records.map { it.note })
    }

    @Test
    fun guessesASpreadsheetWithOneAmountColumn() {
        val rows = Csv.parse(
            "Date,Description,Category,Amount\n2026-09-27,Groceries at DMart,Groceries,-1250.50\n"
        )
        val mapping = ColumnMapping.guess(rows)
        assertEquals(ColumnMapping(0, 3, null, null, 1, 2, DateFormat.YEAR_MONTH_DAY), mapping)
        val record = valid(mapping.parse(rows, zone)).single()
        assertEquals(125_050, record.amountPaise)
        assertEquals(Direction.DEBIT, record.direction)
        assertEquals("Groceries", record.category)
        assertEquals("Groceries at DMart", record.note)
        assertEquals(day("2026-09-27"), record.timestamp)
    }

    @Test
    fun signedAmountsGiveSpendingAndIncome() {
        val rows =
            listOf(listOf("d", "a"), listOf("27/09/2026", "-₹1,250.50"), listOf("1/9/2026", "+500"))
        val records = valid(ColumnMapping(date = 0, amount = 1).parse(rows, zone))
        assertEquals(listOf(Direction.DEBIT, Direction.CREDIT), records.map { it.direction })
        assertEquals(listOf(125_050L, 50_000L), records.map { it.amountPaise })
    }

    @Test
    fun everyRowIsSpendingWhenAsked() {
        val rows =
            listOf(listOf("d", "a"), listOf("27/09/2026", "250"), listOf("27/09/2026", "-99"))
        val mapping = ColumnMapping(date = 0, amount = 1, amountStyle = AmountStyle.ALL_SPENDING)
        val records = valid(mapping.parse(rows, zone))
        assertEquals(listOf(Direction.DEBIT, Direction.DEBIT), records.map { it.direction })
        assertEquals(listOf(25_000L, 9_900L), records.map { it.amountPaise })
    }

    @Test
    fun separateColumnsIgnoreZeroInTheOtherColumn() {
        val rows = listOf(
            listOf("d", "dr", "cr"),
            listOf("27/09/2026", "450.00", "0.00"),
            listOf("27/09/2026", "", "85,000"),
            listOf("27/09/2026", "0", "0")
        )
        val mapping = ColumnMapping(
            date = 0,
            debit = 1,
            credit = 2,
            amountStyle = AmountStyle.SEPARATE_COLUMNS
        )
        val parsed = mapping.parse(rows, zone)
        val records = valid(parsed.take(2))
        assertEquals(listOf(Direction.DEBIT, Direction.CREDIT), records.map { it.direction })
        assertEquals(listOf(45_000L, 8_500_000L), records.map { it.amountPaise })
        assertEquals(ParsedRow.Invalid(4, RowProblem.AMOUNT), parsed[2])
    }

    @Test
    fun amountsInBankNotation() {
        assertEquals(-25_000L, ColumnMapping.signed("(250.00)"))
        assertEquals(-100_000L, ColumnMapping.signed("1,000.00 Dr"))
        assertEquals(100_000L, ColumnMapping.signed("1,000.00Cr"))
        assertEquals(9_900L, ColumnMapping.signed("Rs. 99"))
        assertEquals(9_900L, ColumnMapping.signed("INR 99"))
        assertEquals(-5_000L, ColumnMapping.signed("50-"))
    }

    @Test
    fun badDatesAndAmountsFailOnlyTheirRow() {
        val rows = listOf(
            listOf("d", "a"),
            listOf("31/02/2026", "10"),
            listOf("27/09/2026", "ten"),
            listOf("27/09/2026", "0"),
            listOf("27/09/2026"),
            listOf("27/09/2026 14:30", "10")
        )
        val parsed = ColumnMapping(date = 0, amount = 1).parse(rows, zone)
        assertEquals(
            listOf(
                ParsedRow.Invalid(2, RowProblem.DATE),
                ParsedRow.Invalid(3, RowProblem.AMOUNT),
                ParsedRow.Invalid(4, RowProblem.AMOUNT),
                ParsedRow.Invalid(5, RowProblem.AMOUNT)
            ),
            parsed.dropLast(1)
        )
        assertEquals(day("2026-09-27"), (parsed.last() as ParsedRow.Valid).record.timestamp)
    }

    @Test
    fun dateFormats() {
        val expected = LocalDate.of(2026, 9, 27)
        DateFormat.entries.forEach { assertEquals(it.name, expected, it.parse(it.example)) }
        assertEquals(expected, DateFormat.DAY_MONTH_NAME_YEAR.parse("27 SEP 2026"))
        assertEquals(expected, DateFormat.DAY_MONTH_YEAR.parse("27.9.2026"))
        assertNull(DateFormat.MONTH_DAY_YEAR.parse("27/09/2026"))
    }

    @Test
    fun needsADateAndAnAmount() {
        assertFalse(ColumnMapping(amount = 1).isComplete)
        assertFalse(ColumnMapping(date = 0).isComplete)
        assertFalse(
            ColumnMapping(
                date = 0,
                amount = 1,
                amountStyle = AmountStyle.SEPARATE_COLUMNS
            ).isComplete
        )
        assertTrue(
            ColumnMapping(
                date = 0,
                credit = 2,
                amountStyle = AmountStyle.SEPARATE_COLUMNS
            ).isComplete
        )
    }
}
