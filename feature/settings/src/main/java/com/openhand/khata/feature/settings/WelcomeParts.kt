package com.openhand.khata.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.ui.Segments

/** A row's bold title over its quieter line, as in the promises and the choices. */
@Composable
internal fun RowScope.WelcomeText(title: String, body: String) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The welcome's one wide marigold button, below its card. */
@Composable
internal fun WelcomeButton(@StringRes text: Int, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = Segments.SmallShape,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .heightIn(min = BUTTON_HEIGHT.dp)
    ) {
        Text(stringResource(text), style = MaterialTheme.typography.titleMedium)
    }
}

/** A soft marigold light behind the top of the screen, as in the design. */
internal fun Modifier.marigoldGlow(): Modifier = drawBehind {
    val radius = GLOW_RADIUS.dp.toPx()
    val center = Offset(size.width / 2, GLOW_CENTER.dp.toPx())
    drawCircle(
        Brush.radialGradient(
            0f to MARIGOLD.copy(alpha = GLOW_ALPHA),
            GLOW_STOP to Color.Transparent,
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

private const val BUTTON_HEIGHT = 52
private const val GLOW_RADIUS = 260
private const val GLOW_CENTER = 120
private const val GLOW_ALPHA = 0.16f
private const val GLOW_STOP = 0.65f
