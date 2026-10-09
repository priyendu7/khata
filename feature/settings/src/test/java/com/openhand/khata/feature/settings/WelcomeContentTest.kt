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
import androidx.compose.ui.test.junit4.createComposeRule
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
        compose.onNodeWithText("No internet. Ever.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Encrypted on your phone.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Open source.").performScrollTo().assertIsDisplayed()
        assertFalse(promiseSeen)
    }

    @Test
    fun getStartedLeadsToTheSmsQuestion() {
        show()

        compose.onNodeWithText("Get started").performScrollTo().performClick()
        assertTrue(promiseSeen)
        compose.onNodeWithText("Read transaction messages automatically?").assertIsDisplayed()
        assertNull(done)
    }

    @Test
    fun yesOpensSmsImportSettings() {
        show()

        compose.onNodeWithText("Get started").performScrollTo().performClick()
        compose.onNodeWithText("Yes").performClick()
        assertEquals(true, done)
    }

    @Test
    fun laterGoesHome() {
        show()

        compose.onNodeWithText("Get started").performScrollTo().performClick()
        compose.onNodeWithText("Later").performClick()
        assertEquals(false, done)
    }

    @Test
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
        compose.onNodeWithText("शुरू करें").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun withoutAnimationsTheCardsAreInPlaceAtOnce() {
        // With the clock stopped, an entrance would leave the card part-way through its slide.
        compose.mainClock.autoAdvance = false
        show(animate = false)
        val still = compose.onNodeWithText("No internet. Ever.").getUnclippedBoundsInRoot().top
        compose.mainClock.advanceTimeBy(ENTRANCE_MS)
        assertEquals(
            still,
            compose.onNodeWithText("No internet. Ever.").getUnclippedBoundsInRoot().top
        )
    }

    @Test
    fun withAnimationsTheCardsSlideIn() {
        compose.mainClock.autoAdvance = false
        show(animate = true)
        compose.mainClock.advanceTimeByFrame()
        val start = compose.onNodeWithText("No internet. Ever.").getUnclippedBoundsInRoot().top
        compose.mainClock.advanceTimeBy(ENTRANCE_MS)
        val end = compose.onNodeWithText("No internet. Ever.").getUnclippedBoundsInRoot().top
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
