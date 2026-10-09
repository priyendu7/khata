package com.openhand.khata.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.openhand.khata.R
import com.openhand.khata.core.model.TransactionFilter
import com.openhand.khata.feature.accounts.AccountsScreen
import com.openhand.khata.feature.categories.CategoriesScreen
import com.openhand.khata.feature.categories.EventsScreen
import com.openhand.khata.feature.categories.TagsScreen
import com.openhand.khata.feature.csv.BackupReminderNotifier
import com.openhand.khata.feature.csv.ExportScreen
import com.openhand.khata.feature.csv.ImportScreen
import com.openhand.khata.feature.insights.HomeScreen
import com.openhand.khata.feature.insights.InsightsScreen
import com.openhand.khata.feature.lock.LockSettingsPage
import com.openhand.khata.feature.lock.LockSettingsRows
import com.openhand.khata.feature.payees.PayeesScreen
import com.openhand.khata.feature.settings.AddParserScreen
import com.openhand.khata.feature.settings.EDIT_BUILTIN_ARG
import com.openhand.khata.feature.settings.EDIT_RULE_ID_ARG
import com.openhand.khata.feature.settings.EditParserScreen
import com.openhand.khata.feature.settings.FILTERS_SHOW_ARG
import com.openhand.khata.feature.settings.FiltersScreen
import com.openhand.khata.feature.settings.IGNORE_FROM_UNPARSED_ARG
import com.openhand.khata.feature.settings.IgnoreLikeThisScreen
import com.openhand.khata.feature.settings.MAKE_FROM_BODY_ARG
import com.openhand.khata.feature.settings.MAKE_FROM_SENDER_ARG
import com.openhand.khata.feature.settings.MAKE_FROM_UNPARSED_ARG
import com.openhand.khata.feature.settings.MAKE_REMARK_ARG
import com.openhand.khata.feature.settings.MakeParserScreen
import com.openhand.khata.feature.settings.NO_UNPARSED
import com.openhand.khata.feature.settings.ParsersScreen
import com.openhand.khata.feature.settings.REMARKED_RULE_KEY
import com.openhand.khata.feature.settings.SettingsPage
import com.openhand.khata.feature.settings.SettingsRoute
import com.openhand.khata.feature.settings.SmsImportScreen
import com.openhand.khata.feature.settings.TestMessageScreen
import com.openhand.khata.feature.transactions.AddTransactionButton
import com.openhand.khata.feature.transactions.FILTER_ACCOUNT_ARG
import com.openhand.khata.feature.transactions.FILTER_CATEGORY_ARG
import com.openhand.khata.feature.transactions.FILTER_FROM_ARG
import com.openhand.khata.feature.transactions.FILTER_NO_ACCOUNT
import com.openhand.khata.feature.transactions.FILTER_TAG_ARG
import com.openhand.khata.feature.transactions.FILTER_UNTAGGED
import com.openhand.khata.feature.transactions.FILTER_UNTIL_ARG
import com.openhand.khata.feature.transactions.FROM_UNPARSED_ARG
import com.openhand.khata.feature.transactions.NO_FILTER
import com.openhand.khata.feature.transactions.NO_PREFILL
import com.openhand.khata.feature.transactions.PREFILL_AMOUNT_ARG
import com.openhand.khata.feature.transactions.PREFILL_AT_ARG
import com.openhand.khata.feature.transactions.ReviewCountViewModel
import com.openhand.khata.feature.transactions.ReviewScreen
import com.openhand.khata.feature.transactions.TRANSACTION_ID_ARG
import com.openhand.khata.feature.transactions.TransactionEditorScreen
import com.openhand.khata.feature.transactions.TransactionsScreen

/**
 * Top-level navigation: the tabs, and the screens opened from them. [openRequest] is a screen to
 * open from outside (the backup reminder notification opens Export).
 */
@Composable
fun KhataNavigation(openRequest: String? = null, onOpened: () -> Unit = {}) {
    val navController = rememberNavController()
    val back: () -> Unit = { navController.popBackStack() }
    LaunchedEffect(openRequest) {
        if (openRequest == BackupReminderNotifier.OPEN_EXPORT) {
            navController.navigate(Route.EXPORT) { launchSingleTop = true }
            onOpened()
        }
    }
    NavHost(navController, startDestination = Route.TABS) {
        composable(Route.TABS) {
            MainTabs(
                onOpen = { page -> navController.navigate(page.route()) },
                onOpenTransaction = { id -> navController.navigate(Route.transaction(id)) },
                onOpenTransactions = { navController.navigate(Route.transactions(it)) },
                onReview = { navController.navigate(Route.REVIEW) { launchSingleTop = true } }
            )
        }
        composable(
            Route.TRANSACTIONS,
            arguments = listOf(
                FILTER_CATEGORY_ARG,
                FILTER_TAG_ARG,
                FILTER_ACCOUNT_ARG,
                FILTER_FROM_ARG,
                FILTER_UNTIL_ARG
            ).map {
                navArgument(it) {
                    type = NavType.LongType
                    defaultValue = NO_FILTER
                }
            }
        ) {
            TransactionsScreen(
                onOpen = { id -> navController.navigate(Route.transaction(id)) },
                onBack = back
            )
        }
        composable(
            Route.TRANSACTION,
            arguments = listOf(navArgument(TRANSACTION_ID_ARG) { type = NavType.LongType }) +
                listOf(PREFILL_AMOUNT_ARG, PREFILL_AT_ARG, FROM_UNPARSED_ARG).map {
                    navArgument(it) {
                        type = NavType.LongType
                        defaultValue = NO_PREFILL
                    }
                }
        ) { TransactionEditorScreen(onDone = back) }
        composable(Route.ACCOUNTS) { AccountsScreen(onBack = back) }
        composable(Route.CATEGORIES) { CategoriesScreen(onBack = back) }
        composable(Route.TAGS) { TagsScreen(onBack = back) }
        composable(Route.EVENTS) { EventsScreen(onBack = back) }
        composable(Route.PAYEES) { PayeesScreen(onBack = back) }
        composable(Route.EXPORT) { ExportScreen(onBack = back) }
        composable(Route.IMPORT) { ImportScreen(onBack = back) }
        composable(Route.LOCK) { LockSettingsPage(onBack = back) }
        composable(Route.SMS_IMPORT) {
            SmsImportScreen(
                onBack = back,
                onFilters = { navController.navigate(Route.filters()) },
                onTestMessage = { navController.navigate(Route.TEST_MESSAGE) }
            )
        }
        composable(
            Route.FILTERS,
            arguments = listOf(
                navArgument(FILTERS_SHOW_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { FiltersScreen(onBack = back) }
        composable(Route.TEST_MESSAGE) {
            TestMessageScreen(
                onBack = back,
                onMakeParser = { sender, body ->
                    navController.navigate(Route.makeParser(sender, body))
                },
                onOpenFilter = { navController.navigate(Route.filters(it.name)) }
            )
        }
        composable(Route.PARSERS) {
            ParsersScreen(
                onBack = back,
                onAdd = { navController.navigate(Route.ADD_PARSER) },
                onEdit = { ruleId, builtIn ->
                    navController.navigate(Route.editParser(ruleId, builtIn))
                }
            )
        }
        composable(
            Route.EDIT_PARSER,
            arguments = listOf(
                navArgument(EDIT_RULE_ID_ARG) { type = NavType.StringType },
                navArgument(EDIT_BUILTIN_ARG) { type = NavType.BoolType }
            )
        ) {
            EditParserScreen(
                onDone = back,
                onRemark = { navController.navigate(Route.remarkParser(it)) }
            )
        }
        composable(Route.ADD_PARSER) {
            AddParserScreen(
                onDone = back,
                // Instead of Add, so leaving the maker goes back to Settings > Parsers.
                onMake = {
                    navController.navigate(Route.makeParser()) {
                        popUpTo(Route.ADD_PARSER) { inclusive = true }
                    }
                }
            )
        }
        composable(
            Route.MAKE_PARSER,
            arguments = listOf(
                navArgument(MAKE_FROM_UNPARSED_ARG) {
                    type = NavType.LongType
                    defaultValue = NO_UNPARSED
                }
            ) + listOf(MAKE_FROM_SENDER_ARG, MAKE_FROM_BODY_ARG, MAKE_REMARK_ARG).map {
                navArgument(it) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            }
        ) {
            MakeParserScreen(
                onDone = back,
                // Back to Settings > Parsers > Edit, which picks up the new pattern.
                onRemarked = { code ->
                    navController.previousBackStackEntry?.savedStateHandle
                        ?.set(REMARKED_RULE_KEY, code)
                    navController.popBackStack()
                }
            )
        }
        composable(Route.REVIEW) {
            ReviewScreen(
                onBack = back,
                onAddByHand = { amount, at, unparsedId ->
                    navController.navigate(Route.addFromSms(amount, at, unparsedId))
                },
                onMakeParser = { unparsedId ->
                    navController.navigate(Route.makeParser(unparsedId))
                },
                onIgnoreLikeThis = { unparsedId ->
                    navController.navigate(Route.ignoreLikeThis(unparsedId))
                }
            )
        }
        composable(
            Route.IGNORE_LIKE_THIS,
            arguments = listOf(
                navArgument(IGNORE_FROM_UNPARSED_ARG) { type = NavType.LongType }
            )
        ) { IgnoreLikeThisScreen(onDone = back) }
    }
}

private object Route {
    const val TABS = "tabs"
    const val ACCOUNTS = "accounts"
    const val CATEGORIES = "categories"
    const val TAGS = "tags"
    const val EVENTS = "events"
    const val PAYEES = "payees"
    const val EXPORT = "export"
    const val IMPORT = "import"
    const val LOCK = "lock"
    const val SMS_IMPORT = "sms_import"
    const val TEST_MESSAGE = "sms_import/test"
    const val FILTERS = "sms_import/filters?$FILTERS_SHOW_ARG={$FILTERS_SHOW_ARG}"

    /** Filters, with [show]'s switch brought into view and marked. */
    fun filters(show: String? = null) =
        "sms_import/filters" + (show?.let { "?$FILTERS_SHOW_ARG=$it" } ?: "")
    const val PARSERS = "parsers"
    const val ADD_PARSER = "parsers/add"
    const val EDIT_PARSER = "parsers/edit/{$EDIT_RULE_ID_ARG}/{$EDIT_BUILTIN_ARG}"

    fun editParser(ruleId: String, builtIn: Boolean) = "parsers/edit/${Uri.encode(ruleId)}/$builtIn"
    const val REVIEW = "review"
    const val IGNORE_LIKE_THIS = "review/ignore/{$IGNORE_FROM_UNPARSED_ARG}"

    fun ignoreLikeThis(unparsedId: Long) = "review/ignore/$unparsedId"

    /** Make a parser, from an SMS in To review or one to pick or paste. */
    const val MAKE_PARSER = "parsers/make?$MAKE_FROM_UNPARSED_ARG={$MAKE_FROM_UNPARSED_ARG}" +
        "&$MAKE_FROM_SENDER_ARG={$MAKE_FROM_SENDER_ARG}&$MAKE_FROM_BODY_ARG={$MAKE_FROM_BODY_ARG}" +
        "&$MAKE_REMARK_ARG={$MAKE_REMARK_ARG}"

    fun makeParser(unparsedId: Long = NO_UNPARSED) =
        "parsers/make?$MAKE_FROM_UNPARSED_ARG=$unparsedId"

    /** The rule maker on an SMS that isn't in To review, such as one from Test a message. */
    fun makeParser(sender: String, body: String) = "parsers/make?$MAKE_FROM_SENDER_ARG=${Uri.encode(
        sender
    )}&$MAKE_FROM_BODY_ARG=${Uri.encode(body)}"

    /** The rule maker re-marking a rule (its code) for Settings > Parsers > Edit. */
    fun remarkParser(code: String) = "parsers/make?$MAKE_REMARK_ARG=${Uri.encode(code)}"

    /** Add (id 0) or edit a transaction. */
    const val TRANSACTION = "transaction/{$TRANSACTION_ID_ARG}" +
        "?$PREFILL_AMOUNT_ARG={$PREFILL_AMOUNT_ARG}&$PREFILL_AT_ARG={$PREFILL_AT_ARG}" +
        "&$FROM_UNPARSED_ARG={$FROM_UNPARSED_ARG}"

    fun transaction(id: Long) = "transaction/$id"

    /** Add a transaction for an SMS no rule could read, with what's known filled in. */
    fun addFromSms(amountPaise: Long?, at: Long, unparsedId: Long) =
        "transaction/0?$PREFILL_AMOUNT_ARG=${amountPaise ?: NO_PREFILL}" +
            "&$PREFILL_AT_ARG=$at&$FROM_UNPARSED_ARG=$unparsedId"

    /** The transactions list, filtered by a category, tag, account and dates (each optional). */
    const val TRANSACTIONS = "transactions?$FILTER_CATEGORY_ARG={$FILTER_CATEGORY_ARG}" +
        "&$FILTER_TAG_ARG={$FILTER_TAG_ARG}&$FILTER_ACCOUNT_ARG={$FILTER_ACCOUNT_ARG}" +
        "&$FILTER_FROM_ARG={$FILTER_FROM_ARG}&$FILTER_UNTIL_ARG={$FILTER_UNTIL_ARG}"

    /**
     * Opens the list with [filter]'s category, tag (or untagged), account (or none) and dates;
     * search isn't kept.
     */
    fun transactions(filter: TransactionFilter): String {
        val tag = if (filter.untagged) FILTER_UNTAGGED else filter.tagId
        val account = if (filter.noAccount) FILTER_NO_ACCOUNT else filter.accountId
        return "transactions?$FILTER_CATEGORY_ARG=${filter.categoryId ?: NO_FILTER}" +
            "&$FILTER_TAG_ARG=${tag ?: NO_FILTER}&$FILTER_ACCOUNT_ARG=${account ?: NO_FILTER}" +
            "&$FILTER_FROM_ARG=${filter.from ?: NO_FILTER}" +
            "&$FILTER_UNTIL_ARG=${filter.until ?: NO_FILTER}"
    }
}

private fun SettingsPage.route() = when (this) {
    SettingsPage.SMS_IMPORT -> Route.SMS_IMPORT
    SettingsPage.FILTERS -> Route.filters()
    SettingsPage.TEST_MESSAGE -> Route.TEST_MESSAGE
    SettingsPage.PARSERS -> Route.PARSERS
    SettingsPage.ACCOUNTS -> Route.ACCOUNTS
    SettingsPage.CATEGORIES -> Route.CATEGORIES
    SettingsPage.TAGS -> Route.TAGS
    SettingsPage.EVENTS -> Route.EVENTS
    SettingsPage.PAYEES -> Route.PAYEES
    SettingsPage.EXPORT -> Route.EXPORT
    SettingsPage.IMPORT -> Route.IMPORT
    SettingsPage.LOCK -> Route.LOCK
}

/** Bottom-navigation shell: Home, Transactions, Insights, Settings. */
@Composable
private fun MainTabs(
    onOpen: (SettingsPage) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenTransactions: (TransactionFilter) -> Unit,
    onReview: () -> Unit,
    reviewCount: ReviewCountViewModel = hiltViewModel()
) {
    var current by rememberSaveable { mutableStateOf(Destination.HOME) }
    val toReview by reviewCount.count.collectAsStateWithLifecycle()
    Scaffold(
        floatingActionButton = {
            if (current == Destination.HOME || current == Destination.TRANSACTIONS) {
                AddTransactionButton(onClick = { onOpenTransaction(0L) })
            }
        },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == current,
                        onClick = { current = destination },
                        icon = {
                            // PRD feature 4: the To review count as a badge on Transactions.
                            val badge = toReview.takeIf {
                                destination == Destination.TRANSACTIONS &&
                                    it > 0
                            }
                            BadgedBox(badge = { badge?.let { Badge { Text("$it") } } }) {
                                Icon(painterResource(destination.icon), contentDescription = null)
                            }
                        },
                        label = { Text(stringResource(destination.label)) }
                    )
                }
            }
        }
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (current) {
            Destination.HOME -> HomeScreen(
                stringResource(R.string.app_name),
                modifier,
                onBackup = { onOpen(SettingsPage.EXPORT) },
                onReview = onReview
            )
            Destination.TRANSACTIONS -> TransactionsScreen(
                onOpenTransaction,
                modifier,
                onReview = onReview
            )
            Destination.INSIGHTS -> InsightsScreen(onOpenTransactions, onOpenTransaction, modifier)
            Destination.SETTINGS -> SettingsRoute(
                modifier,
                security = { LockSettingsRows(onOpenLock = { onOpen(SettingsPage.LOCK) }) },
                onOpen = onOpen
            )
        }
    }
}
