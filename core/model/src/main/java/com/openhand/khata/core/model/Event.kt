package com.openhand.khata.core.model

import java.time.LocalDate

/**
 * A named date range, such as a trip (#73). Every transaction from [start] to [end] (inclusive,
 * local days) is tagged with [name], so the whole trip can be found and totalled through the tag.
 */
data class Event(val id: Long = 0, val name: String, val start: LocalDate, val end: LocalDate)
