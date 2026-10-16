package com.openhand.khata.feature.transactions

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.Direction
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

/** The type button's label (#164): "Type", the one or two picked, or how many are picked. */
@Composable
internal fun typeLabel(directions: Set<Direction>): String {
    val names = Direction.entries.filter { it in directions }.map { stringResource(typeName(it)) }
    return when {
        names.isEmpty() -> stringResource(R.string.filter_type)
        names.size <= MAX_NAMED_TYPES -> names.joinToString(", ")
        else -> stringResource(R.string.filter_types_count, names.size)
    }
}

/** A type's name in the filter: Spent, Received, Refund or Transfer. */
@StringRes
internal fun typeName(direction: Direction): Int = when (direction) {
    Direction.DEBIT -> R.string.filter_type_debit
    Direction.CREDIT -> R.string.filter_type_credit
    Direction.REFUND -> R.string.filter_type_refund
    Direction.TRANSFER -> R.string.filter_type_transfer
}

/** More picked types than this show as a count, so the button stays short. */
private const val MAX_NAMED_TYPES = 2
