package com.openhand.khata.feature.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * The in-app language (Settings → Language). Android 13+ keeps the choice itself and also offers it
 * in the phone's Settings; on Android 8–12 AppCompat stores it and applies it on every start.
 */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    HINDI("hi");

    /** Switches the app's language; open screens are recreated in it straight away. */
    fun applyToApp() =
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))

    companion object {
        /** The language chosen in the app, or [SYSTEM] if none was. */
        fun current(): AppLanguage =
            fromTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())

        /** Maps stored language tags (e.g. `hi-IN,en`) to a choice; only the first one counts. */
        fun fromTags(tags: String): AppLanguage {
            val first = tags.substringBefore(',').trim()
            if (first.isEmpty()) return SYSTEM
            val language = Locale.forLanguageTag(first).language
            return entries.firstOrNull { it != SYSTEM && it.tag == language } ?: SYSTEM
        }
    }
}
