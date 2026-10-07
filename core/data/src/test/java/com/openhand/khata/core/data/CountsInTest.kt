package com.openhand.khata.core.data

import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.SmsTransaction
import com.openhand.khata.core.model.Totals
import com.openhand.khata.core.model.Transaction
import com.openhand.khata.core.model.TransactionFilter
import com.openhand.khata.core.model.spendingByDay
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** October's salary paid on 30 September and counted in October (#93). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CountsInTest : RepositoryTest() {
    private val transactions by lazy { TransactionRepository(lazyDb) }

    private suspend fun add(
        direction: Direction,
        amount: Long,
        at: Long,
        countsIn: YearMonth? = null,
        category: String = "uncategorized"
    ) = transactions.save(
        Transaction(
            amountPaise = amount,
            direction = direction,
            timestamp = at,
            categoryId = db.categoryDao().getBySeedKey(category)!!.id,
            countsIn = countsIn
        ),
        zone = INDIA
    )

    private suspend fun totals(from: Long, until: Long) =
        transactions.observeTotals(from, until).first()

    @Test
    fun aSalaryCountedInOctoberIsInOctobersTotalsNotSeptembers() = runTest {
        add(Direction.CREDIT, 85_000_00, SALARY_AT, OCTOBER)
        add(Direction.DEBIT, 500_00, SALARY_AT, OCTOBER, category = "food")
        add(Direction.DEBIT, 200_00, SALARY_AT, category = "food")

        assertEquals(Totals(spentPaise = 200_00, incomePaise = 0), totals(SEP_START, OCT_START))
        assertEquals(
            Totals(spentPaise = 500_00, incomePaise = 85_000_00),
            totals(OCT_START, NOV_START)
        )
        assertEquals(
            listOf(500_00L),
            transactions.observeCategorySpending(OCT_START, NOV_START).first()
                .map { it.spentPaise }
        )
        assertEquals(
            200_00L,
            transactions.observeTopCategory(SEP_START, OCT_START).first()!!.spentPaise
        )
    }

    @Test
    fun todayOnThe30thLeavesItOut() = runTest {
        add(Direction.CREDIT, 85_000_00, SALARY_AT, OCTOBER)

        assertEquals(Totals.ZERO, totals(SEP_30, OCT_START))
    }

    @Test
    fun chartAmountsUseThe1stOfTheCountsInMonth() = runTest {
        add(Direction.DEBIT, 500_00, SALARY_AT, OCTOBER)

        val september = transactions.observeAmounts(SEP_START, OCT_START).first()
        val october = transactions.observeAmounts(OCT_START, NOV_START).first()

        assertTrue(september.isEmpty())
        assertEquals(mapOf(LocalDate.of(2026, 10, 1) to 500_00L), spendingByDay(october, INDIA))
    }

    @Test
    fun theListFilteredToOctoberHasItButTheFullListKeepsItsRealDate() = runTest {
        val id = add(Direction.CREDIT, 85_000_00, SALARY_AT, OCTOBER)
        add(Direction.DEBIT, 50_00, OCT_START + DAY)

        val october = transactions.observe(TransactionFilter(from = OCT_START, until = NOV_START))
            .first()
        val september = transactions.observe(TransactionFilter(from = SEP_START, until = OCT_START))
            .first()
        val all = transactions.observe(zone = INDIA).first()

        assertTrue(october.any { it.id == id })
        assertTrue(september.none { it.id == id })
        // Newest first by the real date: the 2 Oct expense, then the 30 Sep salary.
        assertEquals(id, all[1].id)
        assertEquals(SALARY_AT, all[1].timestamp)
        assertEquals(OCTOBER, all[1].countsIn)
        assertNull(all[0].countsIn)
    }

    @Test
    fun choosingTheDatesOwnMonthIsStoredAsSameAsDate() = runTest {
        val id = add(Direction.CREDIT, 85_000_00, SALARY_AT, YearMonth.of(2026, 9))

        assertNull(db.transactionDao().getById(id)!!.countsAt)
        assertNull(transactions.get(id, INDIA)!!.countsIn)
    }

    @Test
    fun itsKeptWhenEditedAndClearedWhenSetBack() = runTest {
        val id = add(Direction.CREDIT, 85_000_00, SALARY_AT, OCTOBER)
        assertEquals(OCT_START, db.transactionDao().getById(id)!!.countsAt)

        val saved = transactions.get(id, INDIA)!!
        assertEquals(OCTOBER, saved.countsIn)
        transactions.save(saved.copy(note = "Salary"), zone = INDIA)
        assertEquals(OCT_START, db.transactionDao().getById(id)!!.countsAt)

        transactions.save(saved.copy(countsIn = null), zone = INDIA)
        assertNull(db.transactionDao().getById(id)!!.countsAt)
    }

    @Test
    fun anSmsForTheSameSalaryIsStillADuplicateByItsRealTime() = runTest {
        val id = add(Direction.CREDIT, 85_000_00, SALARY_AT, OCTOBER)
        val sms = SmsTransaction(
            amountPaise = 85_000_00,
            direction = Direction.CREDIT,
            timestamp = SALARY_AT + MINUTE,
            bank = "HDFC",
            accountType = AccountType.BANK,
            accountLast4 = "1234",
            payee = null,
            referenceNo = null,
            rawSms = "Rs.85000 credited to a/c XX1234"
        )

        val result = SmsImporter(lazyDb).import(sms)

        assertEquals(SmsImportResult.Duplicate(id, DuplicateMatch.AMOUNT_AND_TIME), result)
    }

    private companion object {
        val INDIA: ZoneId = ZoneId.of("Asia/Kolkata")
        val OCTOBER: YearMonth = YearMonth.of(2026, 10)
        const val DAY = 86_400_000L
        const val MINUTE = 60_000L

        /** 1 Sep, 30 Sep, 1 Oct and 1 Nov 2026 at 00:00 IST. */
        const val SEP_START = 1_788_201_000_000L
        const val SEP_30 = SEP_START + 29 * DAY
        const val OCT_START = SEP_START + 30 * DAY
        const val NOV_START = OCT_START + 31 * DAY

        /** 30 Sep 2026, 09:00 IST. */
        const val SALARY_AT = SEP_30 + 9 * 3_600_000L
    }
}
