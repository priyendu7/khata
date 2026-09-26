package com.openhand.khata.core.database.entity

import androidx.room.TypeConverter

/** Which way money moved. Amounts are always positive; the direction gives the sign. */
enum class Direction(val stored: String) {
    /** Money out: an expense. */
    DEBIT("debit"),

    /** Money in: income. */
    CREDIT("credit"),

    /** Money back for an earlier expense; reduces spending in its category. */
    REFUND("refund"),

    /** Between the user's own accounts (e.g. paying a card bill); never counts as spending or income. */
    TRANSFER("transfer")
}

/** Where a transaction came from. */
enum class TransactionSource(val stored: String) {
    SMS("sms"),
    MANUAL("manual"),
    CSV("csv")
}

enum class AccountType(val stored: String) {
    BANK("bank"),
    CREDIT_CARD("credit_card"),
    DEBIT_CARD("debit_card"),
    WALLET("wallet")
}

/**
 * Stores enums as fixed lowercase text, not Kotlin names, so renaming a constant can't break
 * existing databases. Changing a `stored` value needs a migration.
 */
class EnumConverters {
    @TypeConverter
    fun fromDirection(value: Direction): String = value.stored

    @TypeConverter
    fun toDirection(value: String): Direction = Direction.entries.single { it.stored == value }

    @TypeConverter
    fun fromSource(value: TransactionSource): String = value.stored

    @TypeConverter
    fun toSource(value: String): TransactionSource = TransactionSource.entries.single {
        it.stored ==
            value
    }

    @TypeConverter
    fun fromAccountType(value: AccountType): String = value.stored

    @TypeConverter
    fun toAccountType(value: String): AccountType =
        AccountType.entries.single { it.stored == value }
}
