package com.openhand.khata.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Category icons are emoji: they need no image files and look native on every phone. The key is
 * what's stored in the database (`categories.icon`); keys must never change.
 */
object CategoryIcons {
    val all: Map<String, String> = linkedMapOf(
        // The default categories (DefaultCategory) use the first ten.
        "food" to "🍛",
        "groceries" to "🛒",
        "travel" to "✈️",
        "rent" to "🏠",
        "work" to "💼",
        "bills" to "💡",
        "shopping" to "🛍️",
        "health" to "💊",
        "entertainment" to "🎬",
        "uncategorized" to "📦",
        "fuel" to "⛽",
        "transport" to "🚌",
        "taxi" to "🚕",
        "phone" to "📱",
        "internet" to "🌐",
        "education" to "🎓",
        "kids" to "🧸",
        "family" to "👪",
        "gifts" to "🎁",
        "festival" to "🪔",
        "donation" to "🙏",
        "pets" to "🐾",
        "personal_care" to "💇",
        "fitness" to "🏋️",
        "clothes" to "👕",
        "electronics" to "💻",
        "home_repair" to "🔧",
        "insurance" to "🛡️",
        "medical" to "🏥",
        "coffee" to "☕",
        "snacks" to "🍿",
        "salary" to "💰",
        "cash" to "💵",
        "savings" to "🏦",
        "label" to "🏷️"
    )

    /** What a category added from a picker starts with; the user can change it. */
    const val NEW_CATEGORY_ICON = "label"

    fun emoji(key: String): String = all[key] ?: all.getValue("uncategorized")
}

/** Colours offered for categories (ARGB). */
val CategoryColors: List<Int> = listOf(
    0xFFE65100, 0xFFC62828, 0xFFAD1457, 0xFF6A1B9A, 0xFF4527A0, 0xFF1565C0, 0xFF0277BD, 0xFF00838F,
    0xFF2E7D32, 0xFF558B2F, 0xFF9E9D24, 0xFFF9A825, 0xFFFF8F00, 0xFF5D4037, 0xFF37474F, 0xFF757575
).map { it.toInt() }

/** A category's emoji on a circle of its colour. */
@Composable
fun CategoryBadge(icon: String, color: Int, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(
            size
        ).clip(CircleShape).background(Color(color).copy(alpha = 0.18f))
    ) {
        Text(CategoryIcons.emoji(icon), fontSize = (size.value * 0.5f).sp)
    }
}
