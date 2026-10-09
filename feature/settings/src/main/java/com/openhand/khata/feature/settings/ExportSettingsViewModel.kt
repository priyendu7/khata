package com.openhand.khata.feature.settings

import android.database.SQLException
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.data.SettingsBackupRepository
import com.openhand.khata.core.data.SettingsCrypto
import com.openhand.khata.core.data.SettingsFile
import com.openhand.khata.core.data.SettingsFormat
import com.openhand.khata.core.model.AppInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What a settings file holds, shown once it's saved. */
data class SettingsCounts(
    val customParsers: Int = 0,
    val builtInEdits: Int = 0,
    val ignoreRules: Int = 0,
    val categories: Int = 0,
    val accounts: Int = 0,
    val payees: Int = 0,
    val events: Int = 0
) {
    companion object {
        fun of(file: SettingsFile) = SettingsCounts(
            customParsers = file.customParsers.size,
            builtInEdits = file.builtInOverrides.size,
            ignoreRules = file.ignoreRules.size,
            categories = file.categories.size,
            accounts = file.accounts.size,
            payees = file.payees.size,
            events = file.events.size
        )
    }
}

sealed interface SettingsExportStatus {
    data object Idle : SettingsExportStatus

    data object Working : SettingsExportStatus

    data class Done(val counts: SettingsCounts) : SettingsExportStatus

    data object Failed : SettingsExportStatus
}

/** Settings > Export settings (#125): everything but transactions, locked with a password. */
@HiltViewModel
class ExportSettingsViewModel @Inject constructor(
    private val backup: SettingsBackupRepository,
    private val preferences: AppPreferences,
    private val files: SettingsFiles,
    private val appInfo: AppInfo
) : ViewModel() {
    private val _status = MutableStateFlow<SettingsExportStatus>(SettingsExportStatus.Idle)
    val status: StateFlow<SettingsExportStatus> = _status.asStateFlow()

    /** What the file picker suggests, e.g. `khata-settings-2026-10-09.khata`. */
    fun fileName(today: LocalDate = LocalDate.now()): String = "khata-settings-$today.khata"

    /** Writes the settings file to [uri], a file the user just created in the file picker. */
    fun export(uri: Uri, password: String) {
        if (_status.value == SettingsExportStatus.Working) return
        _status.value = SettingsExportStatus.Working
        viewModelScope.launch {
            _status.value = try {
                val file = preferences.current().writeTo(backup.gather()).copy(
                    exportedAt = Instant.now().toString(),
                    appVersion = appInfo.versionName
                )
                val bytes = withContext(Dispatchers.Default) {
                    SettingsCrypto().encrypt(SettingsFormat.encode(file), password.toCharArray())
                }
                files.write(uri, bytes)
                SettingsExportStatus.Done(SettingsCounts.of(file))
            } catch (_: IOException) {
                SettingsExportStatus.Failed
            } catch (_: SecurityException) {
                SettingsExportStatus.Failed
            } catch (_: SQLException) {
                SettingsExportStatus.Failed
            }
        }
    }
}
