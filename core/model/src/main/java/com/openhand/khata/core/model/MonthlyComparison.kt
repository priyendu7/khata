package com.openhand.khata.core.model

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * One month's bar: spending and income by the Home rules, and the spending split into
 * [segments], one per [MonthlyComparison.stacked] category and then "Other". The segments always
 * add up to [spentPaise]; only "Other" can be negative, when refunds beyond spending pull it down.
 */
data class MonthBar(
    val month: YearMonth,
    val spentPaise: Long,
    val incomePaise: Long,
    val segments: List<Long>
) {
    val otherPaise: Long get() = segments.last()
}

/** A stacked category's change from the month before the last one to the last one. */
data class CategoryChange(val category: Category, val change: Change)

/**
 * Bars for [bars]' months, oldest first, stacked by the biggest categories over the whole window
 * (the same grouping as the donut), and how the last month compares with the one before.
 */
data class MonthlyComparison(
    val bars: List<MonthBar>,
    val stacked: List<Category>,
    /** Total spending, last month against the one before. */
    val change: Change?,
    val categoryChanges: List<CategoryChange>
) {
    companion object {
        fun of(
            entries: List<AmountEntry>,
            months: List<YearMonth>,
            zone: ZoneId,
            categories: List<Category>
        ): MonthlyComparison {
            val byMonth = entries.groupBy {
                YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone))
            }
            val byId = categories.associateBy { it.id }
            val window = months.flatMap { byMonth[it].orEmpty() }
            val stacked = CategoryBreakdown.of(spendingByCategory(window, byId)).slices
                .map { it.category }
            val bars = months.map { month -> bar(month, byMonth[month].orEmpty(), stacked) }
            val last = bars.lastOrNull()
            val before = bars.getOrNull(bars.size - 2)
            return MonthlyComparison(
                bars = bars,
                stacked = stacked,
                change = if (last != null && before != null) {
                    Change.of(before.spentPaise, last.spentPaise)
                } else {
                    null
                },
                categoryChanges = if (last != null && before != null) {
                    stacked.mapIndexedNotNull { i, category ->
                        Change.of(before.segments[i], last.segments[i])
                            ?.let { CategoryChange(category, it) }
                    }
                } else {
                    emptyList()
                }
            )
        }

        private fun bar(month: YearMonth, entries: List<AmountEntry>, stacked: List<Category>) =
            Totals.of(entries.map { it.direction to it.amountPaise }).let { totals ->
                val perCategory = entries.groupBy { it.categoryId }.mapValues { (_, list) ->
                    Totals.of(list.map { it.direction to it.amountPaise }).spentPaise
                }
                // A stacked category with more refunds than spending this month adds nothing;
                // "Other" takes up the difference so the segments still add up.
                val shown = stacked.map { (perCategory[it.id] ?: 0L).coerceAtLeast(0) }
                MonthBar(
                    month = month,
                    spentPaise = totals.spentPaise,
                    incomePaise = totals.incomePaise,
                    segments = shown + (totals.spentPaise - shown.sum())
                )
            }

        private fun spendingByCategory(entries: List<AmountEntry>, byId: Map<Long, Category>) =
            entries.groupBy { it.categoryId }.mapNotNull { (id, list) ->
                val category = byId[id] ?: return@mapNotNull null
                CategorySpend(
                    category,
                    Totals.of(list.map { it.direction to it.amountPaise }).spentPaise
                )
            }
    }
}
