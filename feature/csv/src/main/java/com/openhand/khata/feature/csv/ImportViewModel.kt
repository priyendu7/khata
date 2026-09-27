package com.openhand.khata.feature.csv

import android.database.SQLException
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.data.BackupRepository
import com.openhand.khata.core.data.ImportResult
import com.openhand.khata.core.model.TransactionRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A row that can be imported, and whether it's already saved. */
data class PreviewRow(val line: Int, val record: TransactionRecord, val duplicate: Boolean)

/** What an import would do, shown before anything is saved. */
data class ImportPreview(val rows: List<PreviewRow>, val invalid: List<ParsedRow.Invalid>) {
    val newCount: Int get() = rows.count { !it.duplicate }
    val duplicateCount: Int get() = rows.size - newCount
}

/** The import flow: pick a file, match its columns if it isn't Khata's, preview, import. */
sealed interface ImportState {
    data object Start : ImportState

    data object Working : ImportState

    /** A CSV from another app: which column is which. [sample] is its first data row. */
    data class Matching(
        val header: List<String>,
        val sample: List<String>,
        val mapping: ColumnMapping
    ) : ImportState

    data class Preview(val preview: ImportPreview, val matched: Boolean) : ImportState

    data class Done(val result: ImportResult, val invalid: Int) : ImportState

    data class Failed(val reason: Reason) : ImportState {
        enum class Reason { UNREADABLE, EMPTY, SAVE }
    }
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val backup: BackupRepository,
    private val files: CsvFiles
) : ViewModel() {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private var rows: List<List<String>> = emptyList()
    private var mapping: ColumnMapping? = null

    private val _state = MutableStateFlow<ImportState>(ImportState.Start)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    /** Reads the file the user picked; Khata's own format goes straight to the preview. */
    fun open(uri: Uri) {
        _state.value = ImportState.Working
        viewModelScope.launch {
            _state.value = try {
                val text = files.read(uri)
                rows = withContext(Dispatchers.Default) { Csv.parse(text) }
                when {
                    rows.size < 2 -> ImportState.Failed(ImportState.Failed.Reason.EMPTY)
                    KhataCsvFormat.headerRows(rows) != null -> preview(matched = false) {
                        KhataCsvFormat.parse(rows, zone)
                    }
                    else -> ImportState.Matching(rows[0], rows[1], ColumnMapping.guess(rows))
                }
            } catch (_: IOException) {
                ImportState.Failed(ImportState.Failed.Reason.UNREADABLE)
            } catch (_: SecurityException) {
                ImportState.Failed(ImportState.Failed.Reason.UNREADABLE)
            } catch (_: SQLException) {
                ImportState.Failed(ImportState.Failed.Reason.SAVE)
            }
        }
    }

    fun changeMapping(mapping: ColumnMapping) {
        val matching = _state.value as? ImportState.Matching ?: return
        _state.value = matching.copy(mapping = mapping)
    }

    /** From column matching to the preview. */
    fun confirmMapping() {
        val matching = _state.value as? ImportState.Matching ?: return
        if (!matching.mapping.isComplete) return
        mapping = matching.mapping
        _state.value = ImportState.Working
        viewModelScope.launch {
            _state.value = try {
                preview(matched = true) { matching.mapping.parse(rows, zone) }
            } catch (_: SQLException) {
                ImportState.Failed(ImportState.Failed.Reason.SAVE)
            }
        }
    }

    /** Back from the preview to column matching, keeping the choices made. */
    fun backToMatching() {
        val chosen = mapping ?: return
        if (_state.value !is ImportState.Preview) return
        _state.value = ImportState.Matching(rows[0], rows[1], chosen)
    }

    /** Saves the new rows of the preview, all in one database transaction. */
    fun import(names: CategoryNames, newCategoryColors: List<Int>) {
        val preview = (_state.value as? ImportState.Preview)?.preview ?: return
        _state.value = ImportState.Working
        viewModelScope.launch {
            _state.value = try {
                val result = backup.import(
                    records = preview.rows.map { it.record },
                    zone = zone,
                    categoryAliases = names.aliases,
                    newCategoryColors = newCategoryColors
                )
                ImportState.Done(result, preview.invalid.size)
            } catch (_: SQLException) {
                ImportState.Failed(ImportState.Failed.Reason.SAVE)
            }
        }
    }

    fun restart() {
        rows = emptyList()
        mapping = null
        _state.value = ImportState.Start
    }

    private suspend fun preview(matched: Boolean, parse: () -> List<ParsedRow>): ImportState {
        val parsed = withContext(Dispatchers.Default) { parse() }
        val valid = parsed.filterIsInstance<ParsedRow.Valid>()
        val duplicate = backup.findDuplicates(valid.map { it.record }, zone)
        val previewRows = valid.mapIndexed { index, row ->
            PreviewRow(row.line, row.record, duplicate[index])
        }
        return ImportState.Preview(
            ImportPreview(previewRows, parsed.filterIsInstance<ParsedRow.Invalid>()),
            matched
        )
    }
}
