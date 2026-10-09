package com.openhand.khata.core.data

import androidx.room.withTransaction
import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.database.dao.TransactionDao
import com.openhand.khata.core.database.dao.TransactionRow
import com.openhand.khata.core.database.dao.TransferRow
import com.openhand.khata.core.database.entity.PayeeEntity
import com.openhand.khata.core.database.entity.TransactionEntity
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.AccountSpend
import com.openhand.khata.core.model.AmountEntry
import com.openhand.khata.core.model.CategorySpend
import com.openhand.khata.core.model.CountsIn
import com.openhand.khata.core.model.DefaultCategory
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Tag
import com.openhand.khata.core.model.TagSpend
import com.openhand.khata.core.model.Totals
import com.openhand.khata.core.model.Transaction
import com.openhand.khata.core.model.TransactionFilter
import com.openhand.khata.core.model.TransactionListItem
import com.openhand.khata.core.model.TransactionSource
import com.openhand.khata.core.model.TransferEntry
import com.openhand.khata.core.model.TransferKind
import com.openhand.khata.core.model.TransferSide
import com.openhand.khata.core.model.TransferSummary
import dagger.Lazy
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class TransactionRepository @Inject constructor(private val db: Lazy<KhataDatabase>) {
    /**
     * The transactions matching [filter], newest first by their real date, updated whenever the
     * data changes. The date range uses the month each one counts in. [limit] keeps only the
     * newest ones (Home's recent transactions).
     */
    fun observe(
        filter: TransactionFilter = TransactionFilter(),
        zone: ZoneId = ZoneId.systemDefault(),
        limit: Int? = null
    ): Flow<List<TransactionListItem>> {
        val query = filter.query.trim().ifEmpty { null }?.let(::escapeLike)
        return db.observe {
            it.transactionDao().observeList(
                query = query,
                categoryId = filter.categoryId,
                tagId = filter.tagId,
                untagged = filter.untagged,
                accountId = filter.accountId,
                noAccount = filter.noAccount,
                from = filter.from,
                until = filter.until,
                limit = limit ?: TransactionDao.NO_LIMIT
            )
        }.map { rows -> rows.map { it.toModel(zone) } }
    }

    /** Whether there are any transactions at all, updated as they're added or deleted. */
    fun observeAny(): Flow<Boolean> = observeFirstTimestamp().map { it != null }

    /** When the earliest transaction happened (epoch milliseconds), or null when there are none. */
    fun observeFirstTimestamp(): Flow<Long?> =
        db.observe { it.transactionDao().observeFirstTimestamp() }

    /**
     * Spending and income for timestamps in [from, until) (epoch milliseconds), updated whenever
     * the data changes. Transfers count as neither, and refunds reduce spending (PRD feature 1).
     */
    fun observeTotals(from: Long, until: Long): Flow<Totals> =
        db.observe { it.transactionDao().observeTotals(from, until) }
            .map { Totals(spentPaise = it.spentPaise, incomePaise = it.incomePaise) }

    /** The category with the most spending in [from, until), or null when nothing was spent. */
    fun observeTopCategory(from: Long, until: Long): Flow<CategorySpend?> =
        db.observe { it.transactionDao().observeTopCategory(from, until) }
            .map { row -> row?.let { CategorySpend(it.category.toModel(), it.spentPaise) } }

    /**
     * Every category's spending in [from, until), biggest first, with the same rules as
     * [observeTotals]: added up, they give its spent figure. Refunds beyond spending are negative.
     */
    fun observeCategorySpending(from: Long, until: Long): Flow<List<CategorySpend>> =
        observeCategorySpending(from, until, CategoryScope())

    /**
     * Like [observeCategorySpending], for one [tag]'s transactions only, or those with no tag
     * when [tag] is null. Added up, they give that tag's figure in [observeTagSpending].
     */
    fun observeCategorySpendingForTag(
        from: Long,
        until: Long,
        tag: Tag?
    ): Flow<List<CategorySpend>> = observeCategorySpending(
        from,
        until,
        CategoryScope(tagId = tag?.id, untagged = tag == null)
    )

    /**
     * Like [observeCategorySpending], for one [account]'s transactions only, or those with no
     * account when [account] is null. Added up, they give its figure in [observeAccountSpending].
     */
    fun observeCategorySpendingForAccount(
        from: Long,
        until: Long,
        account: Account?
    ): Flow<List<CategorySpend>> = observeCategorySpending(
        from,
        until,
        CategoryScope(accountId = account?.id, noAccount = account == null)
    )

    private fun observeCategorySpending(from: Long, until: Long, scope: CategoryScope) =
        db.observe {
            it.transactionDao().observeCategorySpending(
                from,
                until,
                scope.tagId,
                scope.untagged,
                scope.accountId,
                scope.noAccount
            )
        }.map { rows -> rows.map { CategorySpend(it.category.toModel(), it.spentPaise) } }

    /**
     * Each tag's spending in [from, until), biggest first, then Untagged (a null tag) when there
     * is any. A transaction with several tags counts in each, so these can add up to more than
     * was spent. Tags that net to zero are left out; refunds beyond spending are negative and
     * come just before Untagged.
     */
    fun observeTagSpending(from: Long, until: Long): Flow<List<TagSpend>> =
        db.observe { it.tagDao().observeTagSpending(from, until) }.map { rows ->
            rows.map { row ->
                TagSpend(row.tag?.let { Tag(it.id, it.name) }, row.spentPaise, row.count)
            }
        }

    /**
     * Each account's spending in [from, until), biggest first, with No account as a null account.
     * Added up, they give [observeTotals]' spent figure: transfers never count, so paying a card
     * bill isn't counted again. Accounts that net to zero are left out; refunds beyond spending
     * are negative and come last.
     */
    fun observeAccountSpending(from: Long, until: Long): Flow<List<AccountSpend>> =
        db.observe { it.accountDao().observeAccountSpending(from, until) }.map { rows ->
            rows.map { AccountSpend(it.account?.toModel(), it.spentPaise) }
        }

    /** Every expense, refund and income in [from, until), for the Insights charts. */
    fun observeAmounts(from: Long, until: Long): Flow<List<AmountEntry>> =
        db.observe { it.transactionDao().observeAmounts(from, until) }.map { rows ->
            rows.map { AmountEntry(it.timestamp, it.direction, it.amountPaise, it.categoryId) }
        }

    /**
     * What moved between the user's accounts in [from, until) (#113): card bills per card, and
     * each move once. None of it counts in [observeTotals] or any chart.
     */
    fun observeTransfers(from: Long, until: Long): Flow<TransferSummary> =
        db.observe { it.transferDao().observeTransfers(from, until) }
            .map { rows -> TransferSummary.of(rows.map { it.toModel() }) }

    /** The transaction with [id] as the edit screen shows it, or null if it's gone. */
    suspend fun get(id: Long, zone: ZoneId = ZoneId.systemDefault()): Transaction? =
        db.io { database ->
            val dao = database.transactionDao()
            val entity = dao.getById(id) ?: return@io null
            Transaction(
                id = entity.id,
                amountPaise = entity.amountPaise,
                direction = entity.direction,
                timestamp = entity.timestamp,
                accountId = entity.accountId,
                payeeName = entity.payeeId?.let { database.payeeDao().getById(it)?.displayName },
                categoryId = entity.categoryId,
                tags = dao.tagNames(id),
                note = entity.note,
                countsIn = CountsIn.fromMillis(entity.countsAt, zone),
                transferSide = entity.transferSide
            )
        }

    /**
     * Adds a new transaction (id 0) or updates one, and returns its id. The payee is matched by
     * name or saved as a new one, tags are created as needed, and no category means Uncategorized.
     * Fields the screen doesn't show (source, reference number, SMS text) are kept on update. A
     * counts-in month that is the date's own month is saved as "same as date", in [zone].
     *
     * With [rememberPayeeDefaults], a payee that has no defaults yet takes this transaction's
     * category and tags as its defaults, and its Uncategorized transactions take the category. A
     * payee that already has some keeps them: a different category here overrides them for this
     * transaction only (PRD feature 3).
     *
     * A transfer keeps its kind and its other side while its side stays the same; picking a side
     * makes it a transfer the user set (#113). One that's no longer a transfer loses all three,
     * and its other side, which stays a transfer, is unlinked.
     */
    suspend fun save(
        transaction: Transaction,
        rememberPayeeDefaults: Boolean = false,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        require(transaction.amountPaise > 0) { "Amount must be more than zero" }
        val payeeName = transaction.payeeName?.trim()?.ifEmpty { null }
        val note = transaction.note?.trim()?.ifEmpty { null }
        return db.io { database ->
            val payeeDao = database.payeeDao()
            val payee = payeeName?.let { name ->
                payeeDao.findByName(name)
                    ?: PayeeEntity(identifier = name, displayName = name, defaultCategoryId = null)
                        .let { it.copy(id = payeeDao.insert(it)) }
            }
            val tagIds = database.tagDao().getOrCreate(transaction.tags)
            val uncategorizedId =
                database.categoryDao().getBySeedKey(DefaultCategory.UNCATEGORIZED.key)!!.id
            val categoryId = transaction.categoryId ?: uncategorizedId
            if (payee != null && rememberPayeeDefaults) {
                val defaultCategoryId = categoryId.takeUnless { it == uncategorizedId }
                rememberDefaults(database, payee, defaultCategoryId, tagIds, uncategorizedId)
            }
            val dao = database.transactionDao()
            val existing = if (transaction.id == 0L) null else dao.getById(transaction.id)
            val transfer = transferOf(transaction, existing)
            val entity = TransactionEntity(
                id = existing?.id ?: 0,
                amountPaise = transaction.amountPaise,
                direction = transaction.direction,
                timestamp = transaction.timestamp,
                accountId = transaction.accountId,
                payeeId = payee?.id,
                categoryId = categoryId,
                note = note,
                referenceNo = existing?.referenceNo,
                source = existing?.source ?: TransactionSource.MANUAL,
                rawSms = existing?.rawSms,
                // Saving from the edit screen means the user has looked at it.
                needsReview = false,
                countsAt = CountsIn.toMillis(transaction.countsIn, transaction.timestamp, zone),
                transferSide = transfer.side,
                transferPairId = transfer.pairId,
                transferKind = transfer.kind
            )
            database.withTransaction {
                val oldPair = existing?.transferPairId
                if (oldPair != null && oldPair != transfer.pairId) {
                    database.transferDao().setTransferPair(oldPair, null)
                }
                dao.saveWithTags(entity, tagIds)
            }
        }
    }

    private fun transferOf(
        transaction: Transaction,
        existing: TransactionEntity?
    ): TransferColumns {
        if (transaction.direction != Direction.TRANSFER) return TransferColumns()
        val unchanged = existing?.direction == Direction.TRANSFER &&
            existing.transferSide == transaction.transferSide
        return if (unchanged) {
            TransferColumns(existing.transferSide, existing.transferPairId, existing.transferKind)
        } else {
            TransferColumns(transaction.transferSide, kind = TransferKind.MANUAL)
        }
    }

    /**
     * Saves defaults for a payee that has none; never replaces ones already saved. A remembered
     * category also files the payee's older Uncategorized transactions (tags aren't applied).
     */
    private suspend fun rememberDefaults(
        database: KhataDatabase,
        payee: PayeeEntity,
        categoryId: Long?,
        tagIds: List<Long>,
        uncategorizedId: Long
    ) {
        val dao = database.payeeDao()
        val hasDefaults =
            payee.defaultCategoryId != null || dao.defaultTagIds(payee.id).isNotEmpty()
        if (hasDefaults || (categoryId == null && tagIds.isEmpty())) return
        dao.updateWithDefaultTags(payee.copy(defaultCategoryId = categoryId), tagIds)
        categoryId?.let { dao.categorizeUncategorized(payee.id, it, uncategorizedId) }
    }

    /** Deletes [id]; if it was one side of a transfer, the other side stays, on its own. */
    suspend fun delete(id: Long) {
        db.io { it.transferDao().deleteUnlinking(id) }
    }
}

private fun TransactionRow.toModel(zone: ZoneId) = TransactionListItem(
    id = id,
    amountPaise = amountPaise,
    direction = direction,
    timestamp = timestamp,
    payeeName = payeeName,
    note = note,
    accountName = accountName,
    category = category.toModel(),
    tags = tags?.split(TransactionRow.TAG_SEPARATOR)?.sortedBy { it.lowercase() }.orEmpty(),
    countsIn = CountsIn.fromMillis(countsAt, zone)
)

/** Which transactions a category breakdown covers: a tag (or untagged), an account (or none). */
private data class CategoryScope(
    val tagId: Long? = null,
    val untagged: Boolean = false,
    val accountId: Long? = null,
    val noAccount: Boolean = false
)

private fun TransferRow.toModel() = TransferEntry(
    id = id,
    amountPaise = amountPaise,
    timestamp = timestamp,
    side = side,
    kind = kind,
    account = account?.toModel(),
    payeeName = payeeName,
    pairId = pairId,
    pairAccount = pairAccount?.toModel()
)

/** A transaction's transfer columns (#113); all null for anything that isn't a transfer. */
private data class TransferColumns(
    val side: TransferSide? = null,
    val pairId: Long? = null,
    val kind: TransferKind? = null
)
