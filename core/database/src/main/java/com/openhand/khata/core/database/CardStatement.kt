package com.openhand.khata.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.openhand.khata.core.database.entity.AccountEntity
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.TransactionSource
import kotlinx.coroutines.flow.Flow

/**
 * A credit card statement read from SMS (#154): the total and minimum due on card [accountId].
 * Dates are local epoch days. Payments and cycles aren't stored; they're worked out from the
 * card's transfers and the statements' dates.
 */
@Entity(
    tableName = "card_statements",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["account_id", "statement_date"])]
)
data class CardStatementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "statement_date") val statementDate: Long,
    @ColumnInfo(name = "due_date") val dueDate: Long,
    @ColumnInfo(name = "total_paise") val totalPaise: Long,
    @ColumnInfo(name = "min_due_paise") val minDuePaise: Long?,
    /** The SMS with its links taken out. */
    @ColumnInfo(name = "raw_sms") val rawSms: String,
    /** Whether the SMS gave [statementDate]; otherwise it's the day the SMS arrived (#166). */
    @ColumnInfo(name = "date_from_sms", defaultValue = "0") val dateFromSms: Boolean = false
)

/**
 * A transfer that may pay a credit card: into a card, or a card bill paid from another account
 * whose card side isn't saved ([accountType] isn't a card then).
 */
data class CardPaymentRow(
    @ColumnInfo(name = "transaction_id") val transactionId: Long,
    @ColumnInfo(name = "account_id") val accountId: Long?,
    @ColumnInfo(name = "account_type") val accountType: AccountType?,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    val timestamp: Long,
    val source: TransactionSource,
    @ColumnInfo(name = "pair_id") val pairId: Long?
)

/** A card bill payment from another account that isn't linked to any card (#166). */
data class UnlinkedPaymentRow(
    @ColumnInfo(name = "transaction_id") val transactionId: Long,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    val timestamp: Long,
    @ColumnInfo(name = "payee_name") val payeeName: String?,
    @ColumnInfo(name = "account_name") val accountName: String?
)

/** An expense or refund on a credit card, for its cycles' spending. */
data class CardSpendRow(
    @ColumnInfo(name = "account_id") val accountId: Long,
    val timestamp: Long,
    val direction: Direction,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long
)

@Dao
interface CardStatementDao {
    @Insert suspend fun insert(statement: CardStatementEntity): Long

    @Query("SELECT * FROM card_statements WHERE id = :id")
    suspend fun getById(id: Long): CardStatementEntity?

    /** Every statement, each card's newest first. */
    @Query("SELECT * FROM card_statements ORDER BY account_id, statement_date DESC, id DESC")
    fun observeAll(): Flow<List<CardStatementEntity>>

    /**
     * The same statement saved before: same due date and total, on [accountId] or from the very
     * same SMS (it may have been moved to another card since).
     */
    @Query(
        "SELECT id FROM card_statements WHERE due_date = :dueDate AND total_paise = :totalPaise " +
            "AND (account_id = :accountId OR raw_sms = :rawSms) LIMIT 1"
    )
    suspend fun findSame(accountId: Long?, dueDate: Long, totalPaise: Long, rawSms: String): Long?

    @Query("UPDATE card_statements SET account_id = :accountId WHERE id = :id")
    suspend fun move(id: Long, accountId: Long)

    /**
     * Transfers into credit cards (one with no side counts, as a card's transfer is almost
     * always a payment), and card bills paid from another account that aren't linked to a card
     * side (#113), so one payment is never counted twice. Oldest first.
     */
    @Query(
        "SELECT t.id AS transaction_id, t.account_id, a.type AS account_type, t.amount_paise, " +
            "t.timestamp, t.source, t.transfer_pair_id AS pair_id " +
            "FROM transactions t LEFT JOIN accounts a ON a.id = t.account_id " +
            "WHERE t.direction = 'transfer' AND ((a.type = 'credit_card' " +
            "AND COALESCE(t.transfer_side, 'in') = 'in') " +
            "OR (t.transfer_kind = 'card_payment' AND t.transfer_side = 'out' " +
            "AND t.transfer_pair_id IS NULL AND (a.type IS NULL OR a.type != 'credit_card'))) " +
            "ORDER BY t.timestamp, t.id"
    )
    fun observePayments(): Flow<List<CardPaymentRow>>

    /**
     * Card bills paid from another account whose card isn't known ("card not known" on Insights),
     * newest first: the payments "Add a payment" offers to link to a card (#166).
     */
    @Query(
        "SELECT t.id AS transaction_id, t.amount_paise, t.timestamp, " +
            "p.display_name AS payee_name, a.name AS account_name FROM transactions t " +
            "LEFT JOIN accounts a ON a.id = t.account_id " +
            "LEFT JOIN payees p ON p.id = t.payee_id " +
            "WHERE t.direction = 'transfer' AND t.transfer_kind = 'card_payment' " +
            "AND t.transfer_side = 'out' AND t.transfer_pair_id IS NULL " +
            "AND (a.type IS NULL OR a.type != 'credit_card') " +
            "ORDER BY t.timestamp DESC, t.id DESC"
    )
    fun observeUnlinkedPayments(): Flow<List<UnlinkedPaymentRow>>

    /**
     * Expenses and refunds on credit cards from [from] on, by the time they count at, with the
     * same rules as the spending queries (#108).
     */
    @Query(
        "SELECT t.account_id, COALESCE(t.counts_at, t.timestamp) AS timestamp, t.direction, " +
            "t.amount_paise FROM transactions t JOIN accounts a ON a.id = t.account_id " +
            "WHERE a.type = 'credit_card' AND t.direction IN ('debit', 'refund') " +
            "AND COALESCE(t.counts_at, t.timestamp) >= :from"
    )
    fun observeSpends(from: Long): Flow<List<CardSpendRow>>
}
