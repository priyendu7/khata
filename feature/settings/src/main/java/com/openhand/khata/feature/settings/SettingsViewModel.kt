package com.openhand.khata.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.data.AccountRepository
import com.openhand.khata.core.data.BackupRepository
import com.openhand.khata.core.data.CategoryRepository
import com.openhand.khata.core.data.CustomParserRepository
import com.openhand.khata.core.data.EventRepository
import com.openhand.khata.core.data.IgnoreRuleRepository
import com.openhand.khata.core.data.PayeeRepository
import com.openhand.khata.core.data.TagRepository
import com.openhand.khata.core.model.AppInfo
import com.openhand.khata.core.model.Event
import com.openhand.khata.sms.ingest.SmsImportSettings
import com.openhand.khata.sms.parser.BuiltInRules
import com.openhand.khata.sms.parser.SmsFilters
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** What each Settings row shows as its current state. */
data class SettingsSummary(
    val smsEnabled: Boolean = false,
    /** The day the last inbox import finished; null if there wasn't one. */
    val lastScan: LocalDate? = null,
    val filtersOn: Int = FilterSwitch.entries.size,
    val ignoreRules: Int = 0,
    val builtInParsers: Int = 0,
    val customParsers: Int = 0,
    val accounts: Int = 0,
    /** Categories that aren't archived. */
    val categories: Int = 0,
    val tags: Int = 0,
    val payees: Int = 0,
    val events: Int = 0,
    /** The name of the event that starts last. */
    val newestEvent: String? = null,
    val lastExport: LocalDate? = null
)

/** Settings reads a count or state from almost every repository, one per row. */
@Suppress("LongParameterList")
@HiltViewModel
class SettingsViewModel
@Inject
constructor(
    appInfo: AppInfo,
    accounts: AccountRepository,
    categories: CategoryRepository,
    tags: TagRepository,
    payees: PayeeRepository,
    events: EventRepository,
    customParsers: CustomParserRepository,
    ignoreRules: IgnoreRuleRepository,
    backup: BackupRepository,
    sms: SmsImportSettings
) : ViewModel() {
    val versionName: String = appInfo.versionName

    private val lists = combine(
        accounts.observeAccounts(),
        categories.observeActive(),
        tags.observeTags(),
        payees.observePayees(),
        events.observeEvents()
    ) { a, c, t, p, e ->
        SettingsSummary(
            accounts = a.size,
            categories = c.size,
            tags = t.size,
            payees = p.size,
            events = e.size,
            newestEvent = newestEvent(e)?.name
        )
    }

    // Reading the built-in rules parses their files, so it's done once, off the main thread.
    private val builtInCount = flow { emit(BuiltInRules.all().size) }.flowOn(Dispatchers.Default)

    val summary: StateFlow<SettingsSummary> = combine(
        lists,
        combine(sms.enabled, sms.lastScan, sms.filters, ::Triple),
        ignoreRules.observeAll(),
        combine(customParsers.observeAll(), builtInCount, ::Pair),
        backup.observeLastExport()
    ) { summary, (smsOn, scan, filters), rules, (custom, builtIn), exported ->
        summary.copy(
            smsEnabled = smsOn,
            lastScan = scan?.finishedAt?.let(::dayOf),
            filtersOn = filtersOn(filters),
            ignoreRules = rules.size,
            builtInParsers = builtIn,
            customParsers = custom.size,
            lastExport = exported?.let(::dayOf)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), SettingsSummary())

    private fun dayOf(millis: Long) =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    private companion object {
        const val STOP_AFTER_MS = 5_000L
    }
}

internal fun filtersOn(filters: SmsFilters) = FilterSwitch.entries.count { it.isOn(filters) }

/** The event that starts last; of those starting the same day, the one added last. */
internal fun newestEvent(events: List<Event>): Event? =
    events.maxWithOrNull(compareBy<Event> { it.start }.thenBy { it.id })
