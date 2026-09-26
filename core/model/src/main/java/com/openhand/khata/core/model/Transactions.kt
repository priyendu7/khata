package com.openhand.khata.core.model

/** A transaction as the add/edit screen reads and writes it. */
data class Transaction(
    val id: Long = 0,
    /** Always positive; [direction] gives the sign. */
    val amountPaise: Long,
    val direction: Direction,
    /** Milliseconds since the epoch. */
    val timestamp: Long,
    val accountId: Long? = null,
    /** Who was paid or who paid; matched to a saved payee by name, or saved as a new one. */
    val payeeName: String? = null,
    /** Null means Uncategorized. */
    val categoryId: Long? = null,
    val tags: List<String> = emptyList(),
    val note: String? = null
)

/** One row of the transactions list, with everything it shows already joined in. */
data class TransactionListItem(
    val id: Long,
    val amountPaise: Long,
    val direction: Direction,
    val timestamp: Long,
    val payeeName: String?,
    val note: String?,
    val accountName: String?,
    val category: Category,
    val tags: List<String>
)

/**
 * What the transactions list shows. Every field left null (or blank) means "any", and set fields
 * combine: category AND tag AND account AND date range AND search text.
 */
data class TransactionFilter(
    /** Matches the payee's name or the note, ignoring case. */
    val query: String = "",
    val categoryId: Long? = null,
    val tagId: Long? = null,
    val accountId: Long? = null,
    /** Inclusive start, in epoch milliseconds. */
    val from: Long? = null,
    /** Exclusive end, in epoch milliseconds. */
    val until: Long? = null
) {
    val isFiltered: Boolean
        get() = query.isNotBlank() ||
            categoryId != null ||
            tagId != null ||
            accountId != null ||
            from != null ||
            until != null
}

/**
 * Spending and income for a set of transactions (PRD: transfers never count as either).
 * Spent is expenses minus refunds, so a refund cancels the expense it pays back.
 */
data class Totals(val spentPaise: Long, val incomePaise: Long) {
    companion object {
        val ZERO = Totals(0, 0)

        fun of(items: Iterable<Pair<Direction, Long>>): Totals {
            var spent = 0L
            var income = 0L
            for ((direction, amount) in items) {
                when (direction) {
                    Direction.DEBIT -> spent += amount
                    Direction.REFUND -> spent -= amount
                    Direction.CREDIT -> income += amount
                    Direction.TRANSFER -> Unit
                }
            }
            return Totals(spent, income)
        }
    }
}
