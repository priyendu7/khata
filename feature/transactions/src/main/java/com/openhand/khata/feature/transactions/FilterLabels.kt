package com.openhand.khata.feature.transactions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.Tag
import com.openhand.khata.core.model.TransactionFilter
import com.openhand.khata.core.ui.R as UiR

/** The tag button's label: the chosen tag, Untagged, or just "Tag". */
@Composable
internal fun tagLabel(filter: TransactionFilter, tags: List<Tag>): String = when {
    filter.untagged -> stringResource(UiR.string.tag_untagged)
    else -> tags.firstOrNull { it.id == filter.tagId }?.name ?: stringResource(R.string.field_tag)
}

/** The account button's label: the chosen account, No account, or just "Account". */
@Composable
internal fun accountLabel(filter: TransactionFilter, accounts: List<Account>): String = when {
    filter.noAccount -> stringResource(R.string.no_account)
    else -> accounts.firstOrNull { it.id == filter.accountId }?.name
        ?: stringResource(R.string.field_account)
}
