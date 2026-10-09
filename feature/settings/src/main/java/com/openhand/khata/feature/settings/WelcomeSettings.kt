package com.openhand.khata.feature.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Whether the welcome (#128) was seen. It's about this install, so it isn't part of the settings
 * file.
 */
class WelcomeSettings @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var seen: Boolean
        get() = prefs.getBoolean(SEEN, false)
        set(value) = prefs.edit().putBoolean(SEEN, value).apply()

    /**
     * False after an update: someone updating from a Khata without the welcome may already have
     * data, so their welcome waits behind the lock.
     */
    val freshInstall: Boolean
        get() = context.packageManager.getPackageInfo(context.packageName, 0)
            .let { it.firstInstallTime == it.lastUpdateTime }

    private companion object {
        const val PREFS = "welcome"
        const val SEEN = "seen"
    }
}
