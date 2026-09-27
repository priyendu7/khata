package com.openhand.khata.feature.transactions

import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Payee
import com.openhand.khata.core.model.Transaction
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorFormTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val form = EditorForm(date = LocalDate.of(2026, 9, 26), time = LocalTime.of(13, 5))

    @Test
    fun explainsWhatIsWrongWithTheAmount() {
        assertEquals(AmountError.EMPTY, form.amountError)
        assertEquals(AmountError.INVALID, form.copy(amount = ".").amountError)
        assertEquals(AmountError.ZERO, form.copy(amount = "0.00").amountError)
        assertNull(form.copy(amount = "12.5").amountError)
    }

    @Test
    fun amountFieldAcceptsOnlyPlainAmounts() {
        listOf("", "1", "12.", "12.5", "12.50", ".5", "99999999999.99").forEach {
            assertTrue(it, EditorForm.isAmountInput(it))
        }
        listOf("12.505", "1.2.3", "-1", "1,000", "a", "999999999999").forEach {
            assertFalse(it, EditorForm.isAmountInput(it))
        }
    }

    @Test
    fun tagsIgnoreBlanksAndCaseDuplicates() {
        val tagged = form.withTag(" Work ").withTag("work").withTag(" ").withTag("trip")
        assertEquals(listOf("Work", "trip"), tagged.tags)
        assertEquals(listOf("trip"), tagged.withoutTag("Work").tags)
    }

    @Test
    fun roundTripsThroughATransaction() {
        val filled = form.copy(
            amount = "123.4",
            direction = Direction.REFUND,
            accountId = 3,
            payee = "Swiggy",
            categoryId = 7,
            tags = listOf("food"),
            note = "cold"
        )
        val transaction = filled.toTransaction(id = 9, zone = zone)

        assertEquals(
            Transaction(
                id = 9,
                amountPaise = 12_340,
                direction = Direction.REFUND,
                timestamp = ZonedDateTime.of(
                    2026,
                    9,
                    26,
                    13,
                    5,
                    0,
                    0,
                    zone
                ).toInstant().toEpochMilli(),
                accountId = 3,
                payeeName = "Swiggy",
                categoryId = 7,
                tags = listOf("food"),
                note = "cold"
            ),
            transaction
        )
        assertEquals(filled.copy(amount = "123.40"), EditorForm.from(transaction, zone))
    }

    private val swiggy = Payee(
        id = 1,
        identifier = "swiggy@icici",
        displayName = "Swiggy",
        defaultCategoryId = 7,
        defaultTags = listOf("online")
    )
    private val zomato = Payee(
        id = 2,
        identifier = "Zomato",
        displayName = "Zomato",
        defaultCategoryId = 8,
        defaultTags = listOf("delivery")
    )

    @Test
    fun fillsInAKnownPayeesDefaults() {
        val filled = form.copy(payee = "Swiggy", tags = listOf("Online", "work"))
            .withKnownPayee(swiggy)

        assertEquals(7L, filled.categoryId)
        assertEquals(listOf("Online", "work"), filled.tags)
        assertEquals(swiggy, filled.knownPayee)
        assertFalse(filled.canRememberPayee)
    }

    @Test
    fun aPickedCategoryAndAddedTagsOverrideTheDefaults() {
        val picked = form.copy(payee = "Swiggy", categoryId = 3).withKnownPayee(swiggy)
        assertEquals(3L, picked.categoryId)

        val overridden = form.copy(payee = "Swiggy").withKnownPayee(swiggy)
            .copy(categoryId = 3).withTag("office").withoutTag("online")
        assertEquals(3L, overridden.categoryId)
        assertEquals(listOf("office"), overridden.tags)
        // The same match arriving again (e.g. after a retyped letter) changes nothing.
        assertEquals(overridden, overridden.withKnownPayee(swiggy))
    }

    @Test
    fun switchingPayeesSwapsOnlyTheFilledInValues() {
        val swapped = form.copy(payee = "Swiggy").withKnownPayee(swiggy).withTag("work")
            .copy(payee = "Zomato").withKnownPayee(zomato)
        assertEquals(8L, swapped.categoryId)
        assertEquals(listOf("work", "delivery"), swapped.tags)

        val cleared = swapped.copy(payee = "Zom").withKnownPayee(null)
        assertNull(cleared.categoryId)
        assertEquals(listOf("work"), cleared.tags)
        assertTrue(cleared.canRememberPayee)
    }

    @Test
    fun offersToRememberOnlyANamedPayeeWithoutDefaults() {
        assertFalse(form.canRememberPayee)
        assertTrue(form.copy(payee = "Ramesh").canRememberPayee)
        val bare = swiggy.copy(defaultCategoryId = null, defaultTags = emptyList())
        assertTrue(form.copy(payee = "Swiggy").withKnownPayee(bare).canRememberPayee)
    }
}
