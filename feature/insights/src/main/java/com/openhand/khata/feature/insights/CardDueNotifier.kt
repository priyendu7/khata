package com.openhand.khata.feature.insights

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
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.model.StatementSummary
import com.openhand.khata.core.ui.R as UiR
import java.time.format.DateTimeFormatter

/**
 * Posts a card's due date reminder (#156), one notification per card; tapping it opens the card's
 * page. On a lock screen that hides private content, it says only that a bill is due.
 */
class CardDueNotifier(private val context: Context) {
    // canNotify checks the permission; lint can't see that through the helper.
    @SuppressLint("MissingPermission")
    fun show(card: Account, bill: StatementSummary, daysBefore: Int) {
        if (!canNotify(context)) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.card_reminder_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        val left = Money.format((bill.statement.totalPaise - bill.paidPaise).coerceAtLeast(0))
        val text = if (daysBefore == 0) {
            context.getString(R.string.card_reminder_text_today, label(card), left)
        } else {
            val locale = context.resources.configuration.locales[0]
            val due = bill.statement.dueDate.format(DateTimeFormatter.ofPattern("d MMM", locale))
            context.getString(R.string.card_reminder_text, label(card), left, due)
        }
        val notification = builder()
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open(card.id))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                builder()
                    .setContentText(context.getString(R.string.card_reminder_public))
                    .build()
            )
            .build()
        NotificationManagerCompat.from(context).notify(TAG, card.id.toInt(), notification)
    }

    /** Takes down [cardId]'s reminder, once its bill is paid or its reminders are off. */
    fun cancel(cardId: Long) {
        NotificationManagerCompat.from(context).cancel(TAG, cardId.toInt())
    }

    private fun builder() = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(UiR.drawable.ic_credit_card)
        .setContentTitle(context.getString(R.string.card_reminder_title))
        .setAutoCancel(true)

    private fun label(card: Account): String = card.shownLast4()
        ?.let { context.getString(R.string.cards_card_last4, card.name, it) }
        ?: card.name

    private fun open(cardId: Long): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.putExtra(EXTRA_CARD, cardId)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?.let {
                // One request code per card, so each notification keeps its own card.
                PendingIntent.getActivity(
                    context,
                    cardId.toInt(),
                    it,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

    companion object {
        /** Intent extra with the credit card whose page MainActivity opens. */
        const val EXTRA_CARD = "com.openhand.khata.OPEN_CARD"

        private const val CHANNEL = "card_due"
        private const val TAG = "card_due"

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
