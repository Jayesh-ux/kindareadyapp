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
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.launch
import androidx.work.*
import java.util.concurrent.TimeUnit
import com.bluemix.clients_lead.domain.model.TrackingUIState
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

    // ✅ NEW: Persistent duty state (survives navigation/restart)
    private val PREFS_NAME = "duty_state_prefs"
    private val PREF_IS_ON_DUTY = "is_on_duty"
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ✅ FIXED: Class-level scope so it can be cancelled in cleanup() to prevent leaks
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val locationSettingsMonitor = LocationSettingsMonitor(appContext)
    
    private val _trackingState = MutableStateFlow(false)
    val trackingState: StateFlow<Boolean> = _trackingState.asStateFlow()

    private val _gpsState = MutableStateFlow(true)
    val gpsState: StateFlow<Boolean> = _gpsState.asStateFlow()

    private val _permissionState = MutableStateFlow(true)
    val permissionState: StateFlow<Boolean> = _permissionState.asStateFlow()

    // ✅ NEW: Expose isOnDuty state persistently
    private val _isOnDuty = MutableStateFlow(prefs.getBoolean(PREF_IS_ON_DUTY, false))
    val isOnDuty: StateFlow<Boolean> = _isOnDuty.asStateFlow()

    // ✅ NEW: Backend-synced tracking UI state
    private val _trackingUIState = MutableStateFlow<TrackingUIState?>(null)
    val trackingUIState: StateFlow<TrackingUIState?> = _trackingUIState.asStateFlow()

    init {
        // ✅ Load persisted duty state
        _isOnDuty.value = prefs.getBoolean(PREF_IS_ON_DUTY, false)
        Timber.d("DUTY: Loaded persisted state isOnDuty=${_isOnDuty.value}")
        
        updateTrackingState()
        startLocationSettingsMonitoring()
        updatePermissionState()
        scheduleHealthCheck()
    }

    // ✅ NEW: Save duty state persistently
    fun setOnDuty(onDuty: Boolean) {
        _isOnDuty.value = onDuty
        prefs.edit().putBoolean(PREF_IS_ON_DUTY, onDuty).apply()
        Timber.d("DUTY: Saved isOnDuty=$onDuty to SharedPreferences")
    }

    // ✅ Already exists at line 228: updateTrackingUIState
    // This method is called from MapViewModel to sync backend state
    fun updateTrackingStateFromBackend(state: TrackingUIState) {
        _trackingUIState.value = state
        Timber.tag(TAG).d("SYNC: Updated from backend: state=${state.state}")
    }
    
    // ✅ NEW: Fetch tracking state from API (suspend)
    suspend fun fetchTrackingStateFromApi(): com.bluemix.clients_lead.data.repository.TrackingStateResponse {
        val client = HttpClient()
        try {
            return client.get(com.bluemix.clients_lead.core.network.ApiEndpoints.Location.TRACKING_STATE)
                .body<com.bluemix.clients_lead.data.repository.TrackingStateResponse>()
        } finally {
            client.close()
        }
    }

    private fun scheduleHealthCheck() {
        val workRequest = PeriodicWorkRequestBuilder<LocationHealthWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.NONE) // Rule 3: Do NOT spam restart, system limit 15 min
            .addTag("TrackingHealthCheck")
            .build()

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            "LocationTrackingHealthCheck",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
        Timber.tag(TAG).d("Scheduled periodic health check every 15 minutes")
    }

    private fun startLocationSettingsMonitoring() {
        locationSettingsMonitor.startMonitoring()

        scope.launch {
            locationSettingsMonitor.isLocationEnabled.collect { enabled ->
                Timber.tag(TAG).d("GPS state changed: $enabled")
                _gpsState.value = enabled

                // If location was disabled and service is running, stop it
                if (!enabled && isServiceRunning()) {
                    Timber.tag(TAG).w("⚠️ GPS disabled by user, stopping tracking service")
                    stopTracking()
                }

                updateTrackingState()
            }
        }
    }

    fun updatePermissionState() {
        val granted = hasLocationPermissions()
        _permissionState.value = granted
        if (!granted && isServiceRunning()) {
            Timber.tag(TAG).w("⚠️ Permissions revoked, stopping tracking service")
            scope.launch { stopTracking() }
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
            // Rule 8: Prompt for battery optimization (only once)
            BatteryOptimizationHandler.promptToDisableOptimization(appContext)

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
            // ✅ FIX: Clear active client before stopping tracking
            trackingManager.updateActiveClient(null, null, null, null, null)
            
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

    // ✅ NEW: Update UI state from backend response
    fun updateTrackingUIState(state: TrackingUIState) {
        _trackingUIState.value = state
        Timber.tag(TAG).d("TrackingUIState updated: ${state.state}, validated=${state.lastValidated}, idle=${state.idle}")
    }

    // ✅ NEW: Get current tracking UI state
    fun getTrackingUIState(): TrackingUIState? = _trackingUIState.value

    private fun hasLocationPermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        // ✅ FIXED: Also check background location for Android 10+
        val backgroundLocation = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Background permission not required before Android 10
        }
        
        // For tracking to work properly, foreground is minimum required
        // Background location improves tracking when app is in background
        val hasForeground = fine || coarse
        val hasBackground = backgroundLocation
        
        Timber.tag(TAG).d("Permission check: foreground=$hasForeground, background=$hasBackground")
        
        // Return true if foreground is granted (background is optional but recommended)
        return hasForeground
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
        private const val TAG = "TrackingSystem"
    }
}
