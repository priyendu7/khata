package com.openhand.khata.feature.lock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Hides the app in recent apps and blocks screenshots and screen recording (FLAG_SECURE). Compose
 * dialogs inherit it from the activity window.
 */
fun Window.setSecure(secure: Boolean) {
    if (secure) {
        setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

/** Keeps FLAG_SECURE in step with the "Block screenshots" setting while the app is running. */
@Composable
internal fun SecureWindowEffect(secure: Boolean) {
    val window = LocalContext.current.findActivity()?.window
    LaunchedEffect(window, secure) { window?.setSecure(secure) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
