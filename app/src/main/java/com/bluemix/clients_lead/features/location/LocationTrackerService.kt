package com.bluemix.clients_lead.features.location


import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.app.ActivityManager
import com.bluemix.clients_lead.core.network.ApiEndpoints
import android.os.Binder
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import com.bluemix.clients_lead.features.location.BatteryUtils
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.bluemix.clients_lead.R
import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.repository.ILocationRepository
import com.bluemix.clients_lead.domain.usecases.InsertLocationLog
import com.bluemix.clients_lead.core.network.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * Foreground service for continuous location tracking with periodic database saves.
 *
 * Features:
 * - Real-time location updates via SharedFlow
 * - Configurable periodic database saves (default: 5 minutes)
 * - Proper lifecycle management and memory leak prevention
 * - Authentication checks before starting
 */
class LocationTrackerService : Service() {

    // Lifecycle-aware coroutine management
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.IO)

    private val httpClient: HttpClient by inject()
    private val sessionManager: SessionManager by inject()
    private val locationRepository: ILocationRepository by inject()
    private val insertLocationLogUseCase: InsertLocationLog by inject()

    // SharedFlow for broadcasting location updates to UI
    private val _locationFlow = MutableSharedFlow<Location>(replay = 1)
    val locationFlow: SharedFlow<Location> = _locationFlow

    // Tracking state
    private var locationTrackingJob: Job? = null
    private var periodicSaveJob: Job? = null
    private var latestLocation: Location? = null
    private var lastSavedLocation: Location? = null // ✅ TRACK LAST SAVED
    private var lastSavedTime = System.currentTimeMillis()
    private var activeClientId: String? = null
    private var activeClientName: String? = null
    private var transportMode: String? = null
    private var activeClientLat: Double? = null 
    private var activeClientLng: Double? = null

    // S11: Idle detection state
    private var lastSignificantMoveTime = System.currentTimeMillis()
    private var isCurrentlyIdle = false
    // FIXED: Match backend thresholds exactly (5 min idle, 50m movement)
    private val IDLE_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes - matches backend
    private val IDLE_DISTANCE_THRESHOLD = 50f // 50 meters - matches backend

    // Activity log throttling - only log if significant movement or time passed
    private var lastLoggedLocation: Location? = null
    private var lastLogTime = 0L
    // FIXED: Match backend MIN_DISTANCE_METERS = 50
    private val LOG_DISTANCE_THRESHOLD_METERS = 50f // 50 meters - matches backend validation
    private val LOG_TIME_THRESHOLD_MS = 10 * 1000L // 10 seconds - matches backend MIN_TIME_DIFF_SECONDS

    // S5: SharedPreferences key for persisting state across restarts
    private val PREFS_NAME = "tracker_service_state"
    private val PREF_CLIENT_ID = "active_client_id"
    private val PREF_CLIENT_NAME = "active_client_name"
    private val PREF_TRANSPORT_MODE = "transport_mode"
    private val PREF_CLIENT_LAT = "client_lat"
    private val PREF_CLIENT_LNG = "client_lng"

    private fun persistState() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putString(PREF_CLIENT_ID, activeClientId)
            putString(PREF_CLIENT_NAME, activeClientName)
            putString(PREF_TRANSPORT_MODE, transportMode)
            if (activeClientLat != null) putFloat(PREF_CLIENT_LAT, activeClientLat!!.toFloat()) else remove(PREF_CLIENT_LAT)
            if (activeClientLng != null) putFloat(PREF_CLIENT_LNG, activeClientLng!!.toFloat()) else remove(PREF_CLIENT_LNG)
            apply()
        }
    }

    private fun restoreState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        activeClientId = prefs.getString(PREF_CLIENT_ID, null)
        activeClientName = prefs.getString(PREF_CLIENT_NAME, null)
        transportMode = prefs.getString(PREF_TRANSPORT_MODE, null)
        activeClientLat = if (prefs.contains(PREF_CLIENT_LAT)) prefs.getFloat(PREF_CLIENT_LAT, 0f).toDouble() else null
        activeClientLng = if (prefs.contains(PREF_CLIENT_LNG)) prefs.getFloat(PREF_CLIENT_LNG, 0f).toDouble() else null
        Timber.d("🔄 Restored state: client=$activeClientName ($activeClientId), mode=$transportMode")
    }

    private fun clearPersistedState() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    // Configuration - FIXED to match backend exactly
    private val MIN_DISTANCE_METERS = 50f // 50 meters - matches backend MIN_DISTANCE_METERS
    private val saveInterval = 10 * 60 * 1000L // 10 mins fallback

    // Notification components
    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationBuilder: NotificationCompat.Builder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // CRITICAL: Call startForeground() IMMEDIATELY as the very first operation
        // This prevents ForegroundServiceDidNotStartInTimeException on Android 12+ (API 31+)
        try {
            notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationBuilder = NotificationCompat.Builder(this, LOCATION_CHANNEL)
                .setSmallIcon(com.bluemix.clients_lead.R.mipmap.ic_launcher)
                .setContentTitle("Location Tracker")
                .setContentText("Starting location tracking...")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID, 
                    notificationBuilder.build(), 
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notificationBuilder.build())
            }
        } catch (e: Exception) {
            android.util.Log.e("LocationTrackerService", "Failed to start foreground immediately", e)
        }

        return try {
            // OS-Level Reliability: If system restarts service after crash/kill, intent is null
            if (intent == null || intent.action == null) {
                Timber.tag(TAG).d("🔄 Service restarted by OS (null intent). Validating conditions...")
                val userId = sessionManager.getCurrentUserId()
                if (userId != null && hasConditionsMet()) {
                    Timber.tag(TAG).d("✅ Session and conditions valid. Resuming tracking...")
                    restoreState() // Restore active client if any
                    start(userId)
                } else {
                    Timber.tag(TAG).w("🛑 Conditions not met on restart (User: ${sessionManager.getCurrentUserId() != null}). Stopping...")
                    stop()
                }
            } else {
                // Handle explicit actions
                when (intent.action) {
                    Action.START.name -> {
                        activeClientId = intent.getStringExtra(EXTRA_CLIENT_ID)
                        val userId = sessionManager.getCurrentUserId()
                        if (userId != null && hasConditionsMet()) {
                            start(userId)
                        } else {
                            Timber.tag(TAG).e("Cannot start tracking: Session or conditions failed")
                            stop()
                        }
                    }
                    Action.UPDATE_CLIENT.name -> {
                        activeClientId = intent.getStringExtra(EXTRA_CLIENT_ID)
                        activeClientName = intent.getStringExtra(EXTRA_CLIENT_NAME)
                        transportMode = intent.getStringExtra(EXTRA_TRANSPORT_MODE)
                        activeClientLat = if (intent.hasExtra(EXTRA_CLIENT_LAT)) intent.getDoubleExtra(EXTRA_CLIENT_LAT, 0.0) else null
                        activeClientLng = if (intent.hasExtra(EXTRA_CLIENT_LNG)) intent.getDoubleExtra(EXTRA_CLIENT_LNG, 0.0) else null
                        persistState() // S5: save to disk
                        Timber.d("📍 Updated active client: $activeClientName ($activeClientId) via $transportMode at ($activeClientLat, $activeClientLng)")
                    }
                    Action.STOP.name -> stop()
                }
            }

            START_STICKY // ✅ Ensure service restarts if killed
        } catch (e: Exception) {
            android.util.Log.e("LocationTrackerService", "Error in onStartCommand", e)
            Timber.e(e, "❌ Error in onStartCommand")
            START_STICKY
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Timber.tag(TAG).w("🚫 App removed from recents - continuing location tracking in background")
        super.onTaskRemoved(rootIntent)
    }


    override fun onBind(intent: Intent?): IBinder {
        return LocationBinder()
    }

    inner class LocationBinder : Binder() {
        fun getService(): LocationTrackerService = this@LocationTrackerService
    }

    private fun start(userId: String) {
        if (locationTrackingJob?.isActive == true) {
            Timber.tag(TAG).w("Location tracking already running")
            return
        }

        Timber.tag(TAG).d("Starting tracking for user: $userId")

        if (activeClientId == null) {
            restoreState()
        }

        if (!hasConditionsMet()) {
            Timber.tag(TAG).e("Cannot start: Conditions not met (Permissions or GPS)")
            stop()
            return
        }

        val locationManager = LocationManager(applicationContext)

        // Start location tracking
        locationTrackingJob = scope.launch {
            try {
                locationManager.trackLocation().collect { trackingLocation: android.location.Location ->
                    if (!hasConditionsMet()) {
                        Timber.tag(TAG).w("🚫 Conditions lost during tracking → stopping service")
                        stop()
                        return@collect
                    }

                    latestLocation = trackingLocation
                    scope.launch {
                        _locationFlow.emit(trackingLocation)
                    }

                    val latitudeStr = String.format(java.util.Locale.US, "%.4f", trackingLocation.latitude)
                    val longitudeStr = String.format(java.util.Locale.US, "%.4f", trackingLocation.longitude)

                    notificationManager.notify(
                        NOTIFICATION_ID,
                        notificationBuilder
                            .setContentText("Location: $latitudeStr / $longitudeStr")
                            .build()
                    )

                    // ✅ INTELLIGENT TRACKING: Save only if significant movement or time elapsed
                    val userId = sessionManager.getCurrentUserId()
                    if (userId != null) {
                        val distanceMoved = lastSavedLocation?.distanceTo(trackingLocation) ?: Float.MAX_VALUE
                        val timeElapsed = System.currentTimeMillis() - lastSavedTime
                        
                        // ✅ MOVEMENT-BASED: Save if moved > 30m OR if it's been > 90 seconds (Real-time Heartbeat)
                        if (distanceMoved >= 30f || timeElapsed >= (90 * 1000L)) {
                            Timber.tag(TAG).d("📍 Distance: ${distanceMoved}m, Time: ${timeElapsed}ms → Saving breadcrumb")
                            lastSavedLocation = trackingLocation
                            lastSavedTime = System.currentTimeMillis()
                            
                            // S11: Idle detection
                            if (distanceMoved >= IDLE_DISTANCE_THRESHOLD) {
                                if (isCurrentlyIdle) {
                                    isCurrentlyIdle = false
                                    val idleDurationMin = ((System.currentTimeMillis() - lastSignificantMoveTime) / 60000).toInt()
                                    scope.launch {
                                        locationRepository.insertLocationLog(
                                            userId = userId,
                                            latitude = trackingLocation.latitude,
                                            longitude = trackingLocation.longitude,
                                            accuracy = trackingLocation.accuracy.toDouble(),
                                            battery = BatteryUtils.getBatteryPercentage(this@LocationTrackerService),
                                            clientId = activeClientId,
                                            markActivity = "IDLE_END",
                                            markNotes = "Agent resumed movement after ${idleDurationMin}min idle"
                                        )
                                    }
                                    Timber.tag(TAG).d("▶️ Agent resumed after ${idleDurationMin}min idle")
                                }
                                lastSignificantMoveTime = System.currentTimeMillis()
                            } else {
                                val timeSinceLastMove = System.currentTimeMillis() - lastSignificantMoveTime
                                if (!isCurrentlyIdle && timeSinceLastMove >= IDLE_THRESHOLD_MS && !activeClientId.isNullOrBlank()) {
                                    isCurrentlyIdle = true
                                    scope.launch {
                                        locationRepository.insertLocationLog(
                                            userId = userId,
                                            latitude = trackingLocation.latitude,
                                            longitude = trackingLocation.longitude,
                                            accuracy = trackingLocation.accuracy.toDouble(),
                                            battery = BatteryUtils.getBatteryPercentage(this@LocationTrackerService),
                                            clientId = activeClientId,
                                            markActivity = "IDLE_START",
                                            markNotes = "Agent stationary for 15+ min during journey to ${activeClientName ?: activeClientId}"
                                        )
                                    }
                                    Timber.tag(TAG).d("⏸️ Agent idle for 15+ min during journey")
                                }
                            }
                            
                            var currentActivity: String? = null
                            if (!activeClientId.isNullOrBlank()) {
                                currentActivity = "TRAVELING"
                                if (activeClientLat != null && activeClientLng != null) {
                                    val clientLoc = android.location.Location("").apply {
                                        latitude = activeClientLat!!
                                        longitude = activeClientLng!!
                                    }
                                    val distanceToClient = trackingLocation.distanceTo(clientLoc)
                                    if (distanceToClient <= 200f) {
                                        currentActivity = "AT_CLIENT_SITE"
                                    }
                                    Timber.tag(TAG).d("📏 Distance to client $activeClientId: ${distanceToClient}m. Activity: $currentActivity")
                                }
                            } else {
                                // ✅ FIX: Don't log ON_DUTY on every breadcrumb
                                // CLOCK_IN already marks the session start, CLOCK_OUT marks the end
                                // Only log when user has an active journey, otherwise skip
                                currentActivity = null // No activity to log when no journey
                            }
                            
                            // ✅ FIX: Only log when there's an actual activity
                            // Skip logging when currentActivity is null (no active journey)
                            val breadcrumbNote = when (currentActivity) {
                                "TRAVELING" -> "Heading to ${activeClientName ?: activeClientId} via ${transportMode ?: "Car"}"
                                "AT_CLIENT_SITE" -> "At ${activeClientName ?: activeClientId} site"
                                else -> null
                            }

                            val currentTime = System.currentTimeMillis()
                            val distanceFromLastLog = lastLoggedLocation?.let { trackingLocation.distanceTo(it) } ?: Float.MAX_VALUE
                            val timeSinceLastLog = currentTime - lastLogTime
                            
                            // ✅ FIX: Only log if there's an actual activity (not null)
                            val shouldLog = currentActivity != null && (
                                lastLoggedLocation == null || 
                                distanceFromLastLog > LOG_DISTANCE_THRESHOLD_METERS || 
                                timeSinceLastLog > LOG_TIME_THRESHOLD_MS
                            )
                            
                            if (shouldLog) {
                                scope.launch {
                                    val result = locationRepository.insertLocationLog(
                                        userId = userId,
                                        latitude = trackingLocation.latitude,
                                        longitude = trackingLocation.longitude,
                                        accuracy = trackingLocation.accuracy.toDouble(),
                                        battery = com.bluemix.clients_lead.features.location.BatteryUtils.getBatteryPercentage(this@LocationTrackerService),
                                        clientId = activeClientId,
                                        markActivity = currentActivity,
                                        markNotes = breadcrumbNote
                                    )
                                    when (result) {
                                        is AppResult.Success -> {
                                            Timber.tag(TAG).d("✅ Saved breadcrumb point: ${result.data.id}")
                                            lastLoggedLocation = trackingLocation
                                            lastLogTime = currentTime
                                        }
                                        is AppResult.Error -> Timber.tag(TAG).e("❌ Failed to save breadcrumb: ${result.error.message}")
                                    }
                                }
                            } else {
                                Timber.tag(TAG).d("⏭️ Skipping log: only ${distanceFromLastLog.toInt()}m moved, ${timeSinceLastLog/60000}min elapsed")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Tracking error")
                stop()
            }
        }

        // Start periodic database save
        startPeriodicDatabaseSave(userId)
    }

    private fun startPeriodicDatabaseSave(userId: String) {
        // Periodic saves are now replaced by movement-based saves in start()
        Timber.d("Movement-based saves are active for $userId.")
    }

    private fun hasConditionsMet(): Boolean {
        val lm = LocationManager(applicationContext)
        return lm.hasLocationPermission() && lm.isLocationEnabled()
    }

    suspend fun clearUserPincode() {
        try {
            Timber.tag(TAG).d("Pincode clear not implemented")
        } catch (e: Exception) {
            Timber.tag(TAG).e("Failed to clear pincode: ${e.message}")
        }
    }

    private suspend fun saveLocationToDatabase(userId: String, location: Location) {
        val battery = BatteryUtils.getBatteryPercentage(this)
        try {
            when (val result = insertLocationLogUseCase(
                userId = userId,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy.toDouble(),
                battery = battery,
                clientId = activeClientId // ✅ PASS SCOPED CLIENT
            )) {
                is AppResult.Success -> {
                    Timber.d("Location saved: ${result.data.id} at ${result.data.timestamp} | Battery: $battery%")
                }
                is AppResult.Error -> {
                    Timber.e(result.error.cause, "Failed to save location: ${result.error.message}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception while saving location")
        }
    }

    private fun stop() {
        Timber.d("Stopping location tracking service")

        scope.launch {
            clearUserPincode()
        }

        // Cancel tracking jobs
        locationTrackingJob?.cancel()
        periodicSaveJob?.cancel()

        // ✅ FIX: Clear all state including active journey
        activeClientId = null
        activeClientName = null
        transportMode = null
        activeClientLat = null
        activeClientLng = null
        
        // Clear references and persisted state
        latestLocation = null
        clearPersistedState() // S5: clean up on explicit stop

        // Stop foreground and service
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        Timber.d("Service created")
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        locationTrackingJob?.cancel()
        periodicSaveJob?.cancel()
        serviceJob.cancelChildren()
        serviceJob.cancel()
        scope.cancel()
        Timber.tag(TAG).d("Service destroyed")
    }

    enum class Action {
        START, STOP, UPDATE_CLIENT
    }

    companion object {
        private const val TAG = "TrackingSystem"
        var isServiceRunning = false
            private set
        const val LOCATION_CHANNEL = "location_channel"
        private const val NOTIFICATION_ID = 1
        const val EXTRA_CLIENT_ID = "extra_client_id"
        const val EXTRA_CLIENT_NAME = "extra_client_name"
        const val EXTRA_TRANSPORT_MODE = "extra_transport_mode"
        const val EXTRA_CLIENT_LAT = "extra_client_lat"
        const val EXTRA_CLIENT_LNG = "extra_client_lng"
    }
}

fun isTrackingServiceRunning(context: Context): Boolean {
    return LocationTrackerService.isServiceRunning
}