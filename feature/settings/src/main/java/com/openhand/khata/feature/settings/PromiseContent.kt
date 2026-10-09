package com.openhand.khata.feature.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Our promise (#128): the first thing Khata shows, and again from Settings > About. Privacy is the
 * main reason to pick Khata, so it says so before anything else; as a statement in one box rather
 * than a list of cards, so it doesn't read like a permission request (#137). Without
 * [onGetStarted] there's no button (Settings has a back arrow instead).
 */
@Composable
fun PromiseContent(
    @DrawableRes appIcon: Int,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    animate: Boolean,
    modifier: Modifier = Modifier,
    onGetStarted: (() -> Unit)? = null
) {
    // Once only: a language change or rotation recreates the screen, and it shouldn't replay.
    var played by rememberSaveable { mutableStateOf(false) }
    val enter = remember { animate && !played }
    LaunchedEffect(Unit) { played = true }
    Box(modifier.fillMaxSize()) {
        // Clear of the language switch, which sits over the top-right corner.
        WelcomeColumn(Modifier.padding(top = LANGUAGE_ROOM.dp)) {
            Appear(0, enter) { WelcomeIllustration(appIcon) }
            Text(
                stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                stringResource(R.string.welcome_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Appear(1, enter) { PromiseBox() }
            onGetStarted?.let {
                Button(onClick = it, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(stringResource(R.string.welcome_get_started))
                }
            }
        }
        LanguageChoice(
            language,
            onLanguage,
            Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 16.dp)
        )
    }
}

/** The three promises side by side, each an icon and a short title. */
@Composable
private fun PromiseBox() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(Modifier.padding(vertical = 20.dp, horizontal = 8.dp)) {
            Text(
                stringResource(R.string.promise_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp, bottom = 16.dp)
            )
            Row(Modifier.fillMaxWidth()) {
                Promise(
                    R.drawable.ic_cloud_off,
                    R.string.promise_offline_title,
                    R.string.promise_offline_body
                )
                Promise(
                    R.drawable.ic_lock,
                    R.string.promise_encrypted_title,
                    R.string.promise_encrypted_body
                )
                Promise(
                    R.drawable.ic_code,
                    R.string.promise_open_title,
                    R.string.promise_open_body
                )
            }
        }
    }
}

/** One promise; TalkBack reads its title and the longer [body] the screen leaves out. */
@Composable
private fun RowScope.Promise(@DrawableRes icon: Int, @StringRes title: Int, @StringRes body: Int) {
    val description = stringResource(title) + " " + stringResource(body)
    Column(
        modifier = Modifier
            .weight(1f)
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = MARIGOLD)
        }
        Text(
            stringResource(title).trimEnd('.', '।'),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

/**
 * A column in the middle of the screen, that scrolls when it doesn't fit (landscape, large fonts)
 * and stays readable on a wide screen.
 */
@Composable
internal fun WelcomeColumn(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .heightIn(min = maxHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            WelcomeItems(content)
        }
    }
}

@Composable
private fun WelcomeItems(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(max = MAX_WIDTH.dp)
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .testTag(WELCOME_CONTENT_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content
    )
}

/** English / हिन्दी, small, applied at once so the rest of the screen is in the chosen language. */
@Composable
private fun LanguageChoice(
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    // "System default" shows as whichever of the two the phone is in.
    val shown = when (language) {
        AppLanguage.SYSTEM -> if (LocalConfiguration.current.locales[0].language ==
            AppLanguage.HINDI.tag
        ) {
            AppLanguage.HINDI
        } else {
            AppLanguage.ENGLISH
        }
        else -> language
    }
    Row(
        modifier
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .selectableGroup()
    ) {
        listOf(AppLanguage.ENGLISH, AppLanguage.HINDI).forEach { choice ->
            val selected = choice == shown
            Text(
                languageName(choice),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .selectable(selected, role = Role.RadioButton) {
                        if (!selected) onLanguage(choice)
                    }
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            Color.Unspecified
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/** [icon], drawn like the launcher icon, on its marigold circle. Decorative. */
@Composable
internal fun WelcomeIllustration(@DrawableRes icon: Int, size: Int = ICON_SIZE) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MARIGOLD),
        contentAlignment = Alignment.Center
    ) {
        Image(painterResource(icon), contentDescription = null, Modifier.size(size.dp))
    }
}

/** Fades and slides in, each [index] a little after the one before; at once if not [animate]. */
@Composable
internal fun Appear(index: Int, animate: Boolean, content: @Composable () -> Unit) {
    if (!animate) return content()
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    val delay = index * STAGGER_MS
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(tween(ENTER_MS, delayMillis = delay)) +
            slideInVertically(tween(ENTER_MS, delayMillis = delay)) { it / SLIDE_FRACTION }
    ) {
        content()
    }
}

/** The launcher icon's background. */
private val MARIGOLD = Color(0xFFF9A825)
private const val LANGUAGE_ROOM = 48
private const val ICON_SIZE = 96
private const val MAX_WIDTH = 560
private const val ENTER_MS = 450
private const val STAGGER_MS = 150
private const val SLIDE_FRACTION = 3
internal const val SOURCE_URL = "https://github.com/priyendu7/khata"
internal const val WELCOME_CONTENT_TAG = "welcome_content"
