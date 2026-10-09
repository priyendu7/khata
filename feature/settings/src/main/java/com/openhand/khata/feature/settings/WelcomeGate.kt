package com.openhand.khata.feature.settings

import androidx.compose.runtime.Composable

/**
 * Shows [welcome] instead of [app] while [welcoming]. On a fresh install it comes [beforeLock], so
 * a fingerprint prompt isn't the first thing a new user sees; otherwise it's behind [lock] too.
 */
@Composable
fun WelcomeGate(
    welcoming: Boolean,
    beforeLock: Boolean,
    welcome: @Composable () -> Unit,
    lock: @Composable (content: @Composable () -> Unit) -> Unit,
    app: @Composable () -> Unit
) {
    if (welcoming && beforeLock) {
        welcome()
    } else {
        lock { if (welcoming) welcome() else app() }
    }
}
