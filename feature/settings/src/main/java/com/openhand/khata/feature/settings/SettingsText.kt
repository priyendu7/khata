package com.openhand.khata.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** "3 accounts": [plural] filled in with [n]. */
@Composable
internal fun count(plural: Int, n: Int) = pluralStringResource(plural, n, n)

/** Two parts of a row's state, "On · last scan 2 Oct 2026". */
@Composable
internal fun pair(first: String, second: String) =
    stringResource(R.string.settings_pair, first, second)

@Composable
internal fun date(day: LocalDate): String =
    day.format(DateTimeFormatter.ofPattern(DATE_PATTERN, LocalConfiguration.current.locales[0]))

private const val DATE_PATTERN = "d MMM yyyy"
