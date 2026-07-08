package com.example.sfa

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

// ═══════════════════════════════════════════════════════════════════════════════
// SyncWorker — runs in the background whenever the device regains connectivity.
// Flushes all pending items from the sync_queue to the server.
// ═══════════════════════════════════════════════════════════════════════════════

class SyncWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
        val repo = OfflineRepository(applicationContext)

        if (!isOnline(applicationContext)) {
            Log.d("SFA", "SyncWorker: still offline, rescheduling")
            return@withContext Result.retry()
        }

        return@withContext try {
            val flushed = repo.flushSyncQueue(baseUrl)
            Log.d("SFA", "SyncWorker: flushed $flushed items")
            Result.success()
        } catch (e: Exception) {
            Log.e("SFA", "SyncWorker error", e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "sfa_sync"

        /**
         * Schedule a one-time sync that runs as soon as the device has internet.
         * Safe to call multiple times — WorkManager deduplicates by WORK_NAME.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,  // don't replace an already-queued sync
                request
            )
            Log.d("SFA", "SyncWorker scheduled")
        }

        /**
         * Schedule a periodic background sync (every 15 minutes when online).
         * Call once from Application or MainActivity onCreate.
         */
        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "${WORK_NAME}_periodic",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
