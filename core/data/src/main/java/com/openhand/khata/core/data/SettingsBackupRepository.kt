package com.openhand.khata.core.data

import androidx.room.withTransaction
import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.database.entity.EnumConverters
import com.openhand.khata.sms.parser.BuiltInRules
import dagger.Lazy
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How a parser rule in a settings file compares with this phone's. */
enum class RuleStatus { NEW, CHANGED, SAME }

data class RuleComparison(val ruleId: String, val status: RuleStatus, val enabled: Boolean)

/** Of one kind of setup data in a file: how many are new, will be updated, or already match. */
data class MatchCounts(val new: Int = 0, val updated: Int = 0, val same: Int = 0)

/** What importing a settings file will do, shown before anything is saved. */
data class SettingsPreview(
    val customRules: List<RuleComparison> = emptyList(),
    /** Custom rule codes this Khata can't read, or that fail its checks: skipped. */
    val unreadableCustomRules: Int = 0,
    val builtInEdits: List<RuleComparison> = emptyList(),
    /** Built-in rules the file has changes for that this Khata doesn't have: skipped. */
    val unknownBuiltIns: List<String> = emptyList(),
    /** Edits made to another version of the built-in rule: they'll show "Updated version". */
    val olderBuiltIns: List<String> = emptyList(),
    /** [MatchCounts.same] counts the duplicates, which are skipped. */
    val ignoreRules: MatchCounts = MatchCounts(),
    val categories: MatchCounts = MatchCounts(),
    val accounts: MatchCounts = MatchCounts(),
    val payees: MatchCounts = MatchCounts(),
    val events: MatchCounts = MatchCounts()
)

/** Rule ids where the user chose Keep mine; every other changed rule is replaced. */
data class RuleChoices(
    val keepCustom: Set<String> = emptySet(),
    val keepBuiltIn: Set<String> = emptySet()
)

/**
 * The settings file (#125): gathers the rules and setup data that the CSV doesn't carry, and puts
 * them back. Preferences outside the database are read and applied by the caller; encryption is
 * [SettingsCrypto]'s job. Matching works like CSV import ([ImportResolver]): by name, ignoring
 * case.
 */
@Singleton
class SettingsBackupRepository @Inject constructor(private val db: Lazy<KhataDatabase>) {
    /** Runs just before the import commits. Tests make it throw, to check that nothing is kept. */
    internal var beforeCommit: suspend () -> Unit = {}

    /** The database's part of a settings file, read in one transaction. */
    suspend fun gather(): SettingsFile = db.io { database ->
        database.withTransaction { gather(database) }
    }

    suspend fun preview(file: SettingsFile): SettingsPreview = db.io { database ->
        SettingsImporter(database, file, builtInHashes()).preview()
    }

    /**
     * Saves everything in [file] in one database transaction, so a failure leaves nothing half
     * imported. Changed rules are replaced unless [choices] keeps them. Event days are read in
     * [zone]; new custom rules count as added at [now].
     */
    suspend fun apply(file: SettingsFile, choices: RuleChoices, zone: ZoneId, now: Long) {
        val hashes = builtInHashes()
        db.io { database ->
            database.withTransaction {
                SettingsImporter(database, file, hashes).apply(choices, zone, now)
                beforeCommit()
            }
        }
    }

    private suspend fun gather(database: KhataDatabase): SettingsFile {
        val converters = EnumConverters()
        val categories = database.categoryDao().getAll()
        val categoryRefs = categories.associate { it.id to CategoryRef(it.seedKey, it.name) }
        val payees = database.payeeDao()
        return SettingsFile(
            customParsers = database.customParserDao().getAll()
                .map { CustomParserData(it.code, it.enabled) },
            builtInOverrides = database.builtInRuleOverrideDao().getAll().map {
                BuiltInOverrideData(it.ruleId, it.enabled, it.editedCode, it.baseHash)
            },
            ignoreRules = database.ignoreRuleDao().getAll().map {
                IgnoreRuleData(
                    kind = converters.fromIgnoreKind(it.kind),
                    header = it.header,
                    pattern = it.pattern,
                    sample = it.sample,
                    enabled = it.enabled
                )
            },
            categories = categories.map {
                CategoryData(it.seedKey, it.name, colorHex(it.color), it.icon, it.archived)
            },
            accounts = database.accountDao().getAll().map {
                AccountData(it.name, converters.fromAccountType(it.type), it.bank, it.last4)
            },
            payees = payees.getAll().map {
                PayeeData(
                    identifier = it.identifier,
                    displayName = it.displayName,
                    defaultCategory = it.defaultCategoryId?.let(categoryRefs::get),
                    defaultTags = payees.defaultTagNames(it.id),
                    ownAccount = it.ownAccount
                )
            },
            events = database.eventDao().getAll().map {
                EventData(
                    name = it.name,
                    firstDay = LocalDate.ofEpochDay(it.startDay).toString(),
                    lastDay = LocalDate.ofEpochDay(it.endDay).toString()
                )
            }
        )
    }

    /** Built-in rule id → the hash of the rule as shipped in this version. */
    private suspend fun builtInHashes(): Map<String, String> = withContext(Dispatchers.Default) {
        BuiltInRules.all().associate { it.id to BuiltInRules.hash(it) }
    }
}

internal fun colorHex(color: Int): String = "#%08X".format(color)

internal fun parseColor(hex: String): Int? =
    hex.removePrefix("#").takeIf { it.length == ARGB_DIGITS }?.toLongOrNull(HEX)?.toInt()

private const val ARGB_DIGITS = 8
private const val HEX = 16
