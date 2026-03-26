package com.bluemix.clients_lead.core.common.utils

import com.bluemix.clients_lead.domain.model.LocationLog
import kotlin.math.*

object LocationUtils {

    /**
     * Calculates distance between two points using Haversine formula
     * @return Distance in meters
     */
    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371e3 // Earth radius in meters
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lon2 - lon1)

        val a = sin(dPhi / 2) * sin(dPhi / 2) +
                cos(phi1) * cos(phi2) *
                sin(dLambda / 2) * sin(dLambda / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return r * c
    }

    /**
     * Calculates total distance for a sequence of location logs
     * @return Distance in kilometers
     */
    fun calculateTotalDistanceKm(logs: List<LocationLog>): Double {
        if (logs.size < 2) return 0.0
        
        var totalDist = 0.0
        val sortedLogs = logs.sortedBy { it.timestamp }
        
        for (i in 0 until sortedLogs.size - 1) {
            val start = sortedLogs[i]
            val end = sortedLogs[i + 1]
            totalDist += calculateDistanceMeters(
                start.latitude, start.longitude,
                end.latitude, end.longitude
            )
        }
        return totalDist / 1000.0
    }

    /**
     * Calculates active duration between first and last log in minutes
     */
    fun calculateActiveDurationMinutes(logs: List<LocationLog>): Long {
        if (logs.size < 2) return 0
        val sortedLogs = logs.sortedBy { it.timestamp }
        val firstTime = DateTimeUtils.parseDate(sortedLogs.first().timestamp)?.time ?: 0L
        val lastTime = DateTimeUtils.parseDate(sortedLogs.last().timestamp)?.time ?: 0L
        val durationMillis = lastTime - firstTime
        return durationMillis / (1000 * 60)
    }

    /**
     * Counts unique clients visited based on MEETING_START activity
     */
    fun countClientsVisited(logs: List<LocationLog>): Int {
        return logs.filter { it.markActivity == "MEETING_START" }
            .mapNotNull { it.clientId }
            .distinct()
            .size
    }

    /**
     * Get a user friendly address from latitude and longitude
     */
    suspend fun getAddress(context: android.content.Context, latitude: Double, longitude: Double): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val subLocal = address.subLocality ?: ""
                val locality = address.locality ?: ""
                val adminArea = address.adminArea ?: ""
                
                val result = listOf(subLocal, locality, adminArea)
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                
                if (result.isBlank()) "Unknown Location (Area: ${String.format("%.4f, %.4f", latitude, longitude)})" else result
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
