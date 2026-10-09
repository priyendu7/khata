package com.openhand.khata.feature.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.ui.KhataTheme
import com.openhand.khata.core.ui.R as UiR

/**
 * The welcome (#128), shown once on the very first open: [PromiseContent], then how to add
 * transactions (#137). [onPromiseSeen] is called at Get started; [onDone] when it's over, with
 * true if the user chose SMS: Settings > SMS import explains it and asks for the permission.
 */
@Composable
fun WelcomeScreen(
    @DrawableRes appIcon: Int,
    onPromiseSeen: () -> Unit,
    onDone: (smsImport: Boolean) -> Unit
) {
    val context = LocalContext.current
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
        onDone = onDone
    )
}

@Composable
fun WelcomeContent(
    @DrawableRes appIcon: Int,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    animate: Boolean,
    onPromiseSeen: () -> Unit,
    onDone: (smsImport: Boolean) -> Unit
) {
    var smsStep by rememberSaveable { mutableStateOf(false) }
    // Always Khata's red and marigold, not the phone's dynamic colours: it's the first impression.
    KhataTheme(dynamicColor = false) {
        MarigoldInTheDark {
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
                        onGetStarted = {
                            onPromiseSeen()
                            smsStep = true
                        }
                    )
                }
            }
        }
    }
}

/**
 * In dark mode the brand red is drawn light enough to read on black, which looks pink; the
 * welcome uses the launcher icon's marigold for its accents there instead.
 */
@Composable
private fun MarigoldInTheDark(content: @Composable () -> Unit) {
    if (!isSystemInDarkTheme()) return content()
    val scheme = MaterialTheme.colorScheme
    MaterialTheme(
        colorScheme = scheme.copy(primary = scheme.secondary, onPrimary = scheme.onSecondary),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}

/**
 * How to add transactions, as two equal choices rather than a yes-or-no question, so it doesn't
 * read like a permission prompt; the permission itself is asked in Settings > SMS import.
 */
@Composable
private fun SmsStep(animate: Boolean, onDone: (smsImport: Boolean) -> Unit, modifier: Modifier) {
    WelcomeColumn(modifier) {
        Appear(0, animate) { WelcomeIllustration(R.drawable.welcome_sms, SMS_ILLUSTRATION_SIZE) }
        Text(
            stringResource(R.string.welcome_add_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Appear(1, animate) {
            ChoiceCard(
                R.drawable.ic_sms,
                R.string.welcome_add_sms_title,
                R.string.welcome_add_sms_body
            ) { onDone(true) }
        }
        Appear(2, animate) {
            ChoiceCard(
                R.drawable.ic_edit,
                R.string.welcome_add_hand_title,
                R.string.welcome_add_hand_body
            ) { onDone(false) }
        }
    }
}

/** A choice read by TalkBack as one button: its title and line. */
@Composable
private fun ChoiceCard(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes body: Int,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
            }
            Icon(painterResource(UiR.drawable.ic_chevron_right), contentDescription = null)
        }
    }
}

private const val SMS_ILLUSTRATION_SIZE = 160
