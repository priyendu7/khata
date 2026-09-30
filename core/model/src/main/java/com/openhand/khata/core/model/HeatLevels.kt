package com.openhand.khata.core.model

/**
 * Heatmap color levels based on the user's own spending: 0 for a day with nothing spent (or more
 * refunded than spent), then 1 to [MAX] by where the day ranks among the days with spending. The
 * top quarter of those days is [MAX], so one very large day doesn't wash the rest out.
 */
class HeatLevels private constructor(private val sorted: LongArray) {
    fun level(paise: Long): Int {
        if (paise <= 0 || sorted.isEmpty()) return 0
        // How many spending days were at most this much; ceil(MAX * count / days).
        val atMost = upperBound(paise)
        return ((MAX * atMost + sorted.size - 1) / sorted.size).coerceIn(1, MAX)
    }

    private fun upperBound(paise: Long): Int {
        var low = 0
        var high = sorted.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (sorted[mid] <= paise) low = mid + 1 else high = mid
        }
        return low
    }

    companion object {
        /** The darkest level; with the empty level 0 there are five colors. */
        const val MAX = 4

        fun of(dailySpending: Collection<Long>): HeatLevels =
            HeatLevels(dailySpending.filter { it > 0 }.sorted().toLongArray())
    }
}
