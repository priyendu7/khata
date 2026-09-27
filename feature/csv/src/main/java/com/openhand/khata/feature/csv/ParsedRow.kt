package com.openhand.khata.feature.csv

import com.openhand.khata.core.model.TransactionRecord

/** Why a row can't be imported; each is shown to the user with the row's line number. */
enum class RowProblem { COLUMNS, DATE, TIME, AMOUNT, DIRECTION }

/**
 * One data row of an imported file. [line] is its row number in the file, counting from 1 at the
 * top, header included, as a spreadsheet numbers it.
 */
sealed interface ParsedRow {
    val line: Int

    data class Valid(override val line: Int, val record: TransactionRecord) : ParsedRow

    data class Invalid(override val line: Int, val problem: RowProblem) : ParsedRow
}

/** Stops reading a row at its first problem; see [parsedRow]. */
internal class RowError(val problem: RowProblem) : Exception(problem.name)

internal fun <T : Any> T?.orFail(problem: RowProblem): T = this ?: throw RowError(problem)

/** Runs [read] on the row at [line], turning its first [RowError] into [ParsedRow.Invalid]. */
internal inline fun parsedRow(line: Int, read: () -> TransactionRecord): ParsedRow = try {
    ParsedRow.Valid(line, read())
} catch (error: RowError) {
    ParsedRow.Invalid(line, error.problem)
}
