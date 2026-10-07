package com.openhand.khata.feature.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.ui.Choice
import com.openhand.khata.core.ui.ChoiceDialog
import com.openhand.khata.core.ui.R as UiR
import java.time.format.DateTimeFormatter

/**
 * A card's dates as ‹ label ›: the arrows step a period back or forward, and tapping the label
 * lists earlier months or years to jump to. A custom period shows just its dates.
 */
@Composable
internal fun PeriodStepper(
    shown: ShownPeriod,
    onStepPeriod: (Int) -> Unit,
    onSelectPast: (Int) -> Unit
) {
    if (shown.period == ChartPeriod.CUSTOM) {
        Text(
            spanLabel(ChartPeriod.CUSTOM, shown.span),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val choose = shown.period.chooseName()?.takeIf { shown.choices.isNotEmpty() }
    var choosing by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = { onStepPeriod(-1) }) {
            Icon(
                painterResource(UiR.drawable.ic_chevron_left),
                contentDescription = stringResource(shown.period.previousName())
            )
        }
        PeriodLabel(
            text = spanLabel(shown.period, shown.span),
            chooseLabel = choose?.let { stringResource(it) },
            onClick = { choosing = true },
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { onStepPeriod(1) }, enabled = shown.canStepForward) {
            Icon(
                painterResource(UiR.drawable.ic_chevron_right),
                contentDescription = stringResource(shown.period.nextName())
            )
        }
    }
    if (choosing && choose != null) {
        ChoiceDialog(
            title = stringResource(choose),
            choices = shown.choices.mapIndexed { back, span ->
                Choice(back, spanLabel(shown.period, span))
            },
            selected = shown.back,
            onSelect = {
                onSelectPast(it)
                choosing = false
            },
            onDismiss = { choosing = false }
        )
    }
}

/** The label, a button that opens the list when there's one ([chooseLabel] isn't null). */
@Composable
private fun PeriodLabel(
    text: String,
    chooseLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .then(
                if (chooseLabel == null) {
                    Modifier
                } else {
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = chooseLabel,
                        onClick = onClick
                    )
                }
            )
            .padding(vertical = 8.dp)
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall)
        if (chooseLabel != null) {
            Icon(painterResource(UiR.drawable.ic_arrow_drop_down), contentDescription = null)
        }
    }
}

/** What ‹ and › say for each kind of period; a custom period has no arrows. */
private fun ChartPeriod.previousName(): Int = when (this) {
    ChartPeriod.WEEK -> R.string.insights_previous_week
    ChartPeriod.MONTH, ChartPeriod.CUSTOM -> R.string.insights_previous_month
    ChartPeriod.YEAR -> R.string.insights_previous_year
}

private fun ChartPeriod.nextName(): Int = when (this) {
    ChartPeriod.WEEK -> R.string.insights_next_week
    ChartPeriod.MONTH, ChartPeriod.CUSTOM -> R.string.insights_next_month
    ChartPeriod.YEAR -> R.string.insights_next_year
}

/** The title of the list the label opens; weeks have arrows only. */
private fun ChartPeriod.chooseName(): Int? = when (this) {
    ChartPeriod.MONTH -> R.string.insights_choose_month
    ChartPeriod.YEAR -> R.string.insights_choose_year
    ChartPeriod.WEEK, ChartPeriod.CUSTOM -> null
}

/** "September 2026", "22–28 Sep", "2025", or a custom period's first and last days. */
@Composable
internal fun spanLabel(period: ChartPeriod, span: DateSpan): String {
    val locale = LocalConfiguration.current.locales[0]
    fun format(pattern: String) = DateTimeFormatter.ofPattern(pattern, locale)
    val first = span.first
    val last = span.last
    return when {
        period == ChartPeriod.MONTH -> first.format(format("MMMM yyyy"))
        period == ChartPeriod.YEAR -> first.year.toString()
        period == ChartPeriod.WEEK && first.month == last.month ->
            first.format(format("d")) + "–" + last.format(format("d MMM"))
        period == ChartPeriod.WEEK && first.year == last.year ->
            first.format(format("d MMM")) + " – " + last.format(format("d MMM"))
        first == last -> first.format(format("d MMM yyyy"))
        else -> first.format(format("d MMM yyyy")) + " – " + last.format(format("d MMM yyyy"))
    }
}
