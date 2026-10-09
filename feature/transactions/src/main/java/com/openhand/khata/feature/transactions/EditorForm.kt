package com.openhand.khata.feature.transactions

import com.openhand.khata.core.model.CountsIn
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.model.Payee
import com.openhand.khata.core.model.Transaction
import com.openhand.khata.core.model.TransferSide
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
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
    val rememberPayee: Boolean = true,
    /** The month totals count it in; null is "same as date" (#93). */
    val countsIn: YearMonth? = null,
    /** Names of the events on [date] (#73), whose tags are filled in like a payee's defaults. */
    val eventTags: List<String> = emptyList(),
    /** For a transfer: money out of the account or into it (#113); null when not known. */
    val transferSide: TransferSide? = null
) {
    /**
     * Switching to Transfer starts at "money out", unless a side is already known. A saved
     * transfer whose side isn't known keeps none until the user picks one.
     */
    fun withDirection(direction: Direction): EditorForm {
        val toTransfer = direction == Direction.TRANSFER && this.direction != Direction.TRANSFER
        return copy(
            direction = direction,
            transferSide = transferSide ?: TransferSide.OUT.takeIf { toTransfer }
        )
    }

    val dateMonth: YearMonth get() = YearMonth.from(date)

    /** Moves to [date], keeping a counts-in month only while it's still next to the new date's. */
    fun withDate(date: LocalDate): EditorForm =
        copy(date = date, countsIn = CountsIn.keepFor(countsIn, YearMonth.from(date)))

    /** Picking the date's own month means "same as date". */
    fun withCountsIn(month: YearMonth?): EditorForm =
        copy(countsIn = month?.takeIf { it != dateMonth })

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

    /**
     * Switches to [names], the events on a new date. The old events' tags make way for the new
     * ones', but tags the user added stay, and so do the payee's defaults.
     */
    fun withEventTags(names: List<String>): EditorForm {
        if (names == eventTags) return this
        val kept = knownPayee?.defaultTags.orEmpty()
        val gone = eventTags.filterNot { old -> kept.any { it.equals(old, ignoreCase = true) } }
        val ownTags = tags.filterNot { tag -> gone.any { it.equals(tag, ignoreCase = true) } }
        return names.fold(copy(eventTags = names, tags = ownTags)) { form, tag ->
            form.withTag(tag)
        }
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
        note = note,
        countsIn = countsIn,
        transferSide = transferSide.takeIf { direction == Direction.TRANSFER }
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
                note = transaction.note.orEmpty(),
                countsIn = transaction.countsIn,
                transferSide = transaction.transferSide
            )
        }

        private val AMOUNT_INPUT = Regex("\\d{0,${Money.MAX_RUPEE_DIGITS}}(\\.\\d{0,2})?")

        /** Whether [text] may be typed into the amount field: digits, one point, two decimals. */
        fun isAmountInput(text: String): Boolean = AMOUNT_INPUT.matches(text)
    }
}
