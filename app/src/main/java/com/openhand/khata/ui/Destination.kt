package com.openhand.khata.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.openhand.khata.core.ui.R as UiR

/** The bottom-navigation tabs. */
enum class Destination(@StringRes val label: Int, @DrawableRes val icon: Int) {
    HOME(UiR.string.nav_home, UiR.drawable.ic_tab_home),
    TRANSACTIONS(UiR.string.nav_transactions, UiR.drawable.ic_tab_list),
    INSIGHTS(UiR.string.nav_insights, UiR.drawable.ic_tab_insights),
    CARDS(UiR.string.nav_cards, UiR.drawable.ic_tab_cards),
    SETTINGS(UiR.string.nav_settings, UiR.drawable.ic_tab_settings)
}
