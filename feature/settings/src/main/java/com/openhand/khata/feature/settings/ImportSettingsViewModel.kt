package com.openhand.khata.feature.settings

import android.database.SQLException
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.data.RuleChoices
import com.openhand.khata.core.data.RuleComparison
import com.openhand.khata.core.data.RuleStatus
import com.openhand.khata.core.data.SettingsBackupRepository
import com.openhand.khata.core.data.SettingsCrypto
import com.openhand.khata.core.data.SettingsFile
import com.openhand.khata.core.data.SettingsFileError
import com.openhand.khata.core.data.SettingsFileException
import com.openhand.khata.core.data.SettingsFormat
import com.openhand.khata.core.data.SettingsPreview
import com.openhand.khata.sms.ingest.SmsIngestor
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SettingsImportState {
    data object Start : SettingsImportState

    /** A file is chosen and needs its password. [wrongPassword]: the last one didn't open it. */
    data class Password(val wrongPassword: Boolean = false) : SettingsImportState

    data object Working : SettingsImportState

    /** What importing will do. Changed rules are replaced unless their id is in [choices]. */
    data class Preview(
        val preview: SettingsPreview,
        val current: AppPrefs,
        val file: AppPrefs,
        val choices: RuleChoices = RuleChoices()
    ) : SettingsImportState

    data class Done(
        val preview: SettingsPreview,
        val outcome: PrefsOutcome,
        /** SMS in To review that the new rules read. */
        val readAgain: Int
    ) : SettingsImportState

    data class Failed(val reason: Reason) : SettingsImportState {
        enum class Reason { UNREADABLE, NOT_SETTINGS_FILE, NEWER_VERSION, DAMAGED, SAVE }
    }
}

/**
 * Settings > Import settings (#125): opens a settings file with its password, shows what will
 * change, and applies it all at once. Nothing is saved before the user taps Import.
 */
@HiltViewModel
class ImportSettingsViewModel @Inject constructor(
    private val backup: SettingsBackupRepository,
    private val preferences: AppPreferences,
    private val files: SettingsFiles,
    private val ingestor: SmsIngestor
) : ViewModel() {
    private val _state = MutableStateFlow<SettingsImportState>(SettingsImportState.Start)
    val state: StateFlow<SettingsImportState> = _state.asStateFlow()

    private var bytes: ByteArray? = null
    private var file: SettingsFile? = null

    fun open(uri: Uri) {
        _state.value = SettingsImportState.Working
        viewModelScope.launch {
            _state.value = try {
                bytes = files.read(uri)
                SettingsImportState.Password()
            } catch (_: IOException) {
                failed(SettingsImportState.Failed.Reason.UNREADABLE)
            } catch (_: SecurityException) {
                failed(SettingsImportState.Failed.Reason.UNREADABLE)
            }
        }
    }

    fun unlock(password: String) {
        val content = bytes ?: return
        _state.value = SettingsImportState.Working
        viewModelScope.launch {
            _state.value = try {
                val opened = withContext(Dispatchers.Default) {
                    SettingsFormat.decode(SettingsCrypto().decrypt(content, password.toCharArray()))
                }
                file = opened
                SettingsImportState.Preview(
                    preview = backup.preview(opened),
                    current = preferences.current(),
                    file = AppPrefs.from(opened)
                )
            } catch (e: SettingsFileException) {
                when (e.error) {
                    SettingsFileError.WRONG_PASSWORD -> SettingsImportState.Password(true)
                    SettingsFileError.NOT_SETTINGS_FILE ->
                        failed(SettingsImportState.Failed.Reason.NOT_SETTINGS_FILE)
                    SettingsFileError.NEWER_VERSION ->
                        failed(SettingsImportState.Failed.Reason.NEWER_VERSION)
                    SettingsFileError.DAMAGED -> failed(SettingsImportState.Failed.Reason.DAMAGED)
                }
            } catch (_: SQLException) {
                failed(SettingsImportState.Failed.Reason.SAVE)
            }
        }
    }

    /** Replace (false) or Keep mine (true) for one changed rule. */
    fun keep(ruleId: String, builtIn: Boolean, keep: Boolean) = updatePreview { state ->
        val choices = state.choices
        state.copy(
            choices = if (builtIn) {
                choices.copy(keepBuiltIn = choices.keepBuiltIn.toggled(ruleId, keep))
            } else {
                choices.copy(keepCustom = choices.keepCustom.toggled(ruleId, keep))
            }
        )
    }

    /** Replace all (false) or Keep all (true). */
    fun keepAll(keep: Boolean) = updatePreview { state ->
        state.copy(
            choices = if (keep) {
                RuleChoices(
                    keepCustom = changedIds(state.preview.customRules),
                    keepBuiltIn = changedIds(state.preview.builtInEdits)
                )
            } else {
                RuleChoices()
            }
        )
    }

    fun import() {
        val preview = _state.value as? SettingsImportState.Preview ?: return
        val opened = file ?: return
        _state.value = SettingsImportState.Working
        viewModelScope.launch {
            try {
                backup.apply(
                    opened,
                    preview.choices,
                    ZoneId.systemDefault(),
                    System.currentTimeMillis()
                )
                val outcome = preferences.apply(preview.file)
                val readAgain = ingestor.retryUnparsed()
                _state.value = SettingsImportState.Done(preview.preview, outcome, readAgain)
                // Last: it recreates the screen, which then shows the result in the new language.
                preferences.applyLanguage(preview.file)
            } catch (_: SQLException) {
                _state.value = failed(SettingsImportState.Failed.Reason.SAVE)
            }
        }
    }

    fun restart() {
        bytes = null
        file = null
        _state.value = SettingsImportState.Start
    }

    private fun failed(reason: SettingsImportState.Failed.Reason): SettingsImportState {
        bytes = null
        file = null
        return SettingsImportState.Failed(reason)
    }

    private fun updatePreview(
        change: (SettingsImportState.Preview) -> SettingsImportState.Preview
    ) = _state.update { if (it is SettingsImportState.Preview) change(it) else it }

    private fun changedIds(rules: List<RuleComparison>) =
        rules.filter { it.status == RuleStatus.CHANGED }.mapTo(HashSet()) { it.ruleId }

    private fun Set<String>.toggled(id: String, on: Boolean) = if (on) this + id else this - id
}
