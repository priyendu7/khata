package com.openhand.khata.core.data

import com.openhand.khata.core.database.CustomParserEntity
import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.model.CustomParser
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Parser rules the user pasted in Settings > Parsers (PRD feature 8), stored as rule codes in the
 * encrypted database. Checking a code is up to `:sms:parser`; this only keeps what passed.
 */
@Singleton
class CustomParserRepository @Inject constructor(private val db: Lazy<KhataDatabase>) {
    /** Newest first, the order the engine tries them in. */
    fun observeAll(): Flow<List<CustomParser>> = db.observe { it.customParserDao().observeAll() }
        .map { rows ->
            rows.map { CustomParser(it.id, it.ruleId, it.bank, it.code, it.enabled, it.addedAt) }
        }

    /** The codes of the rules that are switched on, newest first. */
    suspend fun enabledCodes(): List<String> = db.io { it.customParserDao().enabledCodes() }

    /**
     * Saves a checked rule, switched on. A rule with the same [ruleId] is replaced, so pasting a
     * fixed version of a rule updates it instead of adding a second one.
     */
    suspend fun save(ruleId: String, bank: String, code: String, now: Long) {
        db.io { database ->
            val dao = database.customParserDao()
            val existing = dao.idOf(ruleId) ?: 0
            dao.upsert(
                CustomParserEntity(
                    id = existing,
                    ruleId = ruleId,
                    bank = bank,
                    code = code,
                    addedAt = now
                )
            )
        }
    }

    /**
     * Saves an edited rule over the one with the same [ruleId], keeping its place in the order
     * and its switch. Saves it as new if that rule was deleted meanwhile.
     */
    suspend fun edit(ruleId: String, bank: String, code: String, now: Long) {
        val updated = db.io { it.customParserDao().updateCode(ruleId, bank, code) }
        if (updated == 0) save(ruleId, bank, code, now)
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        db.io { it.customParserDao().setEnabled(id, enabled) }
    }

    suspend fun delete(id: Long) {
        db.io { it.customParserDao().deleteById(id) }
    }
}
