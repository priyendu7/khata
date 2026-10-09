package com.openhand.khata.feature.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.openhand.khata.core.ui.KhataTheme
import com.openhand.khata.core.ui.SubScreen

/** Settings > About > Our promise: the welcome's first page again, without Get started. */
@Composable
fun OurPromiseScreen(@DrawableRes appIcon: Int, onBack: () -> Unit) {
    // Changing it recreates the activity, so this is read afresh in the new language.
    var language by remember { mutableStateOf(AppLanguage.current()) }
    // Khata's own colours, as on the welcome.
    KhataTheme(dynamicColor = false) { Promise(appIcon, onBack, language, { language = it }) }
}

@Composable
private fun Promise(
    @DrawableRes appIcon: Int,
    onBack: () -> Unit,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    SubScreen(title = stringResource(R.string.promise_title), onBack = onBack) { padding ->
        PromiseContent(
            appIcon = appIcon,
            language = language,
            onLanguage = {
                onLanguage(it)
                it.applyToApp()
            },
            animate = remember { !context.animationsOff() },
            modifier = Modifier.padding(padding),
            onOpenLink = { runCatching { uriHandler.openUri(it) } }
        )
    }
}
