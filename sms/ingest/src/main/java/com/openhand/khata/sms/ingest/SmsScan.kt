package com.openhand.khata.sms.ingest

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Starts, follows and cancels the inbox import, which runs in the background. */
class SmsScan @Inject constructor(@ApplicationContext private val context: Context) {
    private val workManager get() = WorkManager.getInstance(context)

    /** Imports bank SMS received since [since] (epoch millis). Does nothing if one is running. */
    fun start(since: Long) {
        val request = OneTimeWorkRequestBuilder<SmsScanWorker>()
            .setInputData(workDataOf(SmsScanWorker.SINCE to since))
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    /** (done, total) while an import runs; null when none is. */
    val progress: Flow<ScanProgress?> =
        workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME).map { infos ->
            infos.firstOrNull { !it.state.isFinished }?.let { info ->
                ScanProgress(
                    done = info.progress.getInt(SmsScanWorker.DONE, 0),
                    total = info.progress.getInt(SmsScanWorker.TOTAL, 0),
                    started = info.state == WorkInfo.State.RUNNING
                )
            }
        }

    private companion object {
        const val WORK_NAME = "sms_inbox_import"
    }
}

data class ScanProgress(val done: Int, val total: Int, val started: Boolean)

/** Runs [InboxScanner] and saves its summary. Does nothing without the SMS permission. */
class SmsScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    /** Hilt can't construct workers without a custom factory, so the worker asks for these. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun scanner(): InboxScanner

        fun settings(): SmsImportSettings
    }

    override suspend fun doWork(): Result {
        val allowed =
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED
        if (!allowed) return Result.failure()
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        val summary = deps.scanner().scan(inputData.getLong(SINCE, 0)) { done, total ->
            // Every SMS would be too many updates for a big inbox; the ends always go out.
            if (done == 0 || done == total || done % PROGRESS_EVERY == 0) {
                setProgress(workDataOf(DONE to done, TOTAL to total))
            }
        }
        deps.settings().saveSummary(summary)
        return Result.success()
    }

    internal companion object {
        const val SINCE = "since"
        const val DONE = "done"
        const val TOTAL = "total"
        const val PROGRESS_EVERY = 10
    }
}
