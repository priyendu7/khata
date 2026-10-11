package com.openhand.khata.feature.categories

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.openhand.khata.core.data.SaveEventResult
import com.openhand.khata.core.model.Event
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Event times (#141) in the events list and the event dialog. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EventsContentTest {
    @get:Rule val compose = createComposeRule()

    private val day = LocalDate.now().withDayOfMonth(2)
    private val party = Event(
        id = 1,
        name = "Party",
        start = day,
        end = day,
        startTime = LocalTime.of(18, 0),
        endTime = LocalTime.of(23, 0)
    )
    private var saved: Event? = null

    private fun show(events: List<Event>) {
        compose.setContent {
            EventsContent(
                events = events,
                onSave = { event, onResult ->
                    saved = event
                    onResult(SaveEventResult.Saved(event.id))
                },
                onDelete = {},
                onBack = {}
            )
        }
    }

    @Test
    fun anEventWithTimesShowsThemAndAWholeDayEventShowsOnlyDates() {
        show(listOf(party, party.copy(id = 2, name = "Diwali", startTime = null, endTime = null)))

        compose.onNodeWithText("6:00", substring = true).assertIsDisplayed()
        compose.onNodeWithText("11:00", substring = true).assertIsDisplayed()
    }

    @Test
    fun turningTimesOffSavesAWholeDayEvent() {
        show(listOf(party))
        compose.onNodeWithText("Party").performClick()
        compose.onNodeWithText("Set times").performClick()
        compose.onNodeWithText("Save").performClick()

        assertEquals(party.copy(startTime = null, endTime = null), saved)
        assertFalse(saved!!.hasTimes)
    }

    @Test
    fun aOneDayEventEndingBeforeItStartsCantBeSaved() {
        show(listOf(party.copy(endTime = LocalTime.of(17, 0))))
        compose.onNodeWithText("Party").performClick()

        compose.onNodeWithText("The end must be after the start").performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsNotEnabled()

        // Whole days are fine again.
        compose.onNodeWithText("Set times").performScrollTo().performClick()
        compose.onNodeWithText("Save").assertIsEnabled()
    }
}
