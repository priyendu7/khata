package com.openhand.khata.feature.csv

import android.database.SQLException
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.data.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** First and last day to export, both included. */
data class DateRange(val first: LocalDate, val last: LocalDate)

sealed interface ExportStatus {
    data object Idle : ExportStatus

    data object Working : ExportStatus

    data class Done(val count: Int) : ExportStatus

    data object Failed : ExportStatus
}

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val backup: BackupRepository,
    private val files: CsvFiles
) : ViewModel() {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Null means all data. */
    private val _range = MutableStateFlow<DateRange?>(null)
    val range: StateFlow<DateRange?> = _range.asStateFlow()

    private val _status = MutableStateFlow<ExportStatus>(ExportStatus.Idle)
    val status: StateFlow<ExportStatus> = _status.asStateFlow()

    val lastExport: StateFlow<Long?> = backup.observeLastExport()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun setRange(range: DateRange?) {
        _range.value = range
        _status.value = ExportStatus.Idle
    }

    /** What the file picker suggests, e.g. `khata-2026-09-27.csv`. */
    fun fileName(today: LocalDate = LocalDate.now(zone)): String =
        _range.value?.let { "khata-${it.first}_${it.last}.csv" } ?: "khata-$today.csv"

    /** Writes the chosen range to [uri], a file the user just created in the file picker. */
    fun export(uri: Uri, names: CategoryNames) {
        if (_status.value == ExportStatus.Working) return
        _status.value = ExportStatus.Working
        viewModelScope.launch {
            val range = _range.value
            _status.value = try {
                val records = backup.export(
                    from = range?.first?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
                    until = range?.last?.plusDays(1)?.atStartOfDay(zone)?.toInstant()
                        ?.toEpochMilli(),
                    categoryName = names::nameOf
                )
                val text = withContext(Dispatchers.Default) { KhataCsvFormat.write(records, zone) }
                files.write(uri, text)
                backup.markExported(System.currentTimeMillis())
                ExportStatus.Done(records.size)
            } catch (_: IOException) {
                ExportStatus.Failed
            } catch (_: SecurityException) {
                ExportStatus.Failed
            } catch (_: SQLException) {
                ExportStatus.Failed
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
