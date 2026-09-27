package com.openhand.khata.feature.csv

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.openhand.khata.core.ui.R as UiR

/** Posts the backup reminder; tapping it opens Khata on the Export screen. */
class BackupReminderNotifier(private val context: Context) {
    // canNotify checks the permission; lint can't see that through the helper.
    @SuppressLint("MissingPermission")
    fun show(days: Int) {
        if (!canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.reminder_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.putExtra(EXTRA_OPEN, OPEN_EXPORT)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val tap = open?.let {
            PendingIntent.getActivity(
                context,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        val text = context.resources.getQuantityString(R.plurals.reminder_text, days, days)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(UiR.drawable.ic_ledger)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            // Shown in full on the lock screen: it holds nothing private.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        /** Intent extra that MainActivity reads to open a screen. */
        const val EXTRA_OPEN = "com.openhand.khata.OPEN"
        const val OPEN_EXPORT = "export"

        private const val CHANNEL = "backup_reminder"
        private const val NOTIFICATION_ID = 1

        /** Android 13+ asks for permission; the user can also turn notifications off. */
        fun canNotify(context: Context): Boolean {
            val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
}
