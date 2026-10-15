package com.openhand.khata.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.model.TransactionFilter
import com.openhand.khata.core.ui.Choice
import com.openhand.khata.core.ui.ChoiceDialog
import com.openhand.khata.core.ui.DateRangeDialog
import com.openhand.khata.core.ui.PickerField
import com.openhand.khata.core.ui.Segments
import com.openhand.khata.core.ui.SubScreen

/** A debit card's page (#155), wired to its [DebitCardViewModel]. */
@Composable
fun DebitCardScreen(
    onBack: () -> Unit,
    onOpenTransactions: (TransactionFilter) -> Unit,
    viewModel: DebitCardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickingDates by rememberSaveable { mutableStateOf(false) }
    DebitCardContent(
        state,
        DebitCardActions(
            onBack = onBack,
            onLink = viewModel::link,
            period = CardActions(
                onSelectPeriod = {
                    if (it ==
                        ChartPeriod.CUSTOM
                    ) {
                        pickingDates = true
                    } else {
                        viewModel.selectPeriod(it)
                    }
                },
                onStepPeriod = viewModel::stepPeriod,
                onSelectPast = viewModel::selectPast,
                onOpen = { id -> viewModel.filter(id)?.let(onOpenTransactions) }
            )
        )
    )
    if (pickingDates) {
        DateRangeDialog(
            start = state?.span?.first,
            end = state?.span?.last,
            onPick = { first, last ->
                viewModel.selectRange(first, last)
                pickingDates = false
            },
            onDismiss = { pickingDates = false }
        )
    }
}

/** The card's bank account, then its spending by category over the chosen period. */
@Composable
fun DebitCardContent(state: DebitCardState?, actions: DebitCardActions = DebitCardActions()) {
    SubScreen(
        title = state?.let { cardLabel(it.card) }.orEmpty(),
        onBack = actions.onBack
    ) { padding ->
        if (state == null) return@SubScreen
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            LinkedAccountField(state, actions.onLink)
            ChartCard(stringResource(R.string.insights_by_category)) {
                PeriodChips(state.period, actions.period.onSelectPeriod)
                PeriodStepper(state, actions.period.onStepPeriod, actions.period.onSelectPast)
                PeriodSwipe(state, actions.period.onStepPeriod) {
                    if (state.spending.isEmpty) {
                        ChartEmpty(
                            stringResource(R.string.insights_nothing_spent_title),
                            stringResource(R.string.debit_nothing_spent_body)
                        )
                    } else {
                        CategoryBreakdownChart(state.spending, actions.period.onOpen)
                    }
                }
            }
        }
    }
}

/** The bank account the card spends from; its spending counts there too. */
@Composable
private fun LinkedAccountField(state: DebitCardState, onLink: (Long?) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val notLinked = stringResource(R.string.debit_not_linked)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(horizontal = Segments.Inset)
    ) {
        PickerField(
            label = stringResource(R.string.debit_linked_account),
            value = state.linkedAccount?.let { accountLabel(it) } ?: notLinked,
            onClick = { choosing = true }
        )
        Text(
            stringResource(R.string.debit_linked_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (choosing) {
        ChoiceDialog(
            title = stringResource(R.string.debit_linked_account),
            choices = listOf(Choice<Long?>(null, notLinked)) +
                state.bankAccounts.map { Choice(it.id, accountLabel(it)) },
            selected = state.linkedAccount?.id,
            onSelect = {
                onLink(it)
                choosing = false
            },
            onDismiss = { choosing = false }
        )
    }
}
