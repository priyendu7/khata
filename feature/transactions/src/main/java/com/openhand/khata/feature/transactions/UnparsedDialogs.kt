package com.openhand.khata.feature.transactions

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.openhand.khata.core.ui.R as UiR

/** "Dismiss all 34 SMS from HDFCBK?" They can't be brought back, so it asks first. */
@Composable
internal fun DismissAllDialog(
    header: String,
    count: Int,
    onDismissAll: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(pluralStringResource(R.plurals.review_dismiss_all_title, count, count, header))
        },
        text = { Text(stringResource(R.string.review_dismiss_all_body)) },
        confirmButton = {
            TextButton(onClick = {
                onDismissAll()
                onClose()
            }) { Text(stringResource(R.string.review_dismiss_all)) }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text(stringResource(UiR.string.cancel)) }
        }
    )
}

/** "Ignore all SMS from HDFCBK? Waiting ones are removed." */
@Composable
internal fun IgnoreSenderDialog(header: String, onIgnore: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.review_ignore_sender_title, header)) },
        text = { Text(stringResource(R.string.review_ignore_sender_body)) },
        confirmButton = {
            TextButton(onClick = {
                onIgnore()
                onClose()
            }) { Text(stringResource(R.string.review_ignore_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text(stringResource(UiR.string.cancel)) }
        }
    )
}

/** After copying: blank out personal details, then file it on GitHub yourself. */
@Composable
internal fun CopiedDialog(onOpenIssues: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.review_copied_title)) },
        text = { Text(stringResource(R.string.review_copied_body)) },
        confirmButton = {
            TextButton(onClick = {
                onOpenIssues()
                onClose()
            }) { Text(stringResource(R.string.review_open_github)) }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.review_close)) }
        }
    )
}
