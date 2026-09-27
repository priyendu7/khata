package com.openhand.khata.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import com.openhand.khata.core.security.lock.LockSettings
import com.openhand.khata.core.ui.KhataTheme
import com.openhand.khata.feature.csv.BackupReminderNotifier
import com.openhand.khata.feature.lock.LockGate
import com.openhand.khata.feature.lock.setSecure
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// AppCompatActivity: a FragmentActivity (BiometricPrompt needs one) that also applies the in-app
// language (AppCompatDelegate.setApplicationLocales) on Android 8–12.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var lockSettings: LockSettings

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
                LockGate {
                    KhataNavigation(
                        openRequest = openRequest.value,
                        onOpened = { openRequest.value = null }
                    )
                }
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
