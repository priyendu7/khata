package com.openhand.khata.feature.settings

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val opened = mutableListOf<SettingsPage>()
    private val links = mutableListOf<String>()

    private fun show(summary: SettingsSummary = SettingsSummary()) {
        compose.setContent {
            SettingsScreen(
                versionName = "1.0",
                summary = summary,
                onOpen = { opened += it },
                onOpenLink = { links += it }
            )
        }
    }

    @Test
    fun sectionsAreHeadingsInOrder() {
        show()
        val sections =
            listOf("SMS", "Your lists", "Backup & restore", "Security", "App", "About")
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertCountEquals(sections.size)
        val tops = sections.map {
            compose.onNodeWithText(it).fetchSemanticsNode().positionInRoot.y
        }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun rowsShowTheirState() {
        show(
            SettingsSummary(
                smsEnabled = true,
                lastScan = LocalDate.of(2026, 10, 2),
                filtersOn = 5,
                ignoreRules = 2,
                builtInParsers = 40,
                customParsers = 1,
                accounts = 3,
                categories = 12,
                tags = 1,
                payees = 25,
                events = 2,
                newestEvent = "Diwali",
                lastExport = LocalDate.of(2026, 10, 1)
            )
        )
        listOf(
            "On · last scan 2 Oct 2026",
            "5 filters on · 2 ignore rules",
            "40 built-in · 1 custom",
            "3 accounts",
            "12 categories",
            "1 tag",
            "25 payees",
            "2 events · Diwali",
            "CSV file · last export 1 Oct 2026"
        ).forEach { compose.onNodeWithText(it).performScrollTo() }
    }

    @Test
    fun emptyStateReadsNaturally() {
        show()
        listOf("Off", "0 accounts", "0 events", "CSV file · never exported")
            .forEach { compose.onNodeWithText(it).performScrollTo() }
    }

    @Test
    fun eachRowOpensItsPage() {
        show()
        val rows = linkedMapOf(
            "SMS import" to SettingsPage.SMS_IMPORT,
            "Filters" to SettingsPage.FILTERS,
            "Test a message" to SettingsPage.TEST_MESSAGE,
            "Parsers" to SettingsPage.PARSERS,
            "Accounts" to SettingsPage.ACCOUNTS,
            "Categories" to SettingsPage.CATEGORIES,
            "Tags" to SettingsPage.TAGS,
            "Payees" to SettingsPage.PAYEES,
            "Events" to SettingsPage.EVENTS,
            "Export transactions" to SettingsPage.EXPORT,
            "Import transactions" to SettingsPage.IMPORT
        )
        rows.keys.forEach { compose.onNodeWithText(it).performScrollTo().performClick() }
        assertEquals(rows.values.toList(), opened)
    }

    @Test
    fun privacyOpensThePolicy() {
        show()
        compose.onNodeWithText("Privacy").performScrollTo().performClick()
        assertTrue(links.single().endsWith("PRIVACY.md"))
    }

    @Test
    fun securityRowsGoInTheirSection() {
        compose.setContent {
            SettingsScreen(versionName = "1.0", security = { Text("Lock rows") })
        }
        val security = compose.onNodeWithText("Security").fetchSemanticsNode().positionInRoot.y
        val rows = compose.onNodeWithText("Lock rows").fetchSemanticsNode().positionInRoot.y
        val app = compose.onNodeWithText("App").fetchSemanticsNode().positionInRoot.y
        assertTrue(rows > security && rows < app)
        compose.onAllNodesWithText("Lock rows").assertCountEquals(1)
    }
}
