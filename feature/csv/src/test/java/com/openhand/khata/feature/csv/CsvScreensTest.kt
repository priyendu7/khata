package com.openhand.khata.feature.csv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.openhand.khata.core.data.ImportResult
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.TransactionRecord
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CsvScreensTest {
    @get:Rule val compose = createComposeRule()

    private val record =
        TransactionRecord(timestamp = 0, amountPaise = 25_000, direction = Direction.DEBIT)

    private fun showImport(state: ImportState, onImport: () -> Unit = {}) {
        compose.setContent {
            ImportContent(
                state = state,
                onChooseFile = {},
                onMapping = {},
                onConfirmMapping = {},
                onChangeColumns = {},
                onImport = onImport,
                onRestart = {},
                onBack = {}
            )
        }
    }

    @Test
    fun exportWarnsTheFileIsNotEncrypted() {
        var exported = false
        compose.setContent {
            ExportContent(
                range = null,
                status = ExportStatus.Done(3),
                lastExport = null,
                onRange = {},
                onExport = { exported = true },
                onBack = {}
            )
        }
        compose.onNodeWithText("not encrypted", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Not exported yet").assertIsDisplayed()
        compose.onNodeWithText("Saved 3 transactions.").assertIsDisplayed()
        compose.onNodeWithText("Export").performClick()
        assertTrue(exported)
    }

    @Test
    fun previewCountsNewDuplicateAndBadRowsBeforeImporting() {
        var imported = false
        val preview = ImportPreview(
            rows = listOf(
                PreviewRow(3, record.copy(note = "Chai"), duplicate = false),
                PreviewRow(4, record, duplicate = true)
            ),
            invalid = listOf(ParsedRow.Invalid(5, RowProblem.DATE))
        )
        showImport(ImportState.Preview(preview, matched = false)) { imported = true }
        compose.onNodeWithText("1 new transaction").assertIsDisplayed()
        compose.onNodeWithText("1 already saved, will be skipped").assertIsDisplayed()
        compose.onNodeWithText("1 row can't be read.").assertIsDisplayed()
        compose.onNodeWithText("Row 5: the date isn't understood").performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Import 1 transaction").performScrollTo().performClick()
        assertTrue(imported)
    }

    @Test
    fun nothingNewMeansNothingToImport() {
        val preview = ImportPreview(listOf(PreviewRow(3, record, duplicate = true)), emptyList())
        showImport(ImportState.Preview(preview, matched = false))
        compose.onNodeWithText("Import 0 transactions").assertIsNotEnabled()
    }

    @Test
    fun matchingSwitchesToSeparateColumnsAndNeedsAnAmount() {
        val header = listOf("Date", "Narration", "Debit", "Credit")
        compose.setContent {
            var state by remember {
                mutableStateOf(
                    ImportState.Matching(
                        header,
                        listOf("27/09/2026", "UPI-SWIGGY", "450.00", ""),
                        ColumnMapping(date = 0, description = 1)
                    )
                )
            }
            ImportContent(
                state = state,
                onChooseFile = {},
                onMapping = { state = state.copy(mapping = it) },
                onConfirmMapping = {},
                onChangeColumns = {},
                onImport = {},
                onRestart = {},
                onBack = {}
            )
        }
        compose.onNodeWithText("Continue").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("One amount column: negative is spending").performScrollTo()
            .performClick()
        compose.onNodeWithText("Separate spending and income columns").performClick()
        compose.onNodeWithText("Spending (debit) column").performScrollTo().performClick()
        compose.onNodeWithText("Debit (e.g. 450.00)").performClick()
        compose.onNodeWithText("Continue").performScrollTo().assertIsEnabled()
    }

    @Test
    fun doneSaysWhatHappened() {
        showImport(ImportState.Done(ImportResult(added = 12, duplicates = 2), invalid = 1))
        compose.onNodeWithText("Added 12 transactions.").assertIsDisplayed()
        compose.onNodeWithText("Skipped 2 that were already saved.").assertIsDisplayed()
        compose.onNodeWithText("1 row can't be read.").assertIsDisplayed()
    }
}
