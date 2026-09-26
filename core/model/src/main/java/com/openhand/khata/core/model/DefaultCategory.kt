package com.openhand.khata.core.model

/**
 * The categories every new database starts with (PRD feature 2). [key] is stored in the database
 * as `seed_key` and must never change; the name is shown in the app's current language unless the
 * user renames the category.
 */
enum class DefaultCategory(
    val key: String,
    /** ARGB colour. */
    val color: Int,
    /** Key of a bundled icon (resolved by the UI). */
    val icon: String
) {
    FOOD("food", 0xFFE65100.toInt(), "food"),
    GROCERIES("groceries", 0xFF2E7D32.toInt(), "groceries"),
    TRAVEL("travel", 0xFF1565C0.toInt(), "travel"),
    RENT("rent", 0xFF5D4037.toInt(), "rent"),
    WORK("work", 0xFF37474F.toInt(), "work"),
    BILLS_UTILITIES("bills_utilities", 0xFFF9A825.toInt(), "bills"),
    SHOPPING("shopping", 0xFFAD1457.toInt(), "shopping"),
    HEALTH("health", 0xFF00838F.toInt(), "health"),
    ENTERTAINMENT("entertainment", 0xFF6A1B9A.toInt(), "entertainment"),

    /** Fallback for anything not yet categorised. Can't be deleted or archived (#20). */
    UNCATEGORIZED("uncategorized", 0xFF757575.toInt(), "uncategorized");

    companion object {
        fun fromKey(key: String?): DefaultCategory? = entries.firstOrNull { it.key == key }
    }
}
