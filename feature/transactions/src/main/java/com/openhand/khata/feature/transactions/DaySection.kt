package com.openhand.khata.feature.transactions

import com.openhand.khata.core.model.Totals
import com.openhand.khata.core.model.TransactionListItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One day in the transactions list: its date, what was spent and received, and its rows. */
data class DaySection(val date: LocalDate, val totals: Totals, val items: List<TransactionListItem>)

/** Splits a newest-first list into days in [zone], keeping the order. */
fun groupByDay(items: List<TransactionListItem>, zone: ZoneId): List<DaySection> {
    val sections = mutableListOf<DaySection>()
    var date: LocalDate? = null
    var day = mutableListOf<TransactionListItem>()
    fun close() {
        val d = date ?: return
        sections += DaySection(d, Totals.of(day.map { it.direction to it.amountPaise }), day)
    }
    for (item in items) {
        val itemDate = Instant.ofEpochMilli(item.timestamp).atZone(zone).toLocalDate()
        if (itemDate != date) {
            close()
            date = itemDate
            day = mutableListOf()
        }
        day += item
    }
    close()
    return sections
}
