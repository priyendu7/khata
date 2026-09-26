package com.openhand.khata.feature.transactions

import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.model.Transaction
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** What's wrong with the typed amount, shown under the field after Save is tapped. */
enum class AmountError { EMPTY, INVALID, ZERO }

/**
 * The add/edit form as the user fills it in. The amount stays as typed text until it's saved, so
 * half-typed values like `12.` survive.
 */
data class EditorForm(
    val amount: String = "",
    val direction: Direction = Direction.DEBIT,
    val date: LocalDate,
    val time: LocalTime,
    val accountId: Long? = null,
    val payee: String = "",
    /** Null means Uncategorized. */
    val categoryId: Long? = null,
    val tags: List<String> = emptyList(),
    val note: String = ""
) {
    val amountError: AmountError?
        get() {
            val paise = Money.parsePaise(amount)
            return when {
                amount.isBlank() -> AmountError.EMPTY
                paise == null -> AmountError.INVALID
                paise == 0L -> AmountError.ZERO
                else -> null
            }
        }

    /** Adds a tag unless it's blank or already there (ignoring case). */
    fun withTag(name: String): EditorForm {
        val clean = name.trim()
        if (clean.isEmpty() || tags.any { it.equals(clean, ignoreCase = true) }) return this
        return copy(tags = tags + clean)
    }

    fun withoutTag(name: String): EditorForm = copy(tags = tags - name)

    /** The transaction to save. Only call when [amountError] is null. */
    fun toTransaction(id: Long, zone: ZoneId): Transaction = Transaction(
        id = id,
        amountPaise = requireNotNull(Money.parsePaise(amount)),
        direction = direction,
        timestamp = ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli(),
        accountId = accountId,
        payeeName = payee,
        categoryId = categoryId,
        tags = tags,
        note = note
    )

    companion object {
        /** An empty form dated now. */
        fun new(now: ZonedDateTime): EditorForm = EditorForm(
            date = now.toLocalDate(),
            time = now.toLocalTime().truncatedTo(ChronoUnit.MINUTES)
        )

        fun from(transaction: Transaction, zone: ZoneId): EditorForm {
            val at = Instant.ofEpochMilli(transaction.timestamp).atZone(zone)
            return EditorForm(
                amount = Money.toInput(transaction.amountPaise),
                direction = transaction.direction,
                date = at.toLocalDate(),
                time = at.toLocalTime(),
                accountId = transaction.accountId,
                payee = transaction.payeeName.orEmpty(),
                categoryId = transaction.categoryId,
                tags = transaction.tags,
                note = transaction.note.orEmpty()
            )
        }

        private val AMOUNT_INPUT = Regex("\\d{0,${Money.MAX_RUPEE_DIGITS}}(\\.\\d{0,2})?")

        /** Whether [text] may be typed into the amount field: digits, one point, two decimals. */
        fun isAmountInput(text: String): Boolean = AMOUNT_INPUT.matches(text)
    }
}
