package com.openhand.khata.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.openhand.khata.BuildConfig
import com.openhand.khata.core.ui.KhataTheme
import com.openhand.khata.feature.lock.LockGate
import dagger.hilt.android.AndroidEntryPoint

// FragmentActivity (not ComponentActivity) because BiometricPrompt needs one.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Hide the app in recent apps and block screenshots (PRD privacy principle 4). Debug builds
        // skip it so developers can take store screenshots and use screen recording.
        if (!BuildConfig.DEBUG) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        setContent {
            KhataTheme {
                LockGate {
                    KhataNavigation()
                }
            }
        }
    }
}
