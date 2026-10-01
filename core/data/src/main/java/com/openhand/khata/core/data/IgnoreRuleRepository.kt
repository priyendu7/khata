package com.openhand.khata.core.data

import com.openhand.khata.core.database.IgnoreRuleEntity
import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.model.IgnoreKind
import com.openhand.khata.core.model.SmsIgnoreRule
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * "Ignore this sender" and "ignore messages like this" rules (PRD feature 7), in the encrypted
 * database. Building and matching a pattern is up to `:sms:parser`; this only keeps them.
 */
@Singleton
class IgnoreRuleRepository @Inject constructor(private val db: Lazy<KhataDatabase>) {
    /** Newest first. */
    fun observeAll(): Flow<List<SmsIgnoreRule>> = db.observe { it.ignoreRuleDao().observeAll() }
        .map { rows -> rows.map(IgnoreRuleEntity::toModel) }

    /** The rules that are switched on, which SMS import uses. */
    suspend fun enabled(): List<SmsIgnoreRule> =
        db.io { database -> database.ignoreRuleDao().enabled().map(IgnoreRuleEntity::toModel) }

    /** Saves a rule, switched on. [pattern] is null for [IgnoreKind.SENDER]. Returns its id. */
    suspend fun add(
        kind: IgnoreKind,
        header: String,
        pattern: String?,
        sample: String,
        now: Long
    ): Long = db.io { database ->
        database.ignoreRuleDao().insert(
            IgnoreRuleEntity(
                kind = kind,
                header = header,
                pattern = pattern,
                sample = sample,
                createdAt = now
            )
        )
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        db.io { it.ignoreRuleDao().setEnabled(id, enabled) }
    }

    suspend fun delete(id: Long) {
        db.io { it.ignoreRuleDao().deleteById(id) }
    }
}

private fun IgnoreRuleEntity.toModel() =
    SmsIgnoreRule(id, kind, header, pattern, sample, enabled, createdAt)
