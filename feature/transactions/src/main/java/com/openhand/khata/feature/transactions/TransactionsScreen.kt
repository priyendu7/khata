package com.openhand.khata.feature.transactions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.openhand.khata.core.ui.EmptyState
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.ScreenTitle

// TODO(Phase 1): transactions grouped by day, with search and filters.
@Composable
fun TransactionsScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        ScreenTitle(stringResource(UiR.string.nav_transactions))
        EmptyState(
            icon = painterResource(UiR.drawable.ic_ledger),
            title = stringResource(R.string.transactions_empty_title),
            body = stringResource(R.string.transactions_empty_body)
        )
    }
}
