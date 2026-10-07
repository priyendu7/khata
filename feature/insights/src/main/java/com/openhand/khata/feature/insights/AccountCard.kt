package com.openhand.khata.feature.insights

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.AccountSpend
import com.openhand.khata.core.model.DefaultCategory

/**
 * Spending by account (PRD feature 5): a slice per account and one for No account. A donut works
 * here, unlike for tags, because each transaction has at most one account, so the slices add up
 * to what was spent.
 */
@Composable
internal fun AccountCard(accounts: AccountsState, actions: CardActions<Account?>) {
    ChartCard(stringResource(R.string.insights_by_account)) {
        PeriodChips(accounts.period, actions.onSelectPeriod)
        PeriodStepper(accounts, actions.onStepPeriod, actions.onSelectPast)
        if (accounts.spending.isEmpty()) {
            ChartEmpty(
                stringResource(R.string.insights_nothing_spent_title),
                stringResource(R.string.insights_nothing_spent_body)
            )
        } else {
            val theme = chartTheme()
            val charted = accounts.spending.filter { it.spentPaise > 0 }.sumOf { it.spentPaise }
            val entries = accounts.spending.map { it.toEntry(theme, charted) }
            SpendingDonut(
                slices = entries.filter { it.paise > 0 },
                refunded = entries.filter { it.paise < 0 },
                totalPaise = accounts.spending.sumOf { it.spentPaise },
                onOpen = actions.onOpen,
                theme = theme
            )
        }
    }
}

@Composable
private fun AccountSpend.toEntry(theme: ChartTheme, charted: Long) = LegendEntry(
    key = account,
    name = accountLabel(account),
    color = account?.let { theme.categoryColor(accountColor(it.id)) } ?: theme.other,
    paise = spentPaise,
    share = if (spentPaise > 0) shareOf(spentPaise, charted) else null
)

/** "HDFC Card ••5678", "Savings · SBI" or No account; no bank when the name has it. */
@Composable
internal fun accountLabel(account: Account?): String {
    if (account == null) return stringResource(R.string.insights_no_account)
    val bank = account.bank?.takeUnless { account.name.contains(it, ignoreCase = true) }
    val name = listOfNotNull(account.name, bank).joinToString(" · ")
    return account.last4?.let { stringResource(R.string.insights_account_last4, name, it) } ?: name
}

/**
 * An account's slice color: picked from a fixed palette by its id, so an account keeps its color
 * from one period to the next. Uncategorized's grey is left out; it would read as No account.
 */
internal fun accountColor(id: Long): Int = ACCOUNT_PALETTE[id.mod(ACCOUNT_PALETTE.size)]

private val ACCOUNT_PALETTE = DefaultCategory.entries
    .filter { it != DefaultCategory.UNCATEGORIZED }
    .map { it.color }
