package com.openhand.khata.core.data

import androidx.room.withTransaction
import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.database.MetadataEntity
import com.openhand.khata.core.database.dao.TransactionRow
import com.openhand.khata.core.database.entity.TransactionEntity
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.TransactionRecord
import com.openhand.khata.core.model.TransactionSource
import dagger.Lazy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What importing did: rows added, and rows skipped because they were already saved. */
data class ImportResult(val added: Int, val duplicates: Int)

/**
 * CSV export and import (PRD feature 6), with names in place of ids so a file can be read by
 * people and other apps. The CSV text itself is written and read in :feature:csv.
 */
@Singleton
class BackupRepository @Inject constructor(private val db: Lazy<KhataDatabase>) {
    /**
     * Every transaction in [from, until) (epoch milliseconds; null for no limit), oldest first.
     * [categoryName] gives each category's name, since default categories are named by the UI.
     */
    suspend fun export(
        from: Long? = null,
        until: Long? = null,
        categoryName: (Category) -> String
    ): List<TransactionRecord> = db.io { database ->
        database.backupDao().exportRows(from, until).map { row ->
            TransactionRecord(
                timestamp = row.timestamp,
                amountPaise = row.amountPaise,
                direction = row.direction,
                account = row.accountName,
                payee = row.payeeIdentifier,
                payeeName = row.payeeName,
                category = categoryName(row.category.toModel()),
                tags = row.tags?.split(TransactionRow.TAG_SEPARATOR)
                    ?.sortedBy { it.lowercase() }.orEmpty(),
                note = row.note,
                referenceNo = row.referenceNo
            )
        }
    }

    /** When the last CSV export finished (epoch milliseconds), or null if there hasn't been one. */
    fun observeLastExport(): Flow<Long?> =
        db.observe { it.metadataDao().observe(LAST_EXPORT_KEY) }.map { it?.toLongOrNull() }

    suspend fun markExported(at: Long) {
        db.io { it.metadataDao().put(MetadataEntity(LAST_EXPORT_KEY, at.toString())) }
    }

    /**
     * Which of [records] are already saved, in the same order, as [import] will judge them. Dates
     * are compared as local days in [zone].
     */
    suspend fun findDuplicates(records: List<TransactionRecord>, zone: ZoneId): List<Boolean> =
        db.io { database ->
            duplicates(ImportResolver(database, emptyMap(), emptyList()), records, zone)
        }

    /**
     * Saves every row of [records] that isn't a duplicate, all in one database transaction, so a
     * failure leaves nothing half imported. Accounts, categories, payees and tags are matched by
     * name (ignoring case) or created. [categoryAliases] maps lowercase default-category names in
     * each language to their seed key; new categories take colours from [newCategoryColors].
     */
    suspend fun import(
        records: List<TransactionRecord>,
        zone: ZoneId,
        categoryAliases: Map<String, String> = emptyMap(),
        newCategoryColors: List<Int> = emptyList()
    ): ImportResult = db.io { database ->
        database.withTransaction {
            val resolver = ImportResolver(database, categoryAliases, newCategoryColors)
            val duplicate = duplicates(resolver, records, zone)
            val dao = database.transactionDao()
            records.filterIndexed { index, _ -> !duplicate[index] }.forEach { record ->
                val entity = TransactionEntity(
                    amountPaise = record.amountPaise,
                    direction = record.direction,
                    timestamp = record.timestamp,
                    accountId = resolver.accountId(record.account),
                    payeeId = resolver.payeeId(record),
                    categoryId = resolver.categoryId(record.category),
                    note = record.note?.trim()?.ifEmpty { null },
                    referenceNo = record.referenceNo?.trim()?.ifEmpty { null },
                    source = TransactionSource.CSV,
                    rawSms = null
                )
                dao.saveWithTags(entity, resolver.tagIds(record.tags))
            }
            ImportResult(added = duplicate.count { !it }, duplicates = duplicate.count { it })
        }
    }

    /**
     * A row is a duplicate when its reference number is already used on the same account (in the
     * database or earlier in the file): both sides of a transfer can share one. Without one, it's
     * a duplicate of a saved transaction on the same day, in the same direction, with the same
     * amount and payee (or note). Each saved transaction matches one row at most, so two
     * identical cups of tea on one day both import the first time.
     */
    private suspend fun duplicates(
        resolver: ImportResolver,
        records: List<TransactionRecord>,
        zone: ZoneId
    ): List<Boolean> {
        val saved = resolver.database.backupDao().duplicateKeys()
        val references = saved.mapNotNullTo(HashSet()) { row ->
            row.referenceNo?.let { ReferenceKey.of(it, row.accountName) }
        }
        val unmatched = HashMap<DuplicateKey, Int>()
        saved.forEach { row ->
            val key = DuplicateKey.of(
                row.timestamp,
                row.direction,
                row.amountPaise,
                row.party,
                zone
            )
            unmatched[key] = (unmatched[key] ?: 0) + 1
        }
        return records.map { record ->
            val reference = record.referenceNo?.trim()?.ifEmpty { null }
            if (reference != null) {
                !references.add(ReferenceKey.of(reference, record.account))
            } else {
                val party = resolver.existingPayee(record)?.displayName
                    ?: record.payeeName?.ifBlank { null }
                    ?: record.payee?.ifBlank { null }
                    ?: record.note
                val key = DuplicateKey.of(
                    record.timestamp,
                    record.direction,
                    record.amountPaise,
                    party,
                    zone
                )
                val left = unmatched[key] ?: 0
                if (left > 0) unmatched[key] = left - 1
                left > 0
            }
        }
    }

    private data class ReferenceKey(val reference: String, val account: String?) {
        companion object {
            fun of(reference: String, account: String?) =
                ReferenceKey(reference, account?.trim()?.lowercase()?.ifEmpty { null })
        }
    }

    private data class DuplicateKey(
        val day: LocalDate,
        val direction: Direction,
        val amountPaise: Long,
        val party: String?
    ) {
        companion object {
            fun of(
                timestamp: Long,
                direction: Direction,
                amount: Long,
                party: String?,
                zone: ZoneId
            ) = DuplicateKey(
                day = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate(),
                direction = direction,
                amountPaise = amount,
                party = party?.trim()?.lowercase()?.ifEmpty { null }
            )
        }
    }

    private companion object {
        const val LAST_EXPORT_KEY = "last_csv_export"
    }
}
