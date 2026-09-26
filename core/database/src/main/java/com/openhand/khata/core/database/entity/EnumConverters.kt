package com.openhand.khata.core.database.entity

import androidx.room.TypeConverter
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.TransactionSource

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
}
