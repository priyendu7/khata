package com.openhand.khata.feature.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

/** What SMS import asks for: reading bank SMS in the inbox, and new ones as they arrive. */
internal val SMS_PERMISSIONS =
    arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

internal fun Context.hasSmsPermission() = SMS_PERMISSIONS.all {
    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
}

internal fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
