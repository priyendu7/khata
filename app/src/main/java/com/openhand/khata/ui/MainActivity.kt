package com.openhand.khata.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.openhand.khata.core.security.lock.LockSettings
import com.openhand.khata.core.ui.KhataTheme
import com.openhand.khata.feature.lock.LockGate
import com.openhand.khata.feature.lock.setSecure
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// FragmentActivity (not ComponentActivity) because BiometricPrompt needs one.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var lockSettings: LockSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // "Block screenshots" (on by default): set before the first frame so the app's content is
        // never captured; LockGate keeps it in step if the setting changes.
        window.setSecure(lockSettings.blockScreenshots)
        setContent {
            KhataTheme {
                LockGate {
                    KhataNavigation()
                }
            }
        }
    }
}
