package com.openhand.khata.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun readsTheStoredLanguageTags() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags(""))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTags("en"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTags("en-IN"))
        assertEquals(AppLanguage.HINDI, AppLanguage.fromTags("hi"))
        assertEquals(AppLanguage.HINDI, AppLanguage.fromTags("hi-IN,en"))
        // A language the app doesn't have falls back to the phone's.
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags("ta-IN"))
    }

    @Test
    fun tagsRoundTrip() {
        AppLanguage.entries.forEach { assertEquals(it, AppLanguage.fromTags(it.tag)) }
    }
}
