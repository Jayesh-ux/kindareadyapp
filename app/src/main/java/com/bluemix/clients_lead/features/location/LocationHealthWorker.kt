package com.bluemix.clients_lead.features.location

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bluemix.clients_lead.core.network.SessionManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * Periodically checks the health of the location tracking system.
 * Restarts the foreground service if it should be running but isn't.
 */
class LocationHealthWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val trackingStateManager: LocationTrackingStateManager by inject()
    private val sessionManager: SessionManager by inject()
    private val locationManager = LocationManager(context)

    override suspend fun doWork(): Result {
        val tag = "TrackingSystem"
        
        try {
            val userId = sessionManager.getCurrentUserId()
            if (userId == null) {
                Timber.tag(tag).d("Worker: No active session. Skipping health check.")
                return Result.success()
            }

            val hasPermission = locationManager.hasLocationPermission()
            val isGpsEnabled = locationManager.isLocationEnabled()
            val isRunning = trackingStateManager.isCurrentlyTracking()

            if (!isRunning && hasPermission && isGpsEnabled) {
                Timber.tag(tag).w("Worker: Tracking should be running but isn't. Attempting restart...")
                trackingStateManager.startTracking()
            } else {
                Timber.tag(tag).d("Worker: Health check passed. Running: $isRunning, Permissions: $hasPermission, GPS: $isGpsEnabled")
            }

            return Result.success()
        } catch (e: Exception) {
            Timber.tag(tag).e(e, "Worker: Error during health check")
            return Result.retry()
        }
    }
}
