package com.openhand.khata.core.database.entity

import androidx.room.TypeConverter
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.IgnoreKind
import com.openhand.khata.core.model.TransactionSource
import com.openhand.khata.core.model.TransferKind
import com.openhand.khata.core.model.TransferSide

/**
 * Stores enums as fixed lowercase text, not Kotlin names, so renaming a constant can't break
 * existing databases. Changing a `stored` value needs a migration.
 */
class EnumConverters {
    @TypeConverter
    fun fromDirection(value: Direction): String = when (value) {
        Direction.DEBIT -> "debit"
        Direction.CREDIT -> "credit"
        Direction.REFUND -> "refund"
        Direction.TRANSFER -> "transfer"
    }

    @TypeConverter
    fun toDirection(value: String): Direction = Direction.entries.single {
        fromDirection(it) ==
            value
    }

    @TypeConverter
    fun fromSource(value: TransactionSource): String = when (value) {
        TransactionSource.SMS -> "sms"
        TransactionSource.MANUAL -> "manual"
        TransactionSource.CSV -> "csv"
    }

    @TypeConverter
    fun toSource(value: String): TransactionSource = TransactionSource.entries.single {
        fromSource(it) ==
            value
    }

    @TypeConverter
    fun fromAccountType(value: AccountType): String = when (value) {
        AccountType.BANK -> "bank"
        AccountType.CREDIT_CARD -> "credit_card"
        AccountType.DEBIT_CARD -> "debit_card"
        AccountType.WALLET -> "wallet"
    }

    @TypeConverter
    fun toAccountType(value: String): AccountType = AccountType.entries.single {
        fromAccountType(it) ==
            value
    }

    @TypeConverter
    fun fromIgnoreKind(value: IgnoreKind): String = when (value) {
        IgnoreKind.SENDER -> "sender"
        IgnoreKind.TEMPLATE -> "template"
    }

    @TypeConverter
    fun toIgnoreKind(value: String): IgnoreKind =
        IgnoreKind.entries.single { fromIgnoreKind(it) == value }

    @TypeConverter
    fun fromTransferSide(value: TransferSide?): String? = when (value) {
        TransferSide.OUT -> "out"
        TransferSide.IN -> "in"
        null -> null
    }

    @TypeConverter
    fun toTransferSide(value: String?): TransferSide? =
        TransferSide.entries.singleOrNull { fromTransferSide(it) == value }

    @TypeConverter
    fun fromTransferKind(value: TransferKind?): String? = when (value) {
        TransferKind.CARD_PAYMENT -> "card_payment"
        TransferKind.OWN_ACCOUNT -> "own_account"
        TransferKind.OTHER_SIDE -> "other_side"
        TransferKind.MANUAL -> "manual"
        null -> null
    }

    @TypeConverter
    fun toTransferKind(value: String?): TransferKind? =
        TransferKind.entries.singleOrNull { fromTransferKind(it) == value }
}
