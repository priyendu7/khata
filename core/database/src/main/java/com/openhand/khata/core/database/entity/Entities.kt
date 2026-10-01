package com.openhand.khata.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.TransactionSource

/** A bank account, card or wallet. Only the last 4 digits of any number are ever stored. */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val bank: String?,
    val last4: String?
)

/**
 * One category per transaction (PRD feature 2). Archived categories stay on old transactions.
 *
 * Default categories have a [seedKey] (see `DefaultCategory`) and a null [name], so the UI shows
 * them in the current language. Renaming sets [name]; [seedKey] never changes, so the app still
 * knows which category is, for example, "Uncategorized".
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["seed_key"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The user's name for this category; null means "use the default name for [seedKey]". */
    val name: String?,
    @ColumnInfo(name = "seed_key") val seedKey: String? = null,
    /** ARGB colour, e.g. 0xFFC62828. */
    val color: Int,
    /** Key of a bundled icon (resolved by the UI). */
    val icon: String,
    val archived: Boolean = false
)

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Unique ignoring case, so `Work` and `work` are the same tag. */
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String
)

/** Payee memory (PRD feature 3): what a UPI ID, merchant or account is called and how it's filed. */
@Entity(
    tableName = "payees",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["default_category_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["identifier"], unique = true), Index("default_category_id")]
)
data class PayeeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** UPI ID, merchant name or account as it appears in SMS or entry, e.g. `paytmqr…@paytm`. */
    val identifier: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "default_category_id") val defaultCategoryId: Long?,
    /** One of the user's own accounts: money to or from it is a transfer, not spending (#56). */
    @ColumnInfo(name = "own_account", defaultValue = "0") val ownAccount: Boolean = false
)

/** A payee's default tags (the "default tags" of PRD feature 3), many-to-many. */
@Entity(
    tableName = "payee_default_tags",
    primaryKeys = ["payee_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = PayeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["payee_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tag_id")]
)
data class PayeeDefaultTagEntity(
    @ColumnInfo(name = "payee_id") val payeeId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        // Accounts and categories in use can't be deleted; the UI moves or archives first (#17, #20).
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = PayeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["payee_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("timestamp"),
        Index("account_id"),
        Index("payee_id"),
        Index("category_id"),
        // Not unique: both sides of a move between the user's own accounts can share one (#56).
        Index("reference_no")
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Always positive, in paise (₹1 = 100). [direction] gives the sign. */
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    val direction: Direction,
    /** When it happened, in milliseconds since the epoch (UTC). */
    val timestamp: Long,
    @ColumnInfo(name = "account_id") val accountId: Long?,
    @ColumnInfo(name = "payee_id") val payeeId: Long?,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    val note: String?,
    /**
     * UPI or bank reference number. SMS and CSV import treat the same reference on the same
     * account as a duplicate; on another account it's the other side of a transfer.
     */
    @ColumnInfo(name = "reference_no") val referenceNo: String?,
    val source: TransactionSource,
    /** Original SMS text, kept only for SMS-sourced transactions. */
    @ColumnInfo(name = "raw_sms") val rawSms: String?,
    @ColumnInfo(name = "needs_review") val needsReview: Boolean = false
)

@Entity(
    tableName = "transaction_tags",
    primaryKeys = ["transaction_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tag_id")]
)
data class TransactionTagEntity(
    @ColumnInfo(name = "transaction_id") val transactionId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long
)
