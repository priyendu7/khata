package com.openhand.khata.core.model

/**
 * An SMS sender as Indian operators deliver it: `AX-KOTAKB-S` is operator/region prefix `AX`
 * (it varies), header `KOTAKB` and TRAI [category] `S`. Some phones show only the header, so
 * [category] is null then.
 */
data class SenderId(val header: String, val category: SenderCategory?) {
    companion object {
        private const val PREFIX_LENGTH = 2
        private val HEADER = Regex("[A-Z0-9]{3,9}")

        /** Null for anything that isn't a header, such as a phone number. */
        fun parse(sender: String): SenderId? {
            val parts = sender.trim().uppercase().split('-').toMutableList()
            val category = if (parts.size > 1) SenderCategory.of(parts.last()) else null
            if (category != null) parts.removeAt(parts.lastIndex)
            if (parts.size > 1 && parts.first().length == PREFIX_LENGTH) parts.removeAt(0)
            // Phone numbers (+919876543210, 9876543210) are people, not businesses.
            val header = parts.singleOrNull()?.takeIf {
                HEADER.matches(it) && !it.all(Char::isDigit)
            }
            return header?.let { SenderId(it, category) }
        }
    }
}

/** The TRAI category suffix of a sender ID. */
enum class SenderCategory(val suffix: String) {
    SERVICE("S"),
    TRANSACTIONAL("T"),
    PROMOTIONAL("P"),
    GOVERNMENT("G");

    companion object {
        fun of(suffix: String): SenderCategory? = entries.firstOrNull { it.suffix == suffix }
    }
}
