package com.bluemix.clients_lead.features.location

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.app.NotificationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import kotlinx.coroutines.launch
/**
 * Centralized manager for location tracking state.
 *
 * This version keeps the original implementation completely intact AND now
 * integrates the smaller LocationTrackingManager so that toggles in Map and
 * toggles in Settings are always synchronized.
 */
class LocationTrackingStateManager(
    private val appContext: Context,
    private val trackingManager: LocationTrackingManager   // <-- injected small manager
) {

    // ✅ FIXED: Class-level scope so it can be cancelled in cleanup() to prevent leaks
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val locationSettingsMonitor = LocationSettingsMonitor(appContext)
    private val _trackingState = MutableStateFlow(false)
    val trackingState: StateFlow<Boolean> = _trackingState.asStateFlow()

    init {
        updateTrackingState()
        startLocationSettingsMonitoring()  // 👈 Add this
    }

    private fun startLocationSettingsMonitoring() {
        locationSettingsMonitor.startMonitoring()

        // ✅ FIXED: Use class-level scope instead of anonymous CoroutineScope that leaked
        scope.launch {
            locationSettingsMonitor.isLocationEnabled.collect { enabled ->
                Timber.tag(TAG).d("Location enabled state changed: $enabled")

                // If location was disabled and service is running, stop it
                if (!enabled && isServiceRunning()) {
                    Timber.tag(TAG).w("⚠️ Location disabled by user, stopping tracking service")
                    stopTracking()
                }

                // Update tracking state
                updateTrackingState()
            }
        }
    }

    // ✅ FIXED: Now also cancels the class-level scope to stop the settings monitor coroutine
    fun cleanup() {
        locationSettingsMonitor.stopMonitoring()
        scope.cancel()
    }







    fun isCurrentlyTracking(): Boolean = _trackingState.value

    fun isLocationEnabled(): Boolean {
        return locationSettingsMonitor.isLocationEnabled.value
    }


    suspend fun startTracking() {
        Timber.tag(TAG).d("Request received to START location tracking")

        if (!hasLocationPermissions()) {
            Timber.tag(TAG).e("❌ Cannot start tracking - Location permissions not granted!")
            _trackingState.value = false
            return
        }
        val locationManager = LocationManager(appContext)
        if (!locationManager.isLocationEnabled()) {
            Timber.tag(TAG).e("❌ Cannot start tracking - Location services disabled!")
            _trackingState.value = false
            return
        }

        if (isServiceRunning()) {
            Timber.tag(TAG).w("⚠️ Service already running, skipping start")
            _trackingState.value = true
            return
        }

        try {
            trackingManager.startTracking()
            Timber.tag(TAG).d("➡️ Forwarded START to LocationTrackingManager")

            // ✅ Give service time to actually start
            kotlinx.coroutines.delay(150)

            updateTrackingState()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Failed forwarding start to small manager")
            _trackingState.value = false
        }
    }

    suspend fun stopTracking() {
        Timber.tag(TAG).d("Request received to STOP location tracking")

        if (!isServiceRunning()) {
            Timber.tag(TAG).w("⚠️ Service not running, skipping stop")
            _trackingState.value = false
            return
        }

        try {
            trackingManager.stopTracking()
            Timber.tag(TAG).d("➡️ Forwarded STOP to LocationTrackingManager")

            // ✅ Give service time to actually stop
            kotlinx.coroutines.delay(150)

            // ✅ NOW verify the service actually stopped
            updateTrackingState()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Failed forwarding stop to small manager")
            _trackingState.value = false
        }
    }

    fun updateActiveClient(
        clientId: String?,
        clientName: String? = null,
        transportMode: String? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        trackingManager.updateActiveClient(clientId, clientName, transportMode, latitude, longitude)
    }

    fun updateTrackingState() {
        val running = isServiceRunning()
        Timber.tag(TAG).d("Tracking state refreshed from system. isRunning = $running")
        _trackingState.value = running
    }

    private fun hasLocationPermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean {
        return try {
            val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return false

            // Strategy 1: Traditional check
            val isRunning = activityManager.getRunningServices(Integer.MAX_VALUE).any {
                it.service.className == LocationTrackerService::class.java.name
            }

            if (isRunning) {
                Timber.tag(TAG).d("Service running check (Strategy 1) = true")
                return true
            }

            // Strategy 2: Notification check (Reliable for foreground services on Android 12+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return false
                val notifications = notificationManager.activeNotifications
                val notificationRunning = notifications.any {
                    it.id == 1 || it.notification.channelId == "location_channel"
                }
                Timber.tag(TAG).d("Service running check (Strategy 2) = $notificationRunning")
                return notificationRunning
            }

            Timber.tag(TAG).d("Service running check = false")
            false
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error checking if service is running")
            false
        }
    }

    companion object {
        private const val TAG = "LocationTrackingStateMgr"
    }
}
