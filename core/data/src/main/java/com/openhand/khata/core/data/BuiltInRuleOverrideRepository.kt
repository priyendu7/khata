package com.openhand.khata.core.data

import com.openhand.khata.core.database.BuiltInRuleOverrideEntity
import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.model.BuiltInRuleOverride
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The user's changes to built-in parser rules (Settings > Parsers, #111): switched off, or edited.
 * A row that changes nothing is removed, so the table only holds real changes.
 */
@Singleton
class BuiltInRuleOverrideRepository @Inject constructor(private val db: Lazy<KhataDatabase>) {
    fun observeAll(): Flow<List<BuiltInRuleOverride>> =
        db.observe { it.builtInRuleOverrideDao().observeAll() }.map { rows -> rows.map(::model) }

    suspend fun getAll(): List<BuiltInRuleOverride> =
        db.io { it.builtInRuleOverrideDao().getAll() }.map(::model)

    suspend fun setEnabled(ruleId: String, enabled: Boolean) = change(ruleId) {
        it.copy(enabled = enabled)
    }

    /** Stores [code] as the rule's edit, made from the built-in rule with hash [baseHash]. */
    suspend fun saveEdit(ruleId: String, code: String, baseHash: String) = change(ruleId) {
        it.copy(editedCode = code, baseHash = baseHash)
    }

    /** Back to the built-in rule (Reset to built-in, or Use new version). The switch stays. */
    suspend fun clearEdit(ruleId: String) = change(ruleId) {
        it.copy(editedCode = null, baseHash = null)
    }

    /** Keep mine: the edit stays, and the update to the built-in rule is no longer offered. */
    suspend fun keepEdit(ruleId: String, newHash: String) = change(ruleId) {
        it.copy(baseHash = newHash)
    }

    private suspend fun change(
        ruleId: String,
        update: (BuiltInRuleOverrideEntity) -> BuiltInRuleOverrideEntity
    ) {
        db.io { database ->
            val dao = database.builtInRuleOverrideDao()
            val next = update(dao.get(ruleId) ?: BuiltInRuleOverrideEntity(ruleId))
            if (next.enabled && next.editedCode == null) dao.delete(ruleId) else dao.upsert(next)
        }
    }

    private fun model(row: BuiltInRuleOverrideEntity) =
        BuiltInRuleOverride(row.ruleId, row.enabled, row.editedCode, row.baseHash)
}
