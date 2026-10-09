package com.openhand.khata.feature.settings

import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WelcomeContentTest {
    @get:Rule val compose = createComposeRule()

    private val languages = mutableListOf<AppLanguage>()
    private var promiseSeen = false
    private var done: Boolean? = null

    private fun show(animate: Boolean = false) {
        compose.setContent {
            var language by remember { mutableStateOf(AppLanguage.ENGLISH) }
            WelcomeContent(
                appIcon = R.drawable.ic_lock,
                language = language,
                onLanguage = {
                    languages += it
                    language = it
                },
                animate = animate,
                onPromiseSeen = { promiseSeen = true },
                onDone = { done = it }
            )
        }
    }

    @Test
    fun showsThePromiseFirst() {
        show()

        compose.onNodeWithText("Welcome to Khata").assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "No internet. Ever.",
            substring = true
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "Encrypted on your phone.",
            substring = true
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "Open source.",
            substring = true
        ).performScrollTo().assertIsDisplayed()
        // A statement, not a request: no link out, nothing to tap but Get started.
        compose.onNodeWithText("github.com/priyendu7/khata").assertDoesNotExist()
        assertFalse(promiseSeen)
    }

    @Test
    fun eachPromiseReadsItsLongerLineToTalkBack() {
        show()

        compose.onNodeWithContentDescription(
            "No internet. Ever. Khata has no internet permission.",
            substring = true
        ).assertExists()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun theLanguageSwitchIsInTheTopRightCorner() {
        show()

        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val english = compose.onNodeWithText("English").getUnclippedBoundsInRoot()
        val title = compose.onNodeWithText("Welcome to Khata").getUnclippedBoundsInRoot()
        assertTrue(english.left > (root.left + root.right) / 2)
        assertTrue(english.bottom < title.top)
    }

    @Test
    fun getStartedLeadsToHowToAddTransactions() {
        show()

        compose.onNodeWithText("Get started").performScrollTo().performClick()
        assertTrue(promiseSeen)
        compose.onNodeWithText("How do you want to add transactions?").assertIsDisplayed()
        compose.onNodeWithText("Automatically from bank SMS", substring = true).assertIsDisplayed()
        compose.onNodeWithText("By hand", substring = true).assertIsDisplayed()
        // Two choices, not a permission-style yes or no.
        compose.onNodeWithText("Yes").assertDoesNotExist()
        compose.onNodeWithText("Later").assertDoesNotExist()
        assertNull(done)
    }

    @Test
    fun choosingSmsOpensSmsImportSettings() {
        show()

        compose.onNodeWithText("Get started").performScrollTo().performClick()
        compose.onNodeWithText("Automatically from bank SMS", substring = true).performClick()
        assertEquals(true, done)
    }

    @Test
    fun choosingByHandGoesHome() {
        show()

        compose.onNodeWithText("Get started").performScrollTo().performClick()
        compose.onNodeWithText("By hand", substring = true).performScrollTo().performClick()
        assertEquals(false, done)
    }

    @Test
    fun eachChoiceIsOneButtonWithItsTitleAndLine() {
        show()

        compose.onNodeWithText("Get started").performScrollTo().performClick()
        compose.onNode(
            hasClickAction() and hasText("By hand") and
                hasText("Add each one with +", substring = true)
        ).assertExists()
    }

    @Test
    // A phone-sized screen; Robolectric's default is too short for the two choices to fit.
    @Config(qualifiers = "w411dp-h891dp")
    fun theQuestionIsInTheMiddleOfTheScreen() {
        show()

        compose.onNodeWithText("Get started").performScrollTo().performClick()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val content = compose.onNodeWithTag(WELCOME_CONTENT_TAG).getUnclippedBoundsInRoot()
        // As much space above it as below.
        assertEquals((content.top - root.top).value, (root.bottom - content.bottom).value, 1f)
    }

    @Test
    fun switchesTheLanguage() {
        show()

        compose.onNodeWithText("English").assertIsSelected()
        compose.onNodeWithText("हिन्दी").performClick()
        assertEquals(listOf(AppLanguage.HINDI), languages)
        compose.onNodeWithText("हिन्दी").assertIsSelected()
        compose.onNodeWithText("English").assertIsNotSelected()
        // Choosing the language already shown does nothing.
        compose.onNodeWithText("हिन्दी").performClick()
        assertEquals(listOf(AppLanguage.HINDI), languages)
    }

    @Test
    @Config(qualifiers = "hi")
    fun isInHindi() {
        show()

        compose.onNodeWithText("खाता में आपका स्वागत है").assertIsDisplayed()
        compose.onNodeWithText("शुरू करें").performScrollTo().performClick()
        compose.onNodeWithText("लेन-देन कैसे जोड़ना चाहेंगे?").assertIsDisplayed()
        compose.onNodeWithText("खुद से", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun withoutAnimationsTheCardsAreInPlaceAtOnce() {
        // With the clock stopped, an entrance would leave the card part-way through its slide.
        compose.mainClock.autoAdvance = false
        show(animate = false)
        val still = compose.onNodeWithContentDescription(
            "No internet. Ever.",
            substring = true
        ).getUnclippedBoundsInRoot().top
        compose.mainClock.advanceTimeBy(ENTRANCE_MS)
        assertEquals(
            still,
            compose.onNodeWithContentDescription(
                "No internet. Ever.",
                substring = true
            ).getUnclippedBoundsInRoot().top
        )
    }

    @Test
    fun withAnimationsTheCardsSlideIn() {
        compose.mainClock.autoAdvance = false
        show(animate = true)
        compose.mainClock.advanceTimeByFrame()
        val start = compose.onNodeWithContentDescription(
            "No internet. Ever.",
            substring = true
        ).getUnclippedBoundsInRoot().top
        compose.mainClock.advanceTimeBy(ENTRANCE_MS)
        val end = compose.onNodeWithContentDescription(
            "No internet. Ever.",
            substring = true
        ).getUnclippedBoundsInRoot().top
        assertTrue(start > end)
    }

    @Test
    fun followsTheRemoveAnimationsSetting() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertFalse(context.animationsOff())
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        assertTrue(context.animationsOff())
    }

    private companion object {
        const val ENTRANCE_MS = 2_000L
    }
}
