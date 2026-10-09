package com.openhand.khata.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.openhand.khata.core.data.MatchCounts
import com.openhand.khata.core.data.RuleChoices
import com.openhand.khata.core.data.RuleComparison
import com.openhand.khata.core.data.RuleStatus
import com.openhand.khata.core.data.SettingsPreview
import com.openhand.khata.core.security.lock.LockTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportSettingsContentTest {
    @get:Rule val compose = createComposeRule()

    private fun show(state: SettingsImportState, onUnlock: (String) -> Unit = {}) {
        compose.setContent {
            ImportSettingsContent(
                state = state,
                onChooseFile = {},
                onUnlock = onUnlock,
                onKeep = { _, _, _ -> },
                onKeepAll = {},
                onImport = {},
                onBack = {}
            )
        }
    }

    private val preview = SettingsPreview(
        customRules = listOf(
            RuleComparison("hdfc-upi", RuleStatus.CHANGED, true),
            RuleComparison("icici-card", RuleStatus.CHANGED, false),
            RuleComparison("sbi-credit", RuleStatus.NEW, true)
        ),
        categories = MatchCounts(new = 1, updated = 2, same = 9)
    )

    @Test
    fun aWrongPasswordSaysSoAndChangesNothing() {
        var tried: String? = null
        show(SettingsImportState.Password(wrongPassword = true)) { tried = it }

        compose.onNodeWithText("Wrong password, or the file is damaged. Nothing was changed.")
            .assertIsDisplayed()
        compose.onNodeWithText("Password").performTextInput("another try")
        compose.onNodeWithText("Open").performClick()
        assertEquals("another try", tried)
    }

    @Test
    fun aNewerFileIsRefused() {
        show(SettingsImportState.Failed(SettingsImportState.Failed.Reason.NEWER_VERSION))

        compose.onNodeWithText("made by a newer Khata", substring = true).assertIsDisplayed()
    }

    @Test
    fun eachChangedRuleHasItsOwnChoice() {
        compose.setContent {
            var state by remember {
                mutableStateOf(
                    SettingsImportState.Preview(
                        preview,
                        current = AppPrefs(lockTimeout = LockTimeout.MINUTE_1),
                        file = AppPrefs(lockTimeout = LockTimeout.MINUTES_5)
                    )
                )
            }
            ImportSettingsContent(
                state = state,
                onChooseFile = {},
                onUnlock = {},
                onKeep = { id, _, keep ->
                    val kept = state.choices.keepCustom
                    state = state.copy(
                        choices = RuleChoices(keepCustom = if (keep) kept + id else kept - id)
                    )
                },
                onKeepAll = { keep ->
                    state = state.copy(
                        choices = RuleChoices(
                            keepCustom = if (keep) setOf("hdfc-upi", "icici-card") else emptySet()
                        )
                    )
                },
                onImport = {},
                onBack = {}
            )
        }
        val replace = compose.onAllNodesWithText("Replace")
        val keepMine = compose.onAllNodesWithText("Keep mine")

        replace[0].assertIsSelected()
        replace[1].assertIsSelected()
        keepMine[1].performScrollTo().performClick()

        replace[0].assertIsSelected()
        keepMine[0].assertIsNotSelected()
        keepMine[1].assertIsSelected()
        replace[1].assertIsNotSelected()

        compose.onNodeWithText("Keep all mine").performScrollTo().performClick()
        keepMine[0].assertIsSelected()
        compose.onNodeWithText("Replace all").performScrollTo().performClick()
        replace[0].assertIsSelected()
        replace[1].assertIsSelected()

        compose.onNodeWithText("New · On").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1 new · 2 will be updated · 9 already here").performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("After 1 minute → After 5 minutes").performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun theResultOffersWhatTheFileCouldNotDo() {
        show(
            SettingsImportState.Done(
                SettingsPreview(),
                PrefsOutcome(offerSmsImport = true, needsPin = true),
                readAgain = 2
            )
        )

        compose.onNodeWithText("The new rules read 2 SMS that were waiting in To review")
            .assertIsDisplayed()
        compose.onNodeWithText("Set app PIN").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Turn on SMS import").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Import transactions").performScrollTo().assertIsDisplayed()
    }
}
