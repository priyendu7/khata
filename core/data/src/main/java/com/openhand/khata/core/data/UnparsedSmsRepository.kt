package com.openhand.khata.core.data

import androidx.room.withTransaction
import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.database.UnparsedSmsEntity
import com.openhand.khata.core.model.SenderId
import com.openhand.khata.core.model.UnparsedSms
import com.openhand.khata.core.model.UnparsedSmsGroup
import dagger.Lazy
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Bank SMS that no rule could read (PRD feature 7): kept with their raw text for the review
 * inbox, so a new format is noticed, until the user adds them by hand or dismisses them.
 */
@Singleton
class UnparsedSmsRepository @Inject constructor(private val db: Lazy<KhataDatabase>) {
    /** Newest first. */
    fun observeAll(): Flow<List<UnparsedSms>> = db.observe { it.unparsedSmsDao().observeAll() }
        .map { rows -> rows.map(UnparsedSmsEntity::toModel) }

    /**
     * Grouped by sender header, whatever the operator prefix, so a past scan's many SMS from one
     * sender are one card. The group with the newest SMS comes first.
     */
    fun observeGroups(): Flow<List<UnparsedSmsGroup>> = observeAll().map { newestFirst ->
        newestFirst.groupBy { SenderId.parse(it.sender)?.header ?: it.sender }
            .map { (header, messages) -> UnparsedSmsGroup(header, messages) }
    }

    /** Oldest first, to read again with a new parser rule. */
    suspend fun getAll(): List<UnparsedSms> =
        db.io { database -> database.unparsedSmsDao().getAll().map(UnparsedSmsEntity::toModel) }

    /** Null if it's been dismissed or recorded since. */
    suspend fun get(id: Long): UnparsedSms? =
        db.io { database -> database.unparsedSmsDao().getById(id)?.toModel() }

    /**
     * Stores an SMS from a known bank that no rule read. The same text seen again within a day
     * (by both the receiver and the inbox import) is stored once. Returns false for a repeat.
     */
    suspend fun save(sender: String, body: String, receivedAt: Long): Boolean = db.io { database ->
        val dao = database.unparsedSmsDao()
        val repeat = dao.findSame(body, receivedAt - SAME_SMS_WINDOW, receivedAt + SAME_SMS_WINDOW)
        if (repeat ==
            null
        ) {
            dao.insert(UnparsedSmsEntity(sender = sender, body = body, receivedAt = receivedAt))
        }
        repeat == null
    }

    /** Deletes it: dismissed, or added by hand. Nothing is kept. */
    suspend fun delete(id: Long) {
        db.io { it.unparsedSmsDao().deleteById(id) }
    }

    /** Deletes them all at once: a group dismissed. */
    suspend fun delete(ids: Collection<Long>) {
        db.io { database ->
            database.withTransaction {
                // SQLite allows 999 variables in one statement on older phones.
                ids.chunked(MAX_IDS_PER_DELETE).forEach {
                    database.unparsedSmsDao().deleteByIds(it)
                }
            }
        }
    }

    private companion object {
        val SAME_SMS_WINDOW = TimeUnit.DAYS.toMillis(1)
        const val MAX_IDS_PER_DELETE = 500
    }
}

private fun UnparsedSmsEntity.toModel() = UnparsedSms(id, sender, body, receivedAt)
