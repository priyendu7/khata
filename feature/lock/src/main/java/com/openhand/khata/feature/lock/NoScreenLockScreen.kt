package com.openhand.khata.feature.lock

import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

/** The phone has no screen lock: set one, use an app PIN instead, or continue without a lock. */
@Composable
fun NoScreenLockScreen(viewModel: LockViewModel) {
    val context = LocalContext.current
    var settingUpPin by rememberSaveable { mutableStateOf(false) }

    if (settingUpPin) {
        PinSetupFlow(viewModel, onFinished = viewModel::unlock, onCancel = { settingUpPin = false })
        return
    }
    LockLayout(
        title = stringResource(R.string.lock_no_screen_lock_title),
        body = stringResource(R.string.lock_no_screen_lock_body),
        primaryAction = stringResource(R.string.lock_set_screen_lock) to {
            context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        },
        secondaryActions = listOf(
            stringResource(R.string.lock_use_app_pin) to { settingUpPin = true },
            stringResource(R.string.lock_continue_without) to viewModel::continueWithoutLock
        )
    )
}
