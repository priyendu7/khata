package com.openhand.khata.core.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * Focuses the field and opens the keyboard when it appears, and again whenever [key] changes (e.g.
 * moving from "Choose a PIN" to "Enter the PIN again").
 */
fun Modifier.focusOnAppear(key: Any): Modifier = composed {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(key) {
        focus.requestFocus()
        keyboard?.show()
    }
    focusRequester(focus)
}
