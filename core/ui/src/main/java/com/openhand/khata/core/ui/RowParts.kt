package com.openhand.khata.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** A settings row's icon: a small tile in its section's tone. */
@Composable
fun RowIcon(@DrawableRes icon: Int, tone: Tone) {
    IconTile(painterResource(icon), tone, size = 36.dp)
}

/** The quiet arrow at the end of a row that opens something. */
@Composable
fun RowChevron() {
    Icon(
        painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(20.dp)
    )
}
