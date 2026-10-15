package com.openhand.khata.feature.settings

/** The form without the guesses it starts with, for tests that mark every field by hand. */
internal fun MakerForm.unguessed(): MakerForm =
    copy(marks = emptyList(), guessed = emptySet(), dateFormat = null)
