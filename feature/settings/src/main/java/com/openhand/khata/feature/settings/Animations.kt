package com.openhand.khata.feature.settings

import android.content.Context
import android.provider.Settings

/** True when the phone's "Remove animations" setting is on (it sets the animator scale to 0). */
internal fun Context.animationsOff() =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
