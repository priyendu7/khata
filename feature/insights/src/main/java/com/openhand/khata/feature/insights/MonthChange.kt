package com.openhand.khata.feature.insights

/** This month's spending against last month's by the same day, as Home shows it (#130). */
sealed interface MonthChange {
    /** Spent [paise] more than last month, [percent] of last month's spending. */
    data class More(val paise: Long, val percent: Int) : MonthChange

    /** Spent [paise] less than last month, [percent] of last month's spending. */
    data class Less(val paise: Long, val percent: Int) : MonthChange

    data object Same : MonthChange

    companion object {
        /**
         * Null when nothing was spent by this day last month: a percentage of nothing means
         * nothing.
         */
        fun of(spentPaise: Long, lastMonthPaise: Long): MonthChange? {
            if (lastMonthPaise <= 0) return null
            val difference = spentPaise - lastMonthPaise
            return when {
                difference > 0 -> More(difference, shareOf(difference, lastMonthPaise))
                difference < 0 -> Less(-difference, shareOf(-difference, lastMonthPaise))
                else -> Same
            }
        }
    }
}
