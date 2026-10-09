package com.openhand.khata.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.TransactionListItem
import java.time.Instant
import java.time.ZoneId

/**
 * A transaction as item [index] of [count] of a segmented list: who or what, the details, and
 * the amount. Under the amount is the time, or with [showDay] the day as well, for lists that
 * aren't grouped by day (Home's recent transactions).
 */
@Composable
fun TransactionRow(
    item: TransactionListItem,
    index: Int,
    count: Int,
    showDay: Boolean = false,
    onClick: () -> Unit
) {
    val category = categoryName(item.category.name, item.category.seedKey)
    val details = listOfNotNull(
        stringResource(item.direction.label()).takeIf { item.direction != Direction.DEBIT },
        category.takeIf { item.payeeName != null },
        item.accountName,
        item.note,
        item.tags.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" }
    ).joinToString(" · ")
    val at = Instant.ofEpochMilli(item.timestamp).atZone(ZoneId.systemDefault())
    val time = timeLabel(at.toLocalTime())
    SegmentListItem(
        index = index,
        count = count,
        leadingContent = { CategoryBadge(item.category.icon, item.category.color) },
        headlineContent = {
            Text(item.payeeName ?: category, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = if (details.isEmpty()) {
            null
        } else {
            { Text(details, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    signedAmount(item.direction, item.amountPaise),
                    style = MaterialTheme.typography.titleMedium,
                    color = amountColor(item.direction)
                )
                Text(
                    if (showDay) dayLabel(at.toLocalDate()) + " · " + time else time,
                    style = MaterialTheme.typography.labelSmall
                )
                item.countsIn?.let {
                    Text(
                        stringResource(R.string.counts_in_label, monthLabel(it)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
