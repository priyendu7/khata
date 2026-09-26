package com.openhand.khata.feature.lock

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Unlocks with the phone's own screen lock. The system prompt opens straight away. */
@Composable
fun DeviceLockScreen(viewModel: LockViewModel) {
    val activity = LocalContext.current.findFragmentActivity()
    val title = stringResource(R.string.lock_prompt_title)
    val subtitle = stringResource(R.string.lock_prompt_subtitle)
    val prompt = { activity?.let { showDevicePrompt(it, title, subtitle, viewModel) } }

    LaunchedEffect(Unit) { prompt() }

    LockLayout(
        title = stringResource(R.string.lock_title),
        body = stringResource(R.string.lock_body_device),
        primaryAction = stringResource(R.string.lock_unlock) to { prompt() }
    )
}

private fun showDevicePrompt(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    viewModel: LockViewModel
) {
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            viewModel.setAuthenticating(false)
            viewModel.unlock()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            // Cancelled or unavailable: stay locked; the Unlock button tries again.
            viewModel.setAuthenticating(false)
        }
    }
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        // Strong biometrics or the phone's PIN/pattern/password. Android 9-10 can't combine
        // "strong" with the device credential, and on 8-10 the credential screen is a separate
        // activity (handled by AppLockManager.authenticating).
        .setAllowedAuthenticators(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                BIOMETRIC_STRONG or DEVICE_CREDENTIAL
            } else {
                BIOMETRIC_WEAK or DEVICE_CREDENTIAL
            }
        )
        .build()
    viewModel.setAuthenticating(true)
    BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
