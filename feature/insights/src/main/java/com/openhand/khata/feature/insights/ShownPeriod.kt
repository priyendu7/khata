package com.openhand.khata.feature.insights

/** The cards with their own period, each switched and stepped on its own. */
enum class PeriodCard { CATEGORIES, TAGS, ACCOUNTS, TRANSFERS }

/** A card's period as its chips, ‹ › arrows and label show it. */
interface ShownPeriod {
    val period: ChartPeriod
    val span: DateSpan

    /** [span] as the `[from, until)` milliseconds the transactions list filters by. */
    val from: Long
    val until: Long

    /** How many periods before the current one [span] is; 0 for a custom period. */
    val back: Int

    /** What tapping the label offers to jump to, newest first: index `i` is `i` periods back. */
    val choices: List<DateSpan>

    /** Whether › can step forward: it can't go past the current period. */
    val canStepForward: Boolean get() = period != ChartPeriod.CUSTOM && back > 0
}
