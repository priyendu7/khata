package com.openhand.khata.core.data

import androidx.room.withTransaction
import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.database.entity.AccountEntity
import com.openhand.khata.core.database.entity.PayeeEntity
import com.openhand.khata.core.database.entity.TransactionEntity
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.DefaultCategory
import com.openhand.khata.core.model.SmsTransaction
import com.openhand.khata.core.model.TransactionSource
import dagger.Lazy
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saves transactions read from bank SMS (PRD feature 7): finds or creates the account, applies
 * payee memory, and skips anything already saved, whether it came from an SMS, by hand or from a
 * CSV. Used by both the new-SMS receiver and the inbox scan, which can see the same SMS.
 *
 * [preview] answers "what would import do?" for Settings > SMS import > Test a message. Both go
 * through [plan], so the answer can't disagree with a real import.
 */
@Singleton
class SmsImporter @Inject constructor(private val db: Lazy<KhataDatabase>) {
    /**
     * One database transaction per SMS, so the receiver and an inbox scan running at the same
     * time can't both save it.
     */
    suspend fun import(sms: SmsTransaction): SmsImportResult {
        require(sms.amountPaise > 0) { "Amount must be more than zero" }
        return db.io { database ->
            database.withTransaction {
                val plan = plan(database, sms)
                plan.duplicate ?: save(database, sms, plan)
            }
        }
    }

    /** What [import] would do with [sms], without saving anything. */
    suspend fun preview(sms: SmsTransaction): SmsImportPreview = db.io { database ->
        val plan = plan(database, sms)
        val memory = plan.memory
        SmsImportPreview(
            duplicate = plan.duplicate,
            account = plan.account?.toModel(),
            payeeName = plan.payee?.displayName,
            category = memory?.categoryId?.let { database.categoryDao().getById(it) }?.toModel(),
            tags = memory?.tagIds.orEmpty().mapNotNull { database.tagDao().getById(it)?.name },
            needsReview = memory == null
        )
    }

    /** Everything [import] decides before it writes: the account, a duplicate, the payee. */
    private suspend fun plan(database: KhataDatabase, sms: SmsTransaction): Plan {
        val account = findAccount(database, sms)
        val payee = payeeIdentifier(sms.payee)?.let { database.payeeDao().findByName(it) }
        return Plan(
            account = account,
            duplicate = duplicateOf(database, sms, account?.id),
            payee = payee,
            memory = payee?.let { memory(database, it) }
        )
    }

    /** [account] and [payee] are null when import would make new ones. */
    private class Plan(
        val account: AccountEntity?,
        val duplicate: SmsImportResult.Duplicate?,
        val payee: PayeeEntity?,
        val memory: PayeeMemory?
    )

    private suspend fun duplicateOf(
        database: KhataDatabase,
        sms: SmsTransaction,
        accountId: Long?
    ): SmsImportResult.Duplicate? {
        val dao = database.smsImportDao()
        val at = sms.timestamp
        return sms.referenceNo?.let { database.transactionDao().getByReferenceNo(it) }
            ?.let { SmsImportResult.Duplicate(it.id, DuplicateMatch.REFERENCE) }
            ?: dao.findSameSms(sms.rawSms, at - SAME_SMS_WINDOW, at + SAME_SMS_WINDOW)
                ?.let { SmsImportResult.Duplicate(it, DuplicateMatch.SAME_SMS) }
            ?: dao.findNear(
                amountPaise = sms.amountPaise,
                direction = sms.direction,
                accountId = accountId,
                referenceNo = sms.referenceNo,
                at = at,
                from = at - NEAR_WINDOW,
                until = at + NEAR_WINDOW
            )?.let { SmsImportResult.Duplicate(it, DuplicateMatch.AMOUNT_AND_TIME) }
    }

    /**
     * The account with the SMS's last 4 digits whose bank is the SMS's bank, or contains it (a
     * user may have typed "Kotak Mahindra Bank"), or, failing that, the only such account with no
     * bank set. Without last 4 digits, only an account with none and the same bank.
     */
    private suspend fun findAccount(database: KhataDatabase, sms: SmsTransaction): AccountEntity? {
        val candidates = database.accountDao().getByLast4(sms.accountLast4)
        fun AccountEntity.bankIs(exact: Boolean): Boolean {
            val bank = bank ?: return false
            return bank.equals(sms.bank, ignoreCase = true) || !exact && sameBank(bank, sms.bank)
        }
        return candidates.firstOrNull { it.bankIs(exact = true) }
            ?: candidates.firstOrNull { it.bankIs(exact = false) }
            ?: candidates.singleOrNull { it.bank == null }?.takeIf { sms.accountLast4 != null }
    }

    private fun sameBank(a: String, b: String) =
        a.contains(b, ignoreCase = true) || b.contains(a, ignoreCase = true)

    private suspend fun save(
        database: KhataDatabase,
        sms: SmsTransaction,
        plan: Plan
    ): SmsImportResult.Saved {
        val accountId = plan.account?.id ?: database.accountDao().insert(
            AccountEntity(
                name = listOfNotNull(sms.bank, sms.accountLast4).joinToString(" "),
                type = sms.accountType,
                bank = sms.bank,
                last4 = sms.accountLast4
            )
        )
        val payee = plan.payee ?: payeeIdentifier(sms.payee)?.let { newPayee(database, it) }
        val memory = plan.memory
        val uncategorizedId =
            database.categoryDao().getBySeedKey(DefaultCategory.UNCATEGORIZED.key)!!.id
        val entity = TransactionEntity(
            amountPaise = sms.amountPaise,
            direction = sms.direction,
            timestamp = sms.timestamp,
            accountId = accountId,
            payeeId = payee?.id,
            categoryId = memory?.categoryId ?: uncategorizedId,
            note = null,
            referenceNo = sms.referenceNo,
            source = TransactionSource.SMS,
            rawSms = sms.rawSms,
            needsReview = memory == null
        )
        val id = database.transactionDao().saveWithTags(entity, memory?.tagIds.orEmpty())
        return SmsImportResult.Saved(id, needsReview = entity.needsReview)
    }

    private fun payeeIdentifier(text: String?): String? =
        text?.trim()?.replace(WHITESPACE, " ")?.ifEmpty { null }

    private suspend fun newPayee(database: KhataDatabase, identifier: String): PayeeEntity =
        PayeeEntity(
            identifier = identifier,
            // No name yet: the review inbox asks for one.
            displayName = identifier,
            defaultCategoryId = null
        ).let { it.copy(id = database.payeeDao().insert(it)) }

    /**
     * What payee memory says for [payee] (PRD feature 3), or null if the user hasn't told us
     * about this payee yet: no name of its own, no default category (or an archived one) and no
     * default tags. Then the transaction waits in the review inbox.
     */
    private suspend fun memory(database: KhataDatabase, payee: PayeeEntity): PayeeMemory? {
        val category = payee.defaultCategoryId
            ?.let { database.categoryDao().getById(it) }
            ?.takeUnless { it.archived }
        val tagIds = database.payeeDao().defaultTagIds(payee.id)
        val named = payee.displayName != payee.identifier
        return if (category == null && tagIds.isEmpty() && !named) {
            null
        } else {
            PayeeMemory(category?.id, tagIds)
        }
    }

    private class PayeeMemory(val categoryId: Long?, val tagIds: List<Long>)

    private companion object {
        /** Receiver and inbox scan can time the same SMS a little differently. */
        val SAME_SMS_WINDOW = TimeUnit.DAYS.toMillis(1)

        /** PRD: without a reference, same amount and account this close together is one payment. */
        val NEAR_WINDOW = TimeUnit.MINUTES.toMillis(2)
        val WHITESPACE = Regex("""\s+""")
    }
}

/** What [SmsImporter.import] would do with one SMS. */
data class SmsImportPreview(
    /** Already saved: import would skip it. */
    val duplicate: SmsImportResult.Duplicate?,
    /** The account it would go to; null when import would make a new one. */
    val account: Account?,
    /** The saved payee's name; null when the SMS names a payee import would add, or none. */
    val payeeName: String?,
    /** From payee memory. Null leaves it Uncategorized. */
    val category: Category?,
    val tags: List<String>,
    /** No payee memory yet, so it would wait in To review. */
    val needsReview: Boolean
)

sealed interface SmsImportResult {
    data class Saved(val transactionId: Long, val needsReview: Boolean) : SmsImportResult

    /** Already saved as [existingId], from an SMS, by hand or from a CSV. */
    data class Duplicate(val existingId: Long, val match: DuplicateMatch) : SmsImportResult
}

enum class DuplicateMatch {
    /** Same UPI or bank reference number. */
    REFERENCE,

    /** The very same SMS text, seen by both the receiver and the inbox scan. */
    SAME_SMS,

    /** Same amount, direction and account within 2 minutes, with no conflicting reference. */
    AMOUNT_AND_TIME
}
