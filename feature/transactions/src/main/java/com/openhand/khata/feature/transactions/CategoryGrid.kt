package com.openhand.khata.feature.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.ui.CategoryBadge
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.Segments
import com.openhand.khata.core.ui.categoryName

/**
 * Every category as a tile, four to a row, with Uncategorized last and a "new category" tile
 * after it. [selectedId] null is Uncategorized, as in the form; picking it gives null too.
 */
@Composable
internal fun CategoryGrid(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onNew: () -> Unit
) {
    val sorted = categories.sortedBy { it.isUncategorized }
    val tiles: List<Category?> = sorted + listOf(null)
    Column(verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
        tiles.chunked(COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                row.forEach { category ->
                    val tileModifier = Modifier.weight(1f)
                    if (category == null) {
                        NewTile(onNew, tileModifier)
                    } else {
                        val picked = if (category.isUncategorized) {
                            selectedId == null || selectedId == category.id
                        } else {
                            selectedId == category.id
                        }
                        CategoryTile(category, picked, tileModifier) {
                            onSelect(category.id.takeUnless { category.isUncategorized })
                        }
                    }
                }
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CategoryTile(
    category: Category,
    picked: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val color = Color(category.color)
    val colors = MaterialTheme.colorScheme
    TileBox(
        modifier = modifier.selectable(picked, role = Role.RadioButton, onClick = onClick),
        background = if (picked) color.copy(alpha = PICKED_TINT) else colors.surfaceContainerLowest,
        border = if (picked) {
            BorderStroke(PICKED_BORDER, color)
        } else {
            BorderStroke(Segments.Hairline, colors.outlineVariant)
        }
    ) {
        CategoryBadge(category.icon, category.color, size = 32.dp)
        TileLabel(categoryName(category.name, category.seedKey))
    }
}

@Composable
private fun NewTile(onClick: () -> Unit, modifier: Modifier) {
    TileBox(
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
        background = Color.Transparent,
        border = BorderStroke(Segments.Hairline, MaterialTheme.colorScheme.outline)
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(UiR.drawable.ic_add), contentDescription = null)
        }
        TileLabel(stringResource(UiR.string.category_new))
    }
}

@Composable
private fun TileBox(
    modifier: Modifier,
    background: Color,
    border: BorderStroke,
    content: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        modifier = modifier
            .heightIn(min = TILE_HEIGHT)
            .clip(TileShape)
            .background(background)
            .border(border, TileShape)
            .padding(horizontal = 4.dp, vertical = 8.dp)
    ) { content() }
}

@Composable
private fun TileLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
    )
}

private const val COLUMNS = 4
private const val PICKED_TINT = 0.12f
private val PICKED_BORDER = 1.5.dp
private val TILE_GAP = 8.dp
private val TILE_HEIGHT = 76.dp
private val TileShape = RoundedCornerShape(16.dp)
