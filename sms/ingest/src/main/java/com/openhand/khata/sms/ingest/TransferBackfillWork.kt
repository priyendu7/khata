package com.openhand.khata.sms.ingest

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.openhand.khata.core.data.TransferBackfill
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * Starts [TransferBackfill] in the background on app start; it needs the SMS rules, which live
 * here. After its first run it finds it has nothing to do.
 */
class TransferBackfillWork @Inject constructor(@ApplicationContext private val context: Context) {
    fun start() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<TransferBackfillWorker>().build()
        )
    }

    private companion object {
        const val WORK_NAME = "transfer_backfill"
    }
}

class TransferBackfillWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    /** Hilt can't construct workers without a custom factory, so the worker asks for these. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun backfill(): TransferBackfill

        fun ingestor(): SmsIngestor
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        deps.backfill().run(deps.ingestor()::reread)
        return Result.success()
    }
}
