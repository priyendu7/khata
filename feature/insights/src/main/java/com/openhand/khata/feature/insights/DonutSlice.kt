package com.openhand.khata.feature.insights

import androidx.compose.ui.graphics.Color

/** One slice of a [CategoryDonut]: its [paise], its color, and what TalkBack reads for it. */
data class DonutSlice(val paise: Long, val color: Color, val description: String)
