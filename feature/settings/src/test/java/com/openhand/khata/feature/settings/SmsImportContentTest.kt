package com.openhand.khata.feature.settings

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.openhand.khata.sms.ingest.ScanProgress
import com.openhand.khata.sms.ingest.ScanSummary
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmsImportContentTest {
    @get:Rule val compose = createComposeRule()

    private val toggles = mutableListOf<Boolean>()
    private val imports = mutableListOf<LocalDate>()
    private var cancelled = false
    private var openedSettings = false

    private fun show(
        on: Boolean = false,
        permissionStillAllowed: Boolean = on,
        permission: PermissionState = PermissionState.NOT_ASKED,
        progress: ScanProgress? = null,
        lastScan: ScanSummary? = null
    ) {
        compose.setContent {
            SmsImportContent(
                onBack = {},
                on = on,
                permissionStillAllowed = permissionStillAllowed,
                permission = permission,
                progress = progress,
                lastScan = lastScan,
                onToggle = { toggles += it },
                onOpenAppSettings = { openedSettings = true },
                onImport = { imports += it },
                onCancel = { cancelled = true },
                onFilters = {},
                onTestMessage = {},
                today = LocalDate.of(2026, 9, 29)
            )
        }
    }

    @Test
    fun explainsBeforeTheSwitchAndStartsOff() {
        show()

        compose.onNodeWithText("Everything stays on this phone", substring = true).assertExists()
        compose.onNodeWithText("Read bank SMS").assertIsOff().performClick()
        assertEquals(listOf(true), toggles)
        compose.onNodeWithText("Import past SMS").assertDoesNotExist()
    }

    @Test
    fun whenOnSuggestsTheFirstOfLastMonthAndImports() {
        show(on = true)

        compose.onNodeWithText("Read bank SMS").assertIsOn()
        compose.onNodeWithText("1 Aug 2026").assertExists()
        compose.onNodeWithText("Import").performScrollTo().performClick()
        assertEquals(listOf(LocalDate.of(2026, 8, 1)), imports)
    }

    @Test
    fun showsProgressAndCanCancel() {
        show(on = true, progress = ScanProgress(done = 12, total = 40, started = true))

        compose.onNodeWithText("Reading SMS: 12 of 40").assertExists()
        compose.onNodeWithText("Import").assertDoesNotExist()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        assertEquals(true, cancelled)
    }

    @Test
    fun showsTheLastImport() {
        val since = LocalDate.of(
            2026,
            8,
            1
        ).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        show(
            on = true,
            lastScan = ScanSummary(
                since,
                since,
                recorded = 42,
                toReview = 7,
                alreadyThere = 3,
                unreadable = 2,
                filtered = 212
            )
        )

        compose.onNodeWithText("Last import, from 1 Aug 2026").performScrollTo().assertExists()
        compose.onNodeWithText("Recorded: 42").assertExists()
        compose.onNodeWithText("New payees to review: 7").assertExists()
        compose.onNodeWithText("Already saved: 3").assertExists()
        compose.onNodeWithText("SMS that couldn't be read: 2").assertExists()
        compose.onNodeWithText("Filtered out: 212").assertExists()
    }

    @Test
    fun refusedPermissionSaysManualEntryStillWorks() {
        show(permission = PermissionState.REFUSED)

        compose.onNodeWithText("Permission not given", substring = true).assertExists()
        compose.onNodeWithText("Open app settings").assertDoesNotExist()
    }

    @Test
    fun blockedPermissionOffersTheAppSettings() {
        show(permission = PermissionState.BLOCKED)

        compose.onNodeWithText("turned off for Khata", substring = true).assertExists()
        compose.onNodeWithText("Open app settings").performClick()
        assertEquals(true, openedSettings)
    }

    @Test
    fun turnedOffWithThePermissionStillAllowedSaysHowToRemoveIt() {
        show(on = false, permissionStillAllowed = true)

        compose.onNodeWithText("still has the SMS permission", substring = true).assertExists()
        compose.onNodeWithText("Open app settings").assertExists()
    }
}
