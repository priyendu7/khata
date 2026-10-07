package com.openhand.khata.feature.insights

import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.CategoryBreakdown
import com.openhand.khata.core.model.Tag
import com.openhand.khata.core.model.TransactionFilter

/** Whose spending a breakdown sheet splits by category: a tag's, or an account's. */
sealed interface BreakdownOf {
    /** The card whose period the sheet follows. */
    val card: PeriodCard

    /** The list's filter for these transactions, before the category and dates are added. */
    val filter: TransactionFilter

    /** A null [tag] is Untagged. */
    data class OfTag(val tag: Tag?) : BreakdownOf {
        override val card get() = PeriodCard.TAGS
        override val filter get() = TransactionFilter(tagId = tag?.id, untagged = tag == null)
    }

    /** A null [account] is No account. */
    data class OfAccount(val account: Account?) : BreakdownOf {
        override val card get() = PeriodCard.ACCOUNTS
        override val filter
            get() = TransactionFilter(accountId = account?.id, noAccount = account == null)
    }
}

/** One tag's or account's spending by category, for its card's period. */
data class BreakdownState(
    val of: BreakdownOf,
    val period: ChartPeriod,
    val span: DateSpan,
    val from: Long,
    val until: Long,
    val breakdown: CategoryBreakdown
) {
    /** The transactions list for it over this period, of one category or of any (null). */
    fun filter(categoryId: Long? = null) =
        of.filter.copy(categoryId = categoryId, from = from, until = until)
}
