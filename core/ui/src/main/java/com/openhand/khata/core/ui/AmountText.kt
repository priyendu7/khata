package com.openhand.khata.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.openhand.khata.core.model.Money

/**
 * A headline amount: the rupees large, with the ₹ sign and the paise smaller and quieter, so the
 * number that matters stands out. Reads (and tests) as the plain [Money.format] text.
 */
@Composable
fun AmountText(
    paise: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displayMedium,
    color: Color = Color.Unspecified
) {
    val text = Money.format(paise)
    val quiet = SpanStyle(
        fontSize = style.fontSize * SMALL,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val symbol = text.indexOf('₹')
    val point = text.indexOf('.').takeIf { it >= 0 } ?: text.length
    val annotated = buildAnnotatedString {
        append(text.substring(0, symbol))
        withStyle(quiet) { append("₹") }
        append(text.substring(symbol + 1, point))
        withStyle(quiet) { append(text.substring(point)) }
    }
    Text(annotated, modifier, color = color, style = style, maxLines = 1, softWrap = false)
}

private const val SMALL = 0.58f
