package com.openhand.khata.core.data

import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.database.UnparsedSmsEntity
import com.openhand.khata.core.model.UnparsedSms
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

    /** Oldest first, to read again with a new parser rule. */
    suspend fun getAll(): List<UnparsedSms> =
        db.io { database -> database.unparsedSmsDao().getAll().map(UnparsedSmsEntity::toModel) }

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

    private companion object {
        val SAME_SMS_WINDOW = TimeUnit.DAYS.toMillis(1)
    }
}

private fun UnparsedSmsEntity.toModel() = UnparsedSms(id, sender, body, receivedAt)
