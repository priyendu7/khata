package com.openhand.khata.core.data

import com.openhand.khata.core.database.entity.AccountEntity
import com.openhand.khata.core.database.entity.CategoryEntity
import com.openhand.khata.core.database.entity.PayeeEntity
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Event
import com.openhand.khata.core.model.IgnoreKind
import com.openhand.khata.core.model.TransactionRecord
import com.openhand.khata.sms.parser.BuiltInRules
import com.openhand.khata.sms.parser.RuleCode
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Restoring a phone (#125): export both files, Clear data, import settings, import transactions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRestoreTest : RepositoryTest() {
    private val settings by lazy { SettingsBackupRepository(lazyDb) }
    private val transactions by lazy { BackupRepository(lazyDb) }
    private val zone = ZoneId.of("Asia/Kolkata")
    private val crypto = SettingsCrypto(iterations = 1_000)
    private val password = "a long password".toCharArray()

    /** How the CSV names categories; the UI would use the current language. */
    private val categoryName: (Category) -> String = { it.name ?: it.seedKey!! }

    private fun millis(day: LocalDate) =
        day.atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()

    @Test
    fun setupAndRulesComeBackAfterClearData() = runTest {
        setUpPhone()
        val setupBefore = settings.gather()
        val settingsFile = crypto.encrypt(SettingsFormat.encode(setupBefore), password)
        val csv = transactions.export(zone = zone, categoryName = categoryName)

        // Clear data: a fresh, freshly seeded database.
        db.close()
        openDatabase()
        val opened = SettingsFormat.decode(crypto.decrypt(settingsFile, password))
        settings.apply(opened, RuleChoices(), zone, now = 2_000_000_000_000)
        transactions.import(csv, zone)

        assertEquals(setupBefore, settings.gather())
        assertEquals(csv, transactions.export(zone = zone, categoryName = categoryName))
    }

    private suspend fun setUpPhone() {
        val rule = BuiltInRules.all().first()
        CustomParserRepository(
            lazyDb
        ).save("my-rule", "My Bank", RuleCode.encode(rule.copy(id = "my-rule")), now = 1)
        BuiltInRuleOverrideRepository(lazyDb).setEnabled(rule.id, false)
        IgnoreRuleRepository(lazyDb).add(IgnoreKind.SENDER, "SPAMMY", null, "Win a prize", now = 1)
        val food = db.categoryDao().getBySeedKey("food")!!
        db.categoryDao().update(food.copy(name = "Khana", color = 0xFF112233.toInt()))
        val pets = db.categoryDao().insert(
            CategoryEntity(name = "Pets", color = 0xFF445566.toInt(), icon = "pets")
        )
        db.accountDao().insert(
            AccountEntity(
                name = "HDFC card",
                type = AccountType.CREDIT_CARD,
                bank = "HDFC",
                last4 = "4321"
            )
        )
        val vet = db.payeeDao().insert(
            PayeeEntity(
                identifier = "vet@upi",
                displayName = "Vet",
                defaultCategoryId = pets,
                ownAccount = false
            )
        )
        db.payeeDao().setDefaultTags(vet, TagRepository(lazyDb).getOrCreate(listOf("dog")))
        val goa = LocalDate.of(2026, 10, 2)
        transactions.import(
            listOf(
                TransactionRecord(
                    timestamp = millis(goa),
                    amountPaise = 50_000,
                    direction = Direction.DEBIT,
                    account = "HDFC card",
                    payee = "vet@upi",
                    payeeName = "Vet",
                    category = "Pets",
                    tags = listOf("dog")
                ),
                TransactionRecord(
                    timestamp = millis(goa.plusDays(10)),
                    amountPaise = 1_000,
                    direction = Direction.DEBIT,
                    account = "HDFC card",
                    category = "Khana",
                    note = "Tea"
                )
            ),
            zone
        )
        EventRepository(lazyDb).save(Event(0, "Goa", goa.minusDays(1), goa.plusDays(3)), zone)
    }
}
