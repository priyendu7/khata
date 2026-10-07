package com.openhand.khata.core.model

import java.time.YearMonth

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
    val note: String? = null,
    /** The month totals count it in, or null for its date's month; see [CountsIn]. */
    val countsIn: YearMonth? = null
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
    val tags: List<String>,
    /** Set only when it counts in another month than its date's; see [CountsIn]. */
    val countsIn: YearMonth? = null
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
    /** Only transactions with no tag at all; used instead of [tagId]. */
    val untagged: Boolean = false,
    val accountId: Long? = null,
    /** Inclusive start, in epoch milliseconds. The range uses the counts-in month (#93). */
    val from: Long? = null,
    /** Exclusive end, in epoch milliseconds. */
    val until: Long? = null
) {
    val isFiltered: Boolean
        get() = query.isNotBlank() ||
            categoryId != null ||
            tagId != null ||
            untagged ||
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

/** How much went to [category] over a period: its expenses minus its refunds. */
data class CategorySpend(val category: Category, val spentPaise: Long)

/**
 * How much went to [tag] over a period (expenses minus refunds) and across how many
 * transactions. A null [tag] is Untagged: spending with no tag at all.
 */
data class TagSpend(val tag: Tag?, val spentPaise: Long, val count: Int)

/**
 * Spending by category, ready for a donut chart: the biggest categories as [slices], the rest
 * added up as [otherPaise], and the categories with more refunds than spending in [refunded],
 * which a donut can't draw. All three add up to [totalPaise], the same figure Home shows.
 */
data class CategoryBreakdown(
    val slices: List<CategorySpend>,
    val otherPaise: Long,
    val refunded: List<CategorySpend>
) {
    /** What the donut draws: the slices and "Other". */
    val chartedPaise: Long get() = slices.sumOf { it.spentPaise } + otherPaise

    val totalPaise: Long get() = chartedPaise + refunded.sumOf { it.spentPaise }

    val isEmpty: Boolean get() = slices.isEmpty() && refunded.isEmpty()

    companion object {
        /** Slices including "Other", so the donut stays readable (PRD: top 5–6 plus "Other"). */
        const val MAX_SLICES = 6

        private val BIGGEST_FIRST =
            compareByDescending<CategorySpend> { it.spentPaise }.thenBy { it.category.id }

        fun of(spending: List<CategorySpend>, maxSlices: Int = MAX_SLICES): CategoryBreakdown {
            val spent = spending.filter { it.spentPaise > 0 }.sortedWith(BIGGEST_FIRST)
            // Six categories fit as they are; with more, the fifth onwards become "Other".
            val shown = if (spent.size <= maxSlices) spent else spent.take(maxSlices - 1)
            return CategoryBreakdown(
                slices = shown,
                otherPaise = spent.drop(shown.size).sumOf { it.spentPaise },
                refunded = spending.filter { it.spentPaise < 0 }.sortedBy { it.spentPaise }
            )
        }
    }
}
