package com.openhand.khata.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.openhand.khata.R
import com.openhand.khata.core.security.lock.LockSettings
import com.openhand.khata.core.ui.KhataTheme
import com.openhand.khata.feature.csv.BackupReminderNotifier
import com.openhand.khata.feature.lock.LockGate
import com.openhand.khata.feature.lock.setSecure
import com.openhand.khata.feature.settings.WelcomeGate
import com.openhand.khata.feature.settings.WelcomeScreen
import com.openhand.khata.feature.settings.WelcomeSettings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// AppCompatActivity: a FragmentActivity (BiometricPrompt needs one) that also applies the in-app
// language (AppCompatDelegate.setApplicationLocales) on Android 8–12.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var lockSettings: LockSettings

    @Inject lateinit var welcomeSettings: WelcomeSettings

    /** A screen to open, from a tapped notification; cleared once opened. */
    private val openRequest = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // "Block screenshots" (on by default): set before the first frame so the app's content is
        // never captured; LockGate keeps it in step if the setting changes.
        window.setSecure(lockSettings.blockScreenshots)
        // Only on a fresh start: after a rotation the screen was already opened.
        if (savedInstanceState == null) openRequest.value = requestedScreen(intent)
        setContent {
            KhataTheme {
                // Kept across a language change or rotation, which recreate the activity.
                var welcoming by rememberSaveable { mutableStateOf(!welcomeSettings.seen) }
                val beforeLock = rememberSaveable { welcomeSettings.freshInstall }
                WelcomeGate(
                    welcoming = welcoming,
                    beforeLock = beforeLock,
                    welcome = {
                        WelcomeScreen(
                            appIcon = R.drawable.ic_launcher_foreground,
                            onPromiseSeen = { welcomeSettings.seen = true },
                            onDone = { smsImport ->
                                if (smsImport) openRequest.value = OPEN_SMS_IMPORT
                                welcoming = false
                            }
                        )
                    },
                    lock = { LockGate(content = it) },
                    app = {
                        KhataNavigation(
                            openRequest = openRequest.value,
                            onOpened = { openRequest.value = null }
                        )
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequest.value = requestedScreen(intent)
    }

    private fun requestedScreen(intent: Intent?) =
        intent?.getStringExtra(BackupReminderNotifier.EXTRA_OPEN)
}
