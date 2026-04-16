package com.bluemix.clients_lead.data.worker

import android.content.Context
import androidx.work.*
import com.bluemix.clients_lead.core.network.ApiEndpoints
import com.bluemix.clients_lead.data.local.AppDatabase
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

class LocationLogSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val database = AppDatabase.getInstance(applicationContext)
            val dao = database.pendingLocationLogDao()
            
            // Get unsynced logs
            val unsyncedLogs = dao.getUnsyncedLogs()
            
            if (unsyncedLogs.isEmpty()) {
                Timber.d("✅ No unsynced logs to upload")
                return@withContext Result.success()
            }
            
            Timber.d("📤 Syncing ${unsyncedLogs.size} location logs...")
            
            // Sync each log to backend
            var successCount = 0
            for (log in unsyncedLogs) {
                try {
                    // Upload to backend
                    val httpClient = io.ktor.client.HttpClient()
                    httpClient.post(ApiEndpoints.Location.LOGS) {
                        setBody(CreateLocationLogRequest(
                            latitude = log.latitude,
                            longitude = log.longitude,
                            accuracy = log.accuracy,
                            battery = log.battery,
                            markActivity = log.markActivity,
                            markNotes = log.markNotes,
                            timestamp = log.timestamp
                        ))
                    }
                    
                    // Mark as synced on success
                    dao.markAsSynced(log.id)
                    successCount++
                    
                } catch (e: Exception) {
                    Timber.e(e, "❌ Failed to sync log ${log.id}")
                    // Continue with next log
                }
            }
            
            // Clean up old synced logs (older than 7 days)
            val sevenDaysAgo = java.time.Instant.now()
                .minus(7, java.time.temporal.ChronoUnit.DAYS)
                .toString()
            dao.deleteSyncedOlderThan(sevenDaysAgo)
            
            Timber.d("✅ Synced $successCount/${unsyncedLogs.size} logs")
            
            if (successCount > 0) {
                Result.success()
            } else {
                Result.retry()
            }
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Sync worker failed")
            Result.retry()
        }
    }
    
    companion object {
        const val WORK_NAME = "location_log_sync"
        
        /**
         * Schedule periodic sync every 15 minutes
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val workRequest = PeriodicWorkRequestBuilder<LocationLogSyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1, TimeUnit.MINUTES
                )
                .build()
            
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
            
            Timber.d("📅 Location sync worker scheduled")
        }
        
        /**
         * Run immediate sync
         */
        fun runNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val workRequest = OneTimeWorkRequestBuilder<LocationLogSyncWorker>()
                .setConstraints(constraints)
                .build()
            
            WorkManager.getInstance(context)
                .enqueue(workRequest)
            
            Timber.d("🚀 Immediate sync triggered")
        }
    }
}

private data class CreateLocationLogRequest(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double? = null,
    val battery: Int? = null,
    val markActivity: String? = null,
    val markNotes: String? = null,
    val timestamp: String? = null
)