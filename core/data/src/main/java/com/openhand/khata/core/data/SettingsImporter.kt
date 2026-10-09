package com.openhand.khata.core.data

import com.openhand.khata.core.database.BuiltInRuleOverrideEntity
import com.openhand.khata.core.database.CustomParserEntity
import com.openhand.khata.core.database.EventEntity
import com.openhand.khata.core.database.EventRow
import com.openhand.khata.core.database.IgnoreRuleEntity
import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.database.entity.AccountEntity
import com.openhand.khata.core.database.entity.CategoryEntity
import com.openhand.khata.core.database.entity.EnumConverters
import com.openhand.khata.core.database.entity.PayeeEntity
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.DefaultCategory
import com.openhand.khata.core.model.IgnoreKind
import com.openhand.khata.sms.parser.CodeCheck
import com.openhand.khata.sms.parser.CustomRules
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * Compares a settings file with the database ([preview]) and saves it ([apply]), with the same
 * matching for both. [builtInHashes] maps each built-in rule id to the hash of the rule as
 * shipped. [apply] must run inside a database transaction.
 */
internal class SettingsImporter(
    private val database: KhataDatabase,
    private val file: SettingsFile,
    private val builtInHashes: Map<String, String>
) {
    /** A custom rule from the file, with the saved rule of the same id if there is one. */
    private class CustomPlan(
        val ruleId: String,
        val bank: String,
        val data: CustomParserData,
        val saved: CustomParserEntity?
    ) {
        val status = when {
            saved == null -> RuleStatus.NEW
            saved.code == data.code && saved.enabled == data.enabled -> RuleStatus.SAME
            else -> RuleStatus.CHANGED
        }
    }

    /** A built-in rule's change from the file; a null [wanted] means "as shipped". */
    private class BuiltInPlan(
        val ruleId: String,
        val wanted: BuiltInRuleOverrideEntity?,
        val saved: BuiltInRuleOverrideEntity?
    ) {
        val status = when {
            saved == wanted -> RuleStatus.SAME
            saved == null -> RuleStatus.NEW
            else -> RuleStatus.CHANGED
        }
    }

    suspend fun preview(): SettingsPreview {
        val custom = customPlans()
        val builtIn = builtInPlans()
        val newIgnoreRules = newIgnoreRules().size
        return SettingsPreview(
            customRules = custom.map { RuleComparison(it.ruleId, it.status, it.data.enabled) },
            unreadableCustomRules = file.customParsers.count { ruleIdOf(it.code) == null },
            builtInEdits = builtIn.map {
                RuleComparison(it.ruleId, it.status, it.wanted?.enabled ?: true)
            },
            unknownBuiltIns = file.builtInOverrides.map { it.ruleId }
                .filter { it !in builtInHashes }.distinct(),
            olderBuiltIns = builtIn.mapNotNull { plan ->
                plan.wanted?.takeIf { it.editedCode != null }
                    ?.takeIf { it.baseHash != builtInHashes[it.ruleId] }?.ruleId
            },
            ignoreRules = MatchCounts(
                new = newIgnoreRules,
                same = file.ignoreRules.size - newIgnoreRules
            ),
            categories = counts(
                file.categories.map { category ->
                    findCategory(category.seedKey, category.name, categories())
                        ?.let { updated(it, category) == it }
                }
            ),
            accounts = counts(
                file.accounts.map { account ->
                    findAccount(account.name)?.let { updated(it, account) == it }
                }
            ),
            payees = counts(file.payees.map { findPayee(it)?.let { saved -> isSame(saved, it) } }),
            events = counts(
                file.events.filter { it.name.isNotBlank() }
                    .mapNotNull { event -> days(event)?.let { event.name to it } }
                    .map { (name, days) ->
                        findEvent(name)?.let {
                            it.startDay == days.first && it.endDay == days.second
                        }
                    }
            )
        )
    }

    suspend fun apply(choices: RuleChoices, zone: ZoneId, now: Long) {
        applyRules(choices, now)
        applyIgnoreRules(now)
        applyCategories()
        applyAccounts()
        file.payees.forEach { applyPayee(it) }
        file.events.forEach { applyEvent(it, zone) }
    }

    private suspend fun applyRules(choices: RuleChoices, now: Long) {
        val dao = database.customParserDao()
        // The file lists them newest first; they stay in that order, ahead of the rules here.
        customPlans().forEachIndexed { index, plan ->
            val saved = plan.saved
            when {
                saved == null -> dao.upsert(
                    CustomParserEntity(
                        ruleId = plan.ruleId,
                        bank = plan.bank,
                        code = plan.data.code,
                        enabled = plan.data.enabled,
                        addedAt = now - index
                    )
                )
                plan.status == RuleStatus.CHANGED && plan.ruleId !in choices.keepCustom -> {
                    dao.updateCode(plan.ruleId, plan.bank, plan.data.code)
                    dao.setEnabled(saved.id, plan.data.enabled)
                }
            }
        }
        val overrides = database.builtInRuleOverrideDao()
        builtInPlans().filter {
            it.status == RuleStatus.NEW ||
                (it.status == RuleStatus.CHANGED && it.ruleId !in choices.keepBuiltIn)
        }.forEach { plan ->
            plan.wanted?.let { overrides.upsert(it) } ?: overrides.delete(plan.ruleId)
        }
    }

    private suspend fun applyIgnoreRules(now: Long) {
        val dao = database.ignoreRuleDao()
        newIgnoreRules().forEachIndexed { index, (kind, rule) ->
            dao.insert(
                IgnoreRuleEntity(
                    kind = kind,
                    header = rule.header,
                    pattern = rule.pattern.takeIf { kind == IgnoreKind.TEMPLATE },
                    sample = rule.sample,
                    enabled = rule.enabled,
                    // Newest first, like the file, and ahead of the rules here.
                    createdAt = now - index
                )
            )
        }
    }

    private suspend fun applyCategories() {
        val dao = database.categoryDao()
        file.categories.forEach { data ->
            val all = dao.getAll()
            val saved = findCategory(data.seedKey, data.name, all)
            when {
                saved != null -> updated(saved, data).takeIf { it != saved }?.let { dao.update(it) }
                !data.name.isNullOrBlank() || data.seedKey != null -> dao.insert(
                    updated(
                        CategoryEntity(
                            name = data.name,
                            seedKey = data.seedKey,
                            color = DefaultCategory.UNCATEGORIZED.color,
                            icon = DefaultCategory.UNCATEGORIZED.icon
                        ),
                        data
                    )
                )
            }
        }
    }

    private suspend fun applyAccounts() {
        val dao = database.accountDao()
        file.accounts.filter { it.name.isNotBlank() }.forEach { data ->
            val saved = findAccount(data.name)
            if (saved == null) {
                val new = AccountEntity(
                    name = data.name.trim(),
                    type = AccountType.BANK,
                    bank = null,
                    last4 = null
                )
                dao.insert(updated(new, data))
            } else {
                updated(saved, data).takeIf { it != saved }?.let { dao.update(it) }
            }
        }
    }

    private suspend fun applyPayee(data: PayeeData) {
        val identifier = data.identifier.trim()
        val name = data.displayName.trim().ifEmpty { identifier }
        if (identifier.isEmpty()) return
        val dao = database.payeeDao()
        val categoryId = data.defaultCategory?.let {
            findCategory(it.seedKey, it.name, database.categoryDao().getAll())?.id
        }
        val tagIds = database.tagDao().getOrCreate(data.defaultTags)
        val saved = findPayee(data)
        if (saved == null) {
            val id = dao.insert(
                PayeeEntity(
                    identifier = identifier,
                    displayName = name,
                    defaultCategoryId = categoryId,
                    ownAccount = data.ownAccount
                )
            )
            dao.setDefaultTags(id, tagIds)
        } else if (!isSame(saved, data)) {
            dao.updateWithDefaultTags(
                saved.copy(
                    displayName = name,
                    defaultCategoryId = categoryId,
                    ownAccount = data.ownAccount
                ),
                tagIds
            )
        }
    }

    /** Moves the event's tag off transactions in its old dates and onto ones in its new dates. */
    private suspend fun applyEvent(data: EventData, zone: ZoneId) {
        val name = data.name.trim()
        val (first, last) = days(data)?.takeIf { name.isNotEmpty() } ?: return
        val dao = database.eventDao()
        val saved = findEvent(name)
        if (saved == null) {
            val tagId = database.tagDao().getOrCreate(listOf(name)).single()
            dao.insert(EventEntity(tagId = tagId, startDay = first, endDay = last))
            dao.tagRange(tagId, millis(first, zone), millis(last + 1, zone))
        } else if (saved.startDay != first || saved.endDay != last) {
            val tagId = saved.tagId
            dao.untagRange(tagId, millis(saved.startDay, zone), millis(saved.endDay + 1, zone))
            dao.update(EventEntity(saved.id, tagId, first, last))
            dao.tagRange(tagId, millis(first, zone), millis(last + 1, zone))
        }
    }

    private suspend fun customPlans(): List<CustomPlan> {
        val dao = database.customParserDao()
        return file.customParsers.mapNotNull { data ->
            (CustomRules.check(data.code) as? CodeCheck.Valid)?.rule?.rule?.let { rule ->
                CustomPlan(rule.id, rule.bank, data, dao.getByRuleId(rule.id))
            }
        }.distinctBy { it.ruleId }
    }

    private suspend fun builtInPlans(): List<BuiltInPlan> {
        val dao = database.builtInRuleOverrideDao()
        return file.builtInOverrides.filter { it.ruleId in builtInHashes }
            .distinctBy { it.ruleId }
            .map { data ->
                val wanted = BuiltInRuleOverrideEntity(
                    ruleId = data.ruleId,
                    enabled = data.enabled,
                    editedCode = data.editedCode,
                    baseHash = data.baseHash.takeIf { data.editedCode != null }
                ).takeUnless { it.enabled && it.editedCode == null }
                BuiltInPlan(data.ruleId, wanted, dao.get(data.ruleId))
            }
    }

    /** Rules not already here: the same sender, or the same pattern, is a duplicate. */
    private suspend fun newIgnoreRules(): List<Pair<IgnoreKind, IgnoreRuleData>> {
        val seen = database.ignoreRuleDao().getAll()
            .mapTo(HashSet()) { ignoreKey(it.kind, it.header, it.pattern) }
        return file.ignoreRules.mapNotNull { rule ->
            runCatching { converters.toIgnoreKind(rule.kind) }.getOrNull()?.takeIf { kind ->
                rule.header.isNotBlank() &&
                    (kind == IgnoreKind.SENDER || !rule.pattern.isNullOrBlank()) &&
                    seen.add(ignoreKey(kind, rule.header, rule.pattern))
            }?.let { it to rule }
        }
    }

    private var categoryCache: List<CategoryEntity>? = null

    private suspend fun categories(): List<CategoryEntity> =
        categoryCache ?: database.categoryDao().getAll().also { categoryCache = it }

    private suspend fun findAccount(name: String): AccountEntity? =
        name.trim().ifEmpty { null }?.let { database.accountDao().getByName(it) }

    private suspend fun findPayee(data: PayeeData): PayeeEntity? {
        val dao = database.payeeDao()
        val identifier = data.identifier.trim().ifEmpty { return null }
        return dao.getByIdentifier(identifier)
            ?: data.displayName.trim().ifEmpty { null }?.let { dao.findByName(it) }
    }

    private suspend fun findEvent(name: String): EventRow? =
        database.eventDao().getAll().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    private suspend fun isSame(saved: PayeeEntity, data: PayeeData): Boolean {
        val categoryId = data.defaultCategory
            ?.let { findCategory(it.seedKey, it.name, categories())?.id }
        val tags = database.payeeDao().defaultTagNames(saved.id).map { it.lowercase() }.toSet()
        val wantedTags = data.defaultTags.map { it.trim().lowercase() }
            .filterTo(HashSet(), String::isNotEmpty)
        return saved.displayName == data.displayName.trim().ifEmpty { saved.identifier } &&
            saved.defaultCategoryId == categoryId &&
            saved.ownAccount == data.ownAccount &&
            tags == wantedTags
    }

    private companion object {
        const val LAST_DIGITS = 4
        val converters = EnumConverters()

        fun ignoreKey(kind: IgnoreKind, header: String, pattern: String?) = when (kind) {
            IgnoreKind.TEMPLATE -> "t:$pattern"
            else -> "s:${header.trim().lowercase()}"
        }

        /** A default category's file entry sets its name too: null shows the default name again. */
        fun updated(saved: CategoryEntity, data: CategoryData) = saved.copy(
            name = if (saved.seedKey != null) data.name?.trim()?.ifEmpty { null } else saved.name,
            color = parseColor(data.color) ?: saved.color,
            icon = data.icon.ifBlank { saved.icon },
            archived = data.archived
        )

        fun updated(saved: AccountEntity, data: AccountData) = saved.copy(
            type = runCatching { converters.toAccountType(data.type) }.getOrDefault(saved.type),
            bank = data.bank?.trim()?.ifEmpty { null },
            // Only ever the last 4 digits, whatever the file says.
            last4 = data.last4?.filter(Char::isDigit)?.takeLast(LAST_DIGITS)?.ifEmpty { null }
        )

        fun days(event: EventData): Pair<Long, Long>? = try {
            val first = LocalDate.parse(event.firstDay).toEpochDay()
            val last = LocalDate.parse(event.lastDay).toEpochDay()
            minOf(first, last) to maxOf(first, last)
        } catch (_: DateTimeParseException) {
            null
        }

        fun millis(epochDay: Long, zone: ZoneId) =
            LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()

        fun ruleIdOf(code: String): String? =
            (CustomRules.check(code) as? CodeCheck.Valid)?.rule?.rule?.id

        /** null → new, true → the same, false → will be updated. */
        fun counts(matches: List<Boolean?>) = MatchCounts(
            new = matches.count { it == null },
            updated = matches.count { it == false },
            same = matches.count { it == true }
        )

        fun findCategory(
            seedKey: String?,
            name: String?,
            all: List<CategoryEntity>
        ): CategoryEntity? = seedKey?.let { key -> all.firstOrNull { it.seedKey == key } }
            ?: name?.trim()?.let { wanted ->
                all.firstOrNull { it.name.equals(wanted, ignoreCase = true) }
            }
    }
}
