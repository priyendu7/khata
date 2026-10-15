package com.openhand.khata.feature.insights

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Plans card due date reminders again after an app update, which the user may not open for a
 * while, or a time zone change, which moves each reminder's morning.
 */
class CardDueRescheduleReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun cardDueScheduler(): CardDueScheduler
    }

    override fun onReceive(context: Context, intent: Intent) {
        val scheduler = EntryPointAccessors
            .fromApplication(context.applicationContext, Dependencies::class.java)
            .cardDueScheduler()
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                scheduler.refresh()
            } finally {
                pending.finish()
            }
        }
    }
}
