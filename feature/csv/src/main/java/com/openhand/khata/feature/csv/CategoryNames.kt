package com.openhand.khata.feature.csv

import android.content.Context
import android.content.res.Configuration
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.DefaultCategory
import com.openhand.khata.core.ui.nameRes
import java.util.Locale

/**
 * Default categories have no stored name; the app shows them in its current language. Export
 * writes that name, and import accepts it in any language the app speaks, so a file exported in
 * Hindi still imports on a phone set to English.
 */
class CategoryNames(
    /** Seed key → name in the current language. */
    private val current: Map<String, String>,
    /** Lowercase name in any language → seed key. */
    val aliases: Map<String, String>
) {
    fun nameOf(category: Category): String = category.name
        ?: category.seedKey?.let { current[it] }
        ?: current.getValue(DefaultCategory.UNCATEGORIZED.key)

    companion object {
        private val LANGUAGES = listOf(Locale.ENGLISH, Locale.forLanguageTag("hi"))

        /** Names from [context], which must carry the app's current language (an activity). */
        fun from(context: Context): CategoryNames {
            val current = DefaultCategory.entries.associate {
                it.key to context.getString(it.nameRes())
            }
            val aliases = HashMap<String, String>()
            val localized = LANGUAGES.map { locale ->
                val config = Configuration(context.resources.configuration)
                config.setLocale(locale)
                context.createConfigurationContext(config)
            }
            (localized + context).forEach { localizedContext ->
                DefaultCategory.entries.forEach { category ->
                    val name = localizedContext.getString(category.nameRes()).lowercase()
                    aliases[name] = category.key
                }
            }
            return CategoryNames(current, aliases)
        }
    }
}
