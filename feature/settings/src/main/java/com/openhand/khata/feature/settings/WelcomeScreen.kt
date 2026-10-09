package com.openhand.khata.feature.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.openhand.khata.core.ui.KhataTheme

/**
 * The welcome (#128), shown once on the very first open: [PromiseContent], then a question about
 * SMS import. [onPromiseSeen] is called at Get started; [onDone] when it's over, with true if the
 * user wants Settings > SMS import, which explains it and asks for the permission.
 */
@Composable
fun WelcomeScreen(
    @DrawableRes appIcon: Int,
    onPromiseSeen: () -> Unit,
    onDone: (smsImport: Boolean) -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    // Changing it recreates the activity, so this is read afresh in the new language.
    var language by remember { mutableStateOf(AppLanguage.current()) }
    WelcomeContent(
        appIcon = appIcon,
        language = language,
        onLanguage = {
            language = it
            it.applyToApp()
        },
        animate = remember { !context.animationsOff() },
        onPromiseSeen = onPromiseSeen,
        onDone = onDone,
        onOpenLink = { runCatching { uriHandler.openUri(it) } }
    )
}

@Composable
fun WelcomeContent(
    @DrawableRes appIcon: Int,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    animate: Boolean,
    onPromiseSeen: () -> Unit,
    onDone: (smsImport: Boolean) -> Unit,
    onOpenLink: (String) -> Unit = {}
) {
    var smsStep by rememberSaveable { mutableStateOf(false) }
    // Always Khata's red and marigold, not the phone's dynamic colours: it's the first impression.
    KhataTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            val modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
            if (smsStep) {
                SmsStep(animate, onDone, modifier)
            } else {
                PromiseContent(
                    appIcon = appIcon,
                    language = language,
                    onLanguage = onLanguage,
                    animate = animate,
                    modifier = modifier,
                    onOpenLink = onOpenLink,
                    onGetStarted = {
                        onPromiseSeen()
                        smsStep = true
                    }
                )
            }
        }
    }
}

/** Just the question: Yes opens Settings > SMS import, Later goes to Home. */
@Composable
private fun SmsStep(animate: Boolean, onDone: (smsImport: Boolean) -> Unit, modifier: Modifier) {
    WelcomeColumn(modifier) {
        Appear(0, animate) { WelcomeIllustration(R.drawable.welcome_sms, SMS_ILLUSTRATION_SIZE) }
        Text(
            stringResource(R.string.welcome_sms_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Button(onClick = { onDone(true) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.welcome_sms_yes))
        }
        FilledTonalButton(onClick = { onDone(false) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.welcome_sms_later))
        }
    }
}

private const val SMS_ILLUSTRATION_SIZE = 160
