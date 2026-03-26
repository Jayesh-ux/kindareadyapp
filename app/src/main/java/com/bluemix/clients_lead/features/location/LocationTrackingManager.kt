package com.bluemix.clients_lead.features.location


import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat


/**
 * Manager for location tracking service.
 * Separates service management from ViewModel.
 */
class LocationTrackingManager(
    private val context: Context
) {
    fun startTracking() {
        val intent = Intent(context, LocationTrackerService::class.java).apply {
            action = LocationTrackerService.Action.START.name
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopTracking() {
        val intent = Intent(context, LocationTrackerService::class.java).apply {
            action = LocationTrackerService.Action.STOP.name
        }
        context.startService(intent)
    }

    fun updateActiveClient(
        clientId: String?, 
        clientName: String? = null,
        transportMode: String? = null,
        latitude: Double? = null, 
        longitude: Double? = null
    ) {
        val intent = Intent(context, LocationTrackerService::class.java).apply {
            action = LocationTrackerService.Action.UPDATE_CLIENT.name
            putExtra(LocationTrackerService.EXTRA_CLIENT_ID, clientId)
            if (clientName != null) putExtra(LocationTrackerService.EXTRA_CLIENT_NAME, clientName)
            if (transportMode != null) putExtra(LocationTrackerService.EXTRA_TRANSPORT_MODE, transportMode)
            if (latitude != null) putExtra(LocationTrackerService.EXTRA_CLIENT_LAT, latitude)
            if (longitude != null) putExtra(LocationTrackerService.EXTRA_CLIENT_LNG, longitude)
        }
        context.startService(intent)
    }
}
