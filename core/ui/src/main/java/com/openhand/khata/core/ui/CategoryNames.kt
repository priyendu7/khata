package com.openhand.khata.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.openhand.khata.core.model.DefaultCategory

@StringRes
fun DefaultCategory.nameRes(): Int = when (this) {
    DefaultCategory.FOOD -> R.string.category_food
    DefaultCategory.GROCERIES -> R.string.category_groceries
    DefaultCategory.TRAVEL -> R.string.category_travel
    DefaultCategory.RENT -> R.string.category_rent
    DefaultCategory.WORK -> R.string.category_work
    DefaultCategory.BILLS_UTILITIES -> R.string.category_bills_utilities
    DefaultCategory.SHOPPING -> R.string.category_shopping
    DefaultCategory.HEALTH -> R.string.category_health
    DefaultCategory.ENTERTAINMENT -> R.string.category_entertainment
    DefaultCategory.UNCATEGORIZED -> R.string.category_uncategorized
}

/**
 * The name to show for a category: the user's own name if they set one, otherwise the default
 * category's name in the current language.
 */
@Composable
fun categoryName(name: String?, seedKey: String?): String = name
    ?: DefaultCategory.fromKey(seedKey)?.let { stringResource(it.nameRes()) }
    ?: stringResource(R.string.category_uncategorized)
