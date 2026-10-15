package com.openhand.khata.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * Something that wants a tap, such as a bill coming due or a backup: a card tinted in its
 * [tone], with an icon tile, a line or two and a chevron. [action] names the tap when the title
 * doesn't make it obvious.
 */
@Composable
fun NoticeCard(
    icon: Painter,
    title: String,
    body: String?,
    tone: Tone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    action: String? = null
) {
    val color = tone.color()
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = FILL),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(Segments.Hairline, color.copy(alpha = EDGE))
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconTile(icon, tone, size = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                body?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                action?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelLarge,
                        color = color,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private const val FILL = 0.10f
private const val EDGE = 0.28f
