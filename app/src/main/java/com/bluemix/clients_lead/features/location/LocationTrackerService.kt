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
    private val IDLE_THRESHOLD_MS = 15 * 60 * 1000L // 15 minutes
    private val IDLE_DISTANCE_THRESHOLD = 50f // meters

    // Activity log throttling - only log if significant movement or time passed
    private var lastLoggedLocation: Location? = null
    private var lastLogTime = 0L
    private val LOG_DISTANCE_THRESHOLD_METERS = 200f // Only log if moved >200m
    private val LOG_TIME_THRESHOLD_MS = 5 * 60 * 1000L // Or 5 minutes passed

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

    // Configuration
    private val MIN_DISTANCE_METERS = 200f // breadcrumb every 200m
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
            // Now handle the intent
            when (intent?.action) {
                Action.START.name -> {
                    activeClientId = intent.getStringExtra(EXTRA_CLIENT_ID)
                    val userId = sessionManager.getCurrentUserId()
                    if (userId != null) {
                        start(userId)
                    } else {
                        Timber.e("Cannot start tracking: User not authenticated")
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

            START_STICKY // ✅ Ensure service restarts if killed
        } catch (e: Exception) {
            android.util.Log.e("LocationTrackerService", "Error in onStartCommand", e)
            Timber.e(e, "❌ Error in onStartCommand")
            START_STICKY
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Timber.w("🚫 App removed from recents - continuing location tracking in background")
        // stop() // ✅ REMOVED: Do not stop service when app is swiped away
        super.onTaskRemoved(rootIntent)
    }


    override fun onBind(intent: Intent?): IBinder {
        return LocationBinder()
    }

    inner class LocationBinder : Binder() {
        fun getService(): LocationTrackerService = this@LocationTrackerService
    }

    private fun start(userId: String) {
        // Prevent duplicate start
        if (locationTrackingJob?.isActive == true) {
            Timber.w("Location tracking already running")
            return
        }

        Timber.d("Starting location tracking for user: $userId")

        // S5: Restore persisted state on restart
        if (activeClientId == null) {
            restoreState()
        }

        val locationManager = LocationManager(applicationContext)
        if (!locationManager.hasLocationPermission()) {
            Timber.e("Cannot start tracking: Location permission not granted")
            stop()
            return
        }
        if (!locationManager.isLocationEnabled()) {
            Timber.e("Cannot start tracking: Location services disabled")
            stop()
            return
        }


        // Start location tracking
        locationTrackingJob = scope.launch {
            try {
                locationManager.trackLocation().collect { location ->

                    if (!locationManager.isLocationEnabled()) {
                        Timber.w("🚫 GPS turned OFF while service running → stopping service")
                        stop()
                        return@collect
                    }

                    latestLocation = location
                    _locationFlow.emit(location)

                    val latitude = String.format(java.util.Locale.US, "%.4f", location.latitude)
                    val longitude = String.format(java.util.Locale.US, "%.4f", location.longitude)

                    notificationManager.notify(
                        NOTIFICATION_ID,
                        notificationBuilder
                            .setContentText("Location: $latitude / $longitude")
                            .build()
                    )

                    // ✅ INTELLIGENT TRACKING: Save only if significant movement or time elapsed
                    val userId = sessionManager.getCurrentUserId()
                    if (userId != null) {
                        val distanceMoved = lastSavedLocation?.distanceTo(location) ?: Float.MAX_VALUE
                        val timeElapsed = System.currentTimeMillis() - lastSavedTime
                        
                        // ✅ MOVEMENT-BASED: Save if moved > 30m OR if it's been > 90 seconds (Real-time Heartbeat)
                        if (distanceMoved >= 30f || timeElapsed >= (90 * 1000L)) {
                            Timber.d("📍 Distance: ${distanceMoved}m, Time: ${timeElapsed}ms → Saving breadcrumb")
                            lastSavedLocation = location
                            lastSavedTime = System.currentTimeMillis()
                            
                            // S11: Idle detection
                            if (distanceMoved >= IDLE_DISTANCE_THRESHOLD) {
                                if (isCurrentlyIdle) {
                                    // Agent was idle but started moving again
                                    isCurrentlyIdle = false
                                    val idleDurationMin = ((System.currentTimeMillis() - lastSignificantMoveTime) / 60000).toInt()
                                    scope.launch {
                                        locationRepository.insertLocationLog(
                                            userId = userId,
                                            latitude = location.latitude,
                                            longitude = location.longitude,
                                            accuracy = location.accuracy.toDouble(),

                                            battery = BatteryUtils.getBatteryPercentage(this@LocationTrackerService),
                                            clientId = activeClientId,
                                            markActivity = "IDLE_END",
                                            markNotes = "Agent resumed movement after ${idleDurationMin}min idle"
                                        )
                                    }
                                    Timber.d("▶️ Agent resumed after ${idleDurationMin}min idle")
                                }
                                lastSignificantMoveTime = System.currentTimeMillis()
                            } else {
                                // Agent hasn't moved much
                                val timeSinceLastMove = System.currentTimeMillis() - lastSignificantMoveTime
                                if (!isCurrentlyIdle && timeSinceLastMove >= IDLE_THRESHOLD_MS && !activeClientId.isNullOrBlank()) {
                                    isCurrentlyIdle = true
                                    scope.launch {
                                        locationRepository.insertLocationLog(
                                            userId = userId,
                                            latitude = location.latitude,
                                            longitude = location.longitude,
                                            accuracy = location.accuracy.toDouble(),

                                            battery = BatteryUtils.getBatteryPercentage(this@LocationTrackerService),
                                            clientId = activeClientId,
                                            markActivity = "IDLE_START",
                                            markNotes = "Agent stationary for 15+ min during journey to ${activeClientName ?: activeClientId}"
                                        )
                                    }
                                    Timber.d("⏸️ Agent idle for 15+ min during journey")
                                }
                            }
                            
                            // ✅ S4: Determine activity tag based on state
                            var currentActivity: String
                            if (!activeClientId.isNullOrBlank()) {
                                // Agent is on an active journey
                                currentActivity = "TRAVELING"
                                if (activeClientLat != null && activeClientLng != null) {
                                    val clientLoc = Location("").apply {
                                        setLatitude(activeClientLat!!)
                                        setLongitude(activeClientLng!!)
                                    }
                                    val distanceToClient = location.distanceTo(clientLoc)
                                    if (distanceToClient <= 200f) {
                                        currentActivity = "AT_CLIENT_SITE"
                                    }
                                    Timber.d("📏 Distance to client $activeClientId: ${distanceToClient}m. Activity: $currentActivity")
                                }
                                // S3: If no client coords, default stays TRAVELING (not AT_CLIENT_SITE)
                            } else {
                                // S4: Agent is clocked in but not on a journey → ON_DUTY
                                currentActivity = "ON_DUTY"
                            }
                            
                            // ✅ Enhanced breadcrumb note
                            val breadcrumbNote = when (currentActivity) {
                                "TRAVELING" -> "Heading to ${activeClientName ?: activeClientId} via ${transportMode ?: "Car"}"
                                "AT_CLIENT_SITE" -> "At ${activeClientName ?: activeClientId} site"
                                "ON_DUTY" -> "On duty - no active journey"
                                else -> null
                            }

                            // ✅ Activity log throttling: Only log if >200m moved OR >5min since last log
                            val currentTime = System.currentTimeMillis()
                            val distanceFromLastLog = lastLoggedLocation?.let { location.distanceTo(it) } ?: Float.MAX_VALUE
                            val timeSinceLastLog = currentTime - lastLogTime
                            
                            val shouldLog = lastLoggedLocation == null || 
                                        distanceFromLastLog > LOG_DISTANCE_THRESHOLD_METERS || 
                                        timeSinceLastLog > LOG_TIME_THRESHOLD_MS
                            
                            if (shouldLog) {
                                val result = locationRepository.insertLocationLog(
                                    userId = userId,
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                    accuracy = location.accuracy.toDouble(),
                                    battery = com.bluemix.clients_lead.features.location.BatteryUtils.getBatteryPercentage(this@LocationTrackerService),
                                    clientId = activeClientId, // ✅ Pass active client if available
                                    markActivity = currentActivity, // ✅ Tag as Traveling or At Client Site
                                    markNotes = breadcrumbNote
                                )
                                when (result) {
                                    is AppResult.Success -> {
                                        Timber.d("✅ Saved breadcrumb point: ${result.data.id}")
                                        lastLoggedLocation = location
                                        lastLogTime = currentTime
                                    }
                                    is AppResult.Error -> Timber.e("❌ Failed to save breadcrumb: ${result.error.message}")
                                }
                            } else {
                                Timber.d("⏭️ Skipping log: only ${distanceFromLastLog.toInt()}m moved, ${timeSinceLastLog/60000}min elapsed")
                            }
                        }
                    }
                }

            } catch (e: CancellationException) {
                Timber.d("Location tracking cancelled")
                throw e // Re-throw to properly cancel coroutine
            } catch (e: Exception) {
                Timber.e(e, "Location tracking error")
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

    suspend fun clearUserPincode() {
        try {
            // Endpoint not implemented yet
            Timber.d("Pincode clear not implemented")
        } catch (e: Exception) {
            Timber.e("Failed to clear pincode: ${e.message}")
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
        // ...

        // Ensure all jobs are cancelled
        locationTrackingJob?.cancel()
        periodicSaveJob?.cancel()
        serviceJob.cancelChildren()
        serviceJob.cancel()
        scope.cancel()

        Timber.d("Service destroyed")
    }

    enum class Action {
        START, STOP, UPDATE_CLIENT
    }

    companion object {
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