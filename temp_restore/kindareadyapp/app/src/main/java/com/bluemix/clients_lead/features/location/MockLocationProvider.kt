// File: app/src/main/java/com/bluemix/clients_lead/features/location/MockLocationProvider.kt
package com.bluemix.clients_lead.features.location

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.usecases.InsertLocationLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Mock location provider for testing GPS features
 * Simulates GPS movement via joystick control
 *
 * ✅ NEW: Can optionally save locations to database for full testing
 */
class MockLocationProvider(
    private val context: Context,
    private val insertLocationLog: InsertLocationLog? = null,  // ✅ NEW: Optional dependency
    private val userId: String? = null  // ✅ NEW: User ID for logging
) {

    private val _mockLocation = MutableStateFlow<Location?>(null)
    val mockLocation: StateFlow<Location?> = _mockLocation.asStateFlow()

    private var currentLat = 19.0760 // Mumbai default
    private var currentLng = 72.8777

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    // ✅ NEW: Coroutine scope for database operations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ✅ NEW: Track if logging is enabled
    var isLoggingEnabled = false

    /**
     * Move location in a direction
     * @param direction: "north", "south", "east", "west", "ne", "nw", "se", "sw"
     * @param speedMetersPerSecond: how fast to move (default: walking speed)
     */
    fun moveInDirection(direction: String, speedMetersPerSecond: Double = 1.4) {
        // 1 degree lat ≈ 111km, 1 degree lng ≈ 111km * cos(lat)
        val latChange = speedMetersPerSecond / 111000.0
        val lngChange = speedMetersPerSecond / (111000.0 * kotlin.math.cos(Math.toRadians(currentLat)))

        when (direction.lowercase()) {
            "north" -> currentLat += latChange
            "south" -> currentLat -= latChange
            "east" -> currentLng += lngChange
            "west" -> currentLng -= lngChange
            "ne", "northeast" -> {
                currentLat += latChange
                currentLng += lngChange
            }
            "nw", "northwest" -> {
                currentLat += latChange
                currentLng -= lngChange
            }
            "se", "southeast" -> {
                currentLat -= latChange
                currentLng += lngChange
            }
            "sw", "southwest" -> {
                currentLat -= latChange
                currentLng -= lngChange
            }
        }

        updateMockLocation()
    }

    /**
     * Set exact coordinates
     */
    fun setLocation(lat: Double, lng: Double) {
        currentLat = lat
        currentLng = lng
        updateMockLocation()
    }

    /**
     * Reset to default Mumbai location
     */
    fun reset() {
        currentLat = 19.0760
        currentLng = 72.8777
        updateMockLocation()
    }

    private fun updateMockLocation() {
        val location = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = currentLat
            longitude = currentLng
            accuracy = 5f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                bearingAccuracyDegrees = 10f
                speedAccuracyMetersPerSecond = 1f
                verticalAccuracyMeters = 3f
            }
        }

        _mockLocation.value = location
        Timber.d("🎮 Mock location updated: $currentLat, $currentLng")

        // ✅ NEW: Optionally save to database
        if (isLoggingEnabled && insertLocationLog != null && userId != null) {
            saveToDatabase(location)
        }
    }

    /**
     * ✅ NEW: Save mock location to database
     */
    /**
     * ✅ NEW: Save mock location to database
     */
    private fun saveToDatabase(location: Location) {
        scope.launch {
            try {
                // Ensure userId is present safely
                val currentUserId = userId ?: return@launch
                val battery = BatteryUtils.getBatteryPercentage(context)

                // Use ?.let to safely access the nullable use case and ensure it's not null during execution
                insertLocationLog?.let { useCase ->
                    when (val result = useCase.invoke(
                        userId = currentUserId,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy.toDouble(),
                        battery = battery
                    )) {
                        is AppResult.Success -> {
                            Timber.d("💾 Mock location saved to DB: ${result.data.id}")
                        }
                        is AppResult.Error -> {
                            Timber.e("❌ Failed to save mock location: ${result.error.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "❌ Exception saving mock location")
            }
        }
    }

    /**
     * Enable mock location mode (requires app to be set as mock location app in Developer Options)
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun enableMockMode() {
        try {
            locationManager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false, false, false, false, true, true, true,
                ProviderProperties.POWER_USAGE_MEDIUM, ProviderProperties.ACCURACY_COARSE
            )
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            Timber.d("✅ Mock location mode enabled")
        } catch (e: SecurityException) {
            Timber.e(e, "❌ Enable 'Mock Location' in Developer Settings and select this app")
        }
    }

    fun disableMockMode() {
        try {
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            Timber.e(e, "Failed to disable mock mode")
        }
    }
}