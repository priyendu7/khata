package com.openhand.khata.sms.ingest

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives every new SMS while SMS import is on, and hands the parts to [NewSmsHandler]. The work
 * is done here with [goAsync], not in a WorkManager job: WorkManager keeps job data in its own
 * unencrypted database, and bank SMS must stay in the encrypted one. Saving one SMS takes
 * milliseconds, well inside a receiver's time limit.
 */
class SmsReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun handler(): NewSmsHandler
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        // Works the same for either SIM: the system has already picked out the messages.
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(
            intent
        ).orEmpty().mapNotNull { sms ->
            val sender = sms?.displayOriginatingAddress ?: return@mapNotNull null
            SmsPart(sender, sms.displayMessageBody.orEmpty(), sms.timestampMillis)
        }
        if (parts.isEmpty()) return
        val handler = EntryPointAccessors
            .fromApplication(context.applicationContext, Dependencies::class.java)
            .handler()
        val pending = goAsync()
        scope.launch {
            try {
                handler.handle(parts)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Turns the receiver on or off with SMS import, so it isn't even started while off. */
        fun setEnabled(context: Context, enabled: Boolean) {
            val state = if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, SmsReceiver::class.java),
                state,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
