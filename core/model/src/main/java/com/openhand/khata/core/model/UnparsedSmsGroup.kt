package com.openhand.khata.core.model

/**
 * The SMS in To review from one sender header (`HDFCBK` for both `AX-HDFCBK-S` and
 * `VM-HDFCBK-S`), newest first, so one parser or one "ignore" can clear them all.
 */
data class UnparsedSmsGroup(val header: String, val messages: List<UnparsedSms>) {
    init {
        require(messages.isNotEmpty()) { "A group has at least one SMS" }
    }

    val count: Int get() = messages.size

    val newest: UnparsedSms get() = messages.first()
}
