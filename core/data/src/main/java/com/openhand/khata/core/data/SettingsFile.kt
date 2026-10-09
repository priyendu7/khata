package com.openhand.khata.core.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * What's inside a settings file once it's decrypted (`docs/settings-format.md`). Never the app
 * PIN, the recovery code, the database key or transactions. Every field has a default, so a file
 * from an older Khata still reads; fields this version doesn't know are ignored.
 */
@Serializable
data class SettingsFile(
    val formatVersion: Int = SettingsFormat.VERSION,
    /** ISO-8601 instant, e.g. `2026-10-09T08:30:00Z`. */
    val exportedAt: String = "",
    val appVersion: String = "",
    /** Newest first, the order the engine tries them in. */
    val customParsers: List<CustomParserData> = emptyList(),
    val builtInOverrides: List<BuiltInOverrideData> = emptyList(),
    val ignoreRules: List<IgnoreRuleData> = emptyList(),
    /** Null in a file without them: the switches here stay as they are. */
    val smsFilters: SmsFiltersData? = null,
    val preferences: PreferencesData = PreferencesData(),
    val categories: List<CategoryData> = emptyList(),
    val accounts: List<AccountData> = emptyList(),
    val payees: List<PayeeData> = emptyList(),
    val events: List<EventData> = emptyList()
)

@Serializable
data class CustomParserData(val code: String, val enabled: Boolean = true)

@Serializable
data class BuiltInOverrideData(
    val ruleId: String,
    val enabled: Boolean = true,
    val editedCode: String? = null,
    /** The built-in rule's hash when it was edited (#111). */
    val baseHash: String? = null
)

@Serializable
data class IgnoreRuleData(
    /** `sender` or `template`. */
    val kind: String,
    val header: String,
    val pattern: String? = null,
    val sample: String = "",
    val enabled: Boolean = true
)

@Serializable
data class SmsFiltersData(
    val dropPromotional: Boolean = true,
    val dropGovernment: Boolean = true,
    val onlyService: Boolean = true,
    val noAmount: Boolean = true,
    val noTransactionWord: Boolean = true,
    val notTransaction: Boolean = true
)

/** App preferences. Null means "not in the file": the setting here is left alone. */
@Serializable
data class PreferencesData(
    /** Whether SMS import was on. Import never turns it on: that needs the SMS permission. */
    val smsImport: Boolean? = null,
    val backupReminder: Boolean? = null,
    val backupReminderDays: Int? = null,
    /** `system`, `en` or `hi`. */
    val language: String? = null,
    val appLock: Boolean? = null,
    /** `device` or `pin`. */
    val lockMethod: String? = null,
    /** `immediately`, `30s`, `1m` or `5m`. */
    val lockTimeout: String? = null,
    val blockScreenshots: Boolean? = null
)

/**
 * A default category has a [seedKey], and a [name] only when the user renamed it; one the user
 * made has only a [name].
 */
@Serializable
data class CategoryRef(val seedKey: String? = null, val name: String? = null)

@Serializable
data class CategoryData(
    val seedKey: String? = null,
    val name: String? = null,
    /** `#AARRGGBB`. */
    val color: String,
    val icon: String,
    val archived: Boolean = false
)

@Serializable
data class AccountData(
    val name: String,
    /** `bank`, `credit_card`, `debit_card` or `wallet`. */
    val type: String = "bank",
    val bank: String? = null,
    val last4: String? = null
)

@Serializable
data class PayeeData(
    val identifier: String,
    val displayName: String,
    val defaultCategory: CategoryRef? = null,
    val defaultTags: List<String> = emptyList(),
    val ownAccount: Boolean = false
)

@Serializable
data class EventData(
    val name: String,
    /** ISO dates, both included. */
    val firstDay: String,
    val lastDay: String
)

/** The JSON inside the envelope: UTF-8, unknown fields ignored, newer versions refused. */
object SettingsFormat {
    const val VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(file: SettingsFile): ByteArray =
        json.encodeToString(SettingsFile.serializer(), file).toByteArray(Charsets.UTF_8)

    /** @throws SettingsFileException for a newer format, or JSON that isn't a settings file. */
    fun decode(bytes: ByteArray): SettingsFile {
        val element = try {
            json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject
        } catch (_: SerializationException) {
            null
        }
        val version = element?.get("formatVersion")?.let { it as? JsonPrimitive }?.intOrNull
        if (version != null && version > VERSION) {
            throw SettingsFileException(SettingsFileError.NEWER_VERSION)
        }
        return element?.takeIf { version != null && version >= 1 }?.let(::read)
            ?: throw SettingsFileException(SettingsFileError.DAMAGED)
    }

    // SerializationException is an IllegalArgumentException: a field of the wrong type.
    private fun read(element: JsonObject): SettingsFile? = try {
        json.decodeFromJsonElement(SettingsFile.serializer(), element)
    } catch (_: IllegalArgumentException) {
        null
    }
}
