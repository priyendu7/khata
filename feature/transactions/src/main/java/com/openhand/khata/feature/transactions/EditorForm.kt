package com.openhand.khata.feature.transactions

import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.model.Payee
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
    val note: String = "",
    /** The saved payee [payee] refers to, or null for a new one. Its defaults are filled in. */
    val knownPayee: Payee? = null,
    /** Save this category and tags as the payee's defaults; offered while it has none. */
    val rememberPayee: Boolean = true
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

    /** Whether to offer saving this category and tags for next time (a new payee, or no defaults). */
    val canRememberPayee: Boolean
        get() = payee.isNotBlank() && knownPayee?.hasDefaults != true

    /**
     * Switches to [match], the saved payee the typed name now refers to (null for none). The old
     * payee's default category and tags make way for the new one's, but a category the user picked
     * and tags they added stay: that's the one-transaction override.
     */
    fun withKnownPayee(match: Payee?): EditorForm {
        if (match == knownPayee) return this
        val old = knownPayee
        val picked = categoryId != old?.defaultCategoryId
        val oldTags = old?.defaultTags.orEmpty()
        val ownTags = tags.filterNot { tag -> oldTags.any { it.equals(tag, ignoreCase = true) } }
        return match?.defaultTags.orEmpty().fold(
            copy(
                knownPayee = match,
                categoryId = if (picked) categoryId else match?.defaultCategoryId,
                tags = ownTags
            )
        ) { form, tag -> form.withTag(tag) }
    }

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
