package com.openhand.khata.feature.transactions

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.ui.incomeColor
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@StringRes
internal fun Direction.label(): Int = when (this) {
    Direction.DEBIT -> R.string.direction_debit
    Direction.CREDIT -> R.string.direction_credit
    Direction.REFUND -> R.string.direction_refund
    Direction.TRANSFER -> R.string.direction_transfer
}

@Composable
private fun locale(): Locale = LocalConfiguration.current.locales[0]

/** "Sat, 26 Sep", with the year added when it isn't this year. */
@Composable
internal fun dateLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val pattern = if (date.year == today.year) "EEE, d MMM" else "EEE, d MMM yyyy"
    return date.format(DateTimeFormatter.ofPattern(pattern, locale()))
}

/** "October", with the year added when it isn't this year. */
@Composable
internal fun monthLabel(month: YearMonth, today: LocalDate = LocalDate.now()): String {
    val pattern = if (month.year == today.year) "LLLL" else "LLLL yyyy"
    return month.format(DateTimeFormatter.ofPattern(pattern, locale()))
}

/** "Today", "Yesterday", or the date. */
@Composable
internal fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> stringResource(R.string.day_today)
    today.minusDays(1) -> stringResource(R.string.day_yesterday)
    else -> dateLabel(date, today)
}

/** "3:45 pm" or "15:45", following the phone's 12/24-hour setting. */
@Composable
internal fun timeLabel(time: LocalTime): String {
    val pattern = if (DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm a"
    return time.format(DateTimeFormatter.ofPattern(pattern, locale()))
}

/** The amount as the list shows it: money in gets a plus sign. */
internal fun signedAmount(direction: Direction, paise: Long): String = when (direction) {
    Direction.CREDIT, Direction.REFUND -> "+" + Money.format(paise)
    Direction.DEBIT, Direction.TRANSFER -> Money.format(paise)
}

/** Green for money in, muted for transfers (they aren't spending), normal for expenses. */
@Composable
internal fun amountColor(direction: Direction): Color = when (direction) {
    Direction.CREDIT, Direction.REFUND -> incomeColor()
    Direction.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
    Direction.DEBIT -> MaterialTheme.colorScheme.onSurface
}
