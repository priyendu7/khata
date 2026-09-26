package com.openhand.khata.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openhand.khata.R
import com.openhand.khata.core.ui.EmptyState
import com.openhand.khata.core.ui.ScreenTitle

// TODO(Phase 1): real totals from the repository once transactions can be added.
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(stringResource(R.string.app_name))
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.home_this_month),
                    style = MaterialTheme.typography.titleMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Total(stringResource(R.string.home_spent))
                    Total(stringResource(R.string.home_income))
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.padding(20.dp)) {
                Total(stringResource(R.string.home_spent_today))
            }
        }
        EmptyState(
            icon = painterResource(R.drawable.ic_ledger),
            title = stringResource(R.string.home_empty_title),
            body = stringResource(R.string.home_empty_body)
        )
    }
}

@Composable
private fun Total(label: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.amount_zero), style = MaterialTheme.typography.headlineSmall)
    }
}
