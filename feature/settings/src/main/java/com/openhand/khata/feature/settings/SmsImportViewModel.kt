package com.openhand.khata.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.sms.ingest.ScanProgress
import com.openhand.khata.sms.ingest.ScanSummary
import com.openhand.khata.sms.ingest.SmsImportSettings
import com.openhand.khata.sms.ingest.SmsScan
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SmsImportViewModel @Inject constructor(
    private val settings: SmsImportSettings,
    private val scan: SmsScan
) : ViewModel() {
    val enabled: StateFlow<Boolean> = settings.enabled
    val lastScan: StateFlow<ScanSummary?> = settings.lastScan
    val progress: StateFlow<ScanProgress?> =
        scan.progress.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), null)

    fun setEnabled(enabled: Boolean) = settings.setEnabled(enabled)

    /** Imports bank SMS from the start of [from], in the phone's time zone. */
    fun startImport(from: LocalDate) =
        scan.start(from.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())

    fun cancelImport() = scan.cancel()

    private companion object {
        const val STOP_AFTER_MS = 5_000L
    }
}
