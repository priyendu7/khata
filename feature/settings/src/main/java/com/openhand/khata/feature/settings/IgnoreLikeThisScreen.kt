package com.openhand.khata.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.model.UnparsedSms
import com.openhand.khata.core.ui.SubScreen
import com.openhand.khata.sms.parser.RuleMaker

/** Ignore messages like this, from an SMS in To review (PRD feature 7). */
@Composable
fun IgnoreLikeThisScreen(onDone: () -> Unit, viewModel: IgnoreLikeThisViewModel = hiltViewModel()) {
    val sms by viewModel.sms.collectAsStateWithLifecycle()
    val matches by viewModel.matches.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()
    LaunchedEffect(done) { if (done) onDone() }
    IgnoreLikeThisContent(
        onBack = onDone,
        sms = sms,
        matches = matches,
        onTap = viewModel::count,
        onSave = viewModel::save
    )
}

@Composable
fun IgnoreLikeThisContent(
    onBack: () -> Unit,
    sms: UnparsedSms?,
    matches: Int?,
    onTap: (Set<RuleMaker.Word>) -> Unit,
    onSave: (Set<RuleMaker.Word>) -> Unit
) {
    SubScreen(title = stringResource(R.string.ignore_like_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            sms ?: return@Column
            var tapped by remember(sms.id) { mutableStateOf(emptySet<RuleMaker.Word>()) }
            Text(
                stringResource(R.string.ignore_like_intro),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                sms.sender,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                RuleMaker.words(sms.body).forEach { word ->
                    // Numbers always change; the template treats them so already.
                    val number = word.text.any(Char::isDigit)
                    FilterChip(
                        selected = number || word in tapped,
                        enabled = !number,
                        onClick = {
                            tapped = if (word in tapped) tapped - word else tapped + word
                            onTap(tapped)
                        },
                        label = { Text(word.text) }
                    )
                }
            }
            matches?.let {
                Text(
                    pluralStringResource(R.plurals.ignore_like_matches, it, it),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(onClick = { onSave(tapped) }) {
                Text(stringResource(R.string.ignore_like_save))
            }
        }
    }
}
