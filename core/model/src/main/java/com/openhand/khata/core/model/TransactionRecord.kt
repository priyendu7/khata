package com.openhand.khata.core.model

import java.time.YearMonth

/**
 * A transaction with names in place of ids: what CSV export writes and import reads
 * (`docs/csv-format.md`). Blank or null fields mean "none", and no category means Uncategorized.
 */
data class TransactionRecord(
    /** Milliseconds since the epoch. */
    val timestamp: Long,
    /** Always positive; [direction] gives the sign. */
    val amountPaise: Long,
    val direction: Direction,
    val account: String? = null,
    /** The payee's identifier: UPI ID, merchant or typed name. */
    val payee: String? = null,
    /** What the payee is called in the app. */
    val payeeName: String? = null,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val note: String? = null,
    /** UPI or bank reference number; unique when present. */
    val referenceNo: String? = null,
    /** The month it counts in, or null for its date's month; see [CountsIn]. */
    val countsIn: YearMonth? = null
)
