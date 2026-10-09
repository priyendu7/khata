package com.openhand.khata.core.data

import com.openhand.khata.core.database.BuiltInRuleOverrideEntity
import com.openhand.khata.core.database.CustomParserEntity
import com.openhand.khata.core.database.IgnoreRuleEntity
import com.openhand.khata.core.database.entity.AccountEntity
import com.openhand.khata.core.database.entity.CategoryEntity
import com.openhand.khata.core.database.entity.PayeeEntity
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.Event
import com.openhand.khata.core.model.IgnoreKind
import com.openhand.khata.sms.parser.BuiltInRules
import com.openhand.khata.sms.parser.RuleCode
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsBackupRepositoryTest : RepositoryTest() {
    private val repository by lazy { SettingsBackupRepository(lazyDb) }
    private val zone = ZoneId.of("Asia/Kolkata")
    private val builtIn = BuiltInRules.all()

    private fun code(id: String, bank: String = "Test Bank") =
        RuleCode.encode(builtIn.first().copy(id = id, bank = bank))

    private suspend fun addCustom(id: String, code: String, enabled: Boolean = true, at: Long = 1) =
        db.customParserDao().upsert(
            CustomParserEntity(
                ruleId = id,
                bank = "Test Bank",
                code = code,
                enabled = enabled,
                addedAt = at
            )
        )

    private suspend fun apply(file: SettingsFile, choices: RuleChoices = RuleChoices()) =
        repository.apply(file, choices, zone, now = 1_000)

    @Test
    fun exportHasEveryPartAndNoSecrets() = runTest {
        setUpEverything()

        val file = repository.gather()

        assertEquals(listOf(CustomParserData(code("my-rule"), enabled = false)), file.customParsers)
        assertEquals(
            listOf(BuiltInOverrideData(builtIn.first().id, enabled = false)),
            file.builtInOverrides
        )
        assertEquals(
            listOf(IgnoreRuleData("sender", "SPAMMY", null, "Win!", true)),
            file.ignoreRules
        )
        assertTrue(
            CategoryData("food", "Khana", "#FFE65100", "food", archived = true) in file.categories
        )
        assertTrue(CategoryData(null, "Pets", "#7F00FF00", "pets") in file.categories)
        assertEquals(listOf(AccountData("HDFC card", "credit_card", "HDFC", "4321")), file.accounts)
        assertEquals(
            listOf(PayeeData("vet@upi", "Vet", CategoryRef(null, "Pets"), listOf("dog"), false)),
            file.payees
        )
        assertEquals(listOf(EventData("Goa", "2026-10-01", "2026-10-05")), file.events)

        // The whole file, preferences too, as it's written: only the documented parts.
        val json = Json.parseToJsonElement(
            SettingsFormat.encode(
                file.copy(preferences = PreferencesData(lockMethod = "pin", appLock = true))
            )
                .toString(Charsets.UTF_8)
        ).jsonObject
        assertEquals(
            setOf(
                "formatVersion", "exportedAt", "appVersion", "customParsers", "builtInOverrides",
                "ignoreRules", "preferences", "categories", "accounts", "payees", "events"
            ),
            json.keys
        )
        assertEquals(setOf("appLock", "lockMethod"), json.getValue("preferences").jsonObject.keys)
        val text = json.toString().lowercase()
        listOf("pin_hash", "recovery", "pinhash", "databasekey", "wrapped").forEach {
            assertTrue("$it in the file", it !in text)
        }
    }

    @Test
    fun previewClassifiesCustomRules() = runTest {
        addCustom("same-rule", code("same-rule"))
        addCustom("changed-rule", code("changed-rule"))
        val file = SettingsFile(
            customParsers = listOf(
                CustomParserData(code("same-rule")),
                CustomParserData(code("changed-rule", bank = "Other Bank")),
                CustomParserData(code("new-rule"), enabled = false),
                CustomParserData("khata1:not-a-rule")
            )
        )

        val preview = repository.preview(file)

        assertEquals(
            listOf(
                RuleComparison("same-rule", RuleStatus.SAME, true),
                RuleComparison("changed-rule", RuleStatus.CHANGED, true),
                RuleComparison("new-rule", RuleStatus.NEW, false)
            ),
            preview.customRules
        )
        assertEquals(1, preview.unreadableCustomRules)
    }

    @Test
    fun replaceAndKeepMineAreHonouredPerRule() = runTest {
        addCustom("replace-me", code("replace-me"), at = 1)
        addCustom("keep-me", code("keep-me"), enabled = false, at = 2)
        val file = SettingsFile(
            customParsers = listOf(
                CustomParserData(code("new-rule")),
                CustomParserData(code("replace-me", bank = "New Bank"), enabled = false),
                CustomParserData(code("keep-me", bank = "New Bank"))
            )
        )

        apply(file, RuleChoices(keepCustom = setOf("keep-me")))

        val saved = db.customParserDao().getAll().associateBy { it.ruleId }
        assertEquals(code("replace-me", bank = "New Bank"), saved.getValue("replace-me").code)
        assertEquals(false, saved.getValue("replace-me").enabled)
        assertEquals(code("keep-me"), saved.getValue("keep-me").code)
        assertEquals(false, saved.getValue("keep-me").enabled)
        assertEquals(true, saved.getValue("new-rule").enabled)
    }

    @Test
    fun builtInEditsAreClassifiedAndChosenPerRule() = runTest {
        val (first, second) = builtIn.take(2)
        val overrides = db.builtInRuleOverrideDao()
        overrides.upsert(BuiltInRuleOverrideEntity(first.id, enabled = false))
        overrides.upsert(BuiltInRuleOverrideEntity(second.id, enabled = false))
        val edit = RuleCode.encode(first.copy(bank = "Edited"))
        val file = SettingsFile(
            builtInOverrides = listOf(
                BuiltInOverrideData(first.id, true, edit, "an-older-hash"),
                BuiltInOverrideData(
                    second.id,
                    true,
                    RuleCode.encode(second),
                    BuiltInRules.hash(second)
                ),
                BuiltInOverrideData(builtIn[2].id, enabled = false),
                BuiltInOverrideData("no-such-rule", enabled = false)
            )
        )

        val preview = repository.preview(file)

        assertEquals(
            listOf(RuleStatus.CHANGED, RuleStatus.CHANGED, RuleStatus.NEW),
            preview.builtInEdits.map { it.status }
        )
        assertEquals(listOf("no-such-rule"), preview.unknownBuiltIns)
        assertEquals(listOf(first.id), preview.olderBuiltIns)

        apply(file, RuleChoices(keepBuiltIn = setOf(second.id)))

        assertEquals(
            BuiltInRuleOverrideEntity(first.id, true, edit, "an-older-hash"),
            overrides.get(first.id)
        )
        assertEquals(
            BuiltInRuleOverrideEntity(second.id, enabled = false),
            overrides.get(second.id)
        )
        assertEquals(
            BuiltInRuleOverrideEntity(builtIn[2].id, enabled = false),
            overrides.get(builtIn[2].id)
        )
        assertNull(overrides.get("no-such-rule"))
    }

    @Test
    fun ignoreRulesSkipTheSameSenderOrPattern() = runTest {
        db.ignoreRuleDao().insert(
            IgnoreRuleEntity(
                kind = IgnoreKind.SENDER,
                header = "SPAMMY",
                pattern = null,
                sample = "",
                createdAt = 1
            )
        )
        val file = SettingsFile(
            ignoreRules = listOf(
                IgnoreRuleData("sender", "spammy"),
                IgnoreRuleData("sender", "OFFERS"),
                IgnoreRuleData("template", "BANKX", "Your OTP is .*", "Your OTP is 1234"),
                IgnoreRuleData("template", "BANKY", "Your OTP is .*")
            )
        )

        assertEquals(MatchCounts(new = 2, same = 2), repository.preview(file).ignoreRules)
        apply(file)

        assertEquals(
            listOf("SPAMMY", "OFFERS", "BANKX"),
            db.ignoreRuleDao().getAll().sortedBy { it.id }.map { it.header }
        )
    }

    @Test
    fun setupDataIsMatchedByNameAndUpdated() = runTest {
        val pets = addSetup()
        val file = setupFile()
        val preview = repository.preview(file)

        assertEquals(MatchCounts(new = 1, updated = 2, same = 1), preview.categories)
        assertEquals(MatchCounts(new = 1, updated = 1), preview.accounts)
        assertEquals(MatchCounts(new = 1, updated = 1), preview.payees)
        assertEquals(MatchCounts(new = 1, updated = 1), preview.events)

        apply(file)

        val food = db.categoryDao().getBySeedKey("food")!!
        assertEquals(listOf("Khana", 0xFF000001.toInt()), listOf(food.name, food.color))
        assertEquals(
            CategoryEntity(pets, "Pets", null, 0xFF000002.toInt(), "dog", archived = true),
            db.categoryDao().getById(pets)
        )
        val gym = db.categoryDao().getAll().single { it.name == "Gym" }
        assertEquals(
            AccountEntity(1, "HDFC", AccountType.CREDIT_CARD, "HDFC", "1234"),
            db.accountDao().getByName("HDFC")
        )
        assertEquals(AccountType.WALLET, db.accountDao().getByName("Cash")!!.type)
        val zomato = db.payeeDao().getByIdentifier("zomato@upi")!!
        assertEquals(
            listOf("Zomato Foods", gym.id, true),
            listOf(zomato.displayName, zomato.defaultCategoryId, zomato.ownAccount)
        )
        assertEquals(listOf("Food", "online"), db.payeeDao().defaultTagNames(zomato.id))
        assertEquals(food.id, db.payeeDao().getByIdentifier("swiggy@upi")!!.defaultCategoryId)
        val saved = db.eventDao().getAll().associateBy { it.name }
        assertEquals(LocalDate.of(2026, 10, 2).toEpochDay(), saved.getValue("Goa").startDay)
        assertEquals(LocalDate.of(2026, 11, 8).toEpochDay(), saved.getValue("Diwali").endDay)

        // Importing the same file again changes nothing.
        val again = repository.preview(file)
        assertEquals(MatchCounts(same = 4), again.categories)
        assertEquals(MatchCounts(same = 2), again.payees)
        assertEquals(MatchCounts(same = 2), again.events)
    }

    @Test
    fun aFailureMidImportLeavesTheDatabaseUnchanged() = runTest {
        val before = repository.gather()
        repository.beforeCommit = { error("Disk full") }
        val file = SettingsFile(
            customParsers = listOf(CustomParserData(code("new-rule"))),
            categories = listOf(CategoryData(null, "Gym", "#FF000003", "fitness")),
            accounts = listOf(AccountData("Cash", "wallet")),
            payees = listOf(PayeeData("swiggy@upi", "Swiggy", defaultTags = listOf("food"))),
            events = listOf(EventData("Diwali", "2026-11-08", "2026-11-08"))
        )

        try {
            apply(file)
            fail("The import should have failed")
        } catch (_: IllegalStateException) {
            // Expected.
        }

        assertEquals(before, repository.gather())
        assertNull(db.tagDao().getByName("food"))
    }

    private suspend fun setUpEverything() {
        addCustom("my-rule", code("my-rule"), enabled = false)
        db.builtInRuleOverrideDao().upsert(
            BuiltInRuleOverrideEntity(builtIn.first().id, enabled = false)
        )
        db.ignoreRuleDao().insert(
            IgnoreRuleEntity(
                kind = IgnoreKind.SENDER,
                header = "SPAMMY",
                pattern = null,
                sample = "Win!",
                createdAt = 1
            )
        )
        val food = db.categoryDao().getBySeedKey("food")!!
        db.categoryDao().update(food.copy(name = "Khana", archived = true))
        val pets = db.categoryDao().insert(
            CategoryEntity(name = "Pets", color = 0x7F00FF00, icon = "pets")
        )
        db.accountDao().insert(
            AccountEntity(
                name = "HDFC card",
                type = AccountType.CREDIT_CARD,
                bank = "HDFC",
                last4 = "4321"
            )
        )
        val payee = db.payeeDao().insert(
            PayeeEntity(
                identifier = "vet@upi",
                displayName = "Vet",
                defaultCategoryId = pets,
                ownAccount = false
            )
        )
        db.payeeDao().setDefaultTags(payee, TagRepository(lazyDb).getOrCreate(listOf("dog")))
        EventRepository(
            lazyDb
        ).save(Event(0, "Goa", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)), zone)
    }

    /** Returns the id of the user's category Pets. */
    private suspend fun addSetup(): Long {
        db.accountDao().insert(
            AccountEntity(name = "HDFC", type = AccountType.BANK, bank = null, last4 = null)
        )
        val pets = db.categoryDao().insert(CategoryEntity(name = "Pets", color = 1, icon = "pets"))
        db.payeeDao().insert(
            PayeeEntity(identifier = "zomato@upi", displayName = "Zomato", defaultCategoryId = null)
        )
        val events = EventRepository(lazyDb)
        events.save(Event(0, "Goa", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)), zone)
        return pets
    }

    private fun setupFile() = SettingsFile(
        categories = listOf(
            CategoryData("food", "Khana", "#FF000001", "food"),
            CategoryData(null, "pets", "#FF000002", "dog", archived = true),
            CategoryData(null, "Gym", "#FF000003", "fitness"),
            CategoryData("travel", null, colorHex(0xFF1565C0.toInt()), "travel")
        ),
        accounts = listOf(
            AccountData("hdfc", "credit_card", "HDFC", "XX1234"),
            AccountData("Cash", "wallet")
        ),
        payees = listOf(
            PayeeData(
                "zomato@upi",
                "Zomato Foods",
                CategoryRef(null, "Gym"),
                listOf("Food", "online"),
                true
            ),
            PayeeData("swiggy@upi", "Swiggy", CategoryRef("food", "Khana"))
        ),
        events = listOf(
            EventData("goa", "2026-10-02", "2026-10-06"),
            EventData("Diwali", "2026-11-08", "2026-11-08")
        )
    )
}
