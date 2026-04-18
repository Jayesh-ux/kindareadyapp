// data/models/LocationLogDto.kt
package com.bluemix.clients_lead.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LocationLogDto(
    val id: String? = null,
    @SerialName("user_id")
    val userId: String,
    val userEmail: String? = null,
    @SerialName("companyName")
    val companyName: String? = null,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double? = null,
    val timestamp: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val battery: Int? = null,
    @SerialName("battery_stale")
    val batteryStale: Boolean = false,
    val markActivity: String? = null,
    val markNotes: String? = null,
    @SerialName("client_id")
    val clientId: String? = null,
    @SerialName("distance_delta")
    val distanceDelta: Double? = null,
    @SerialName("speed_kmh")
    val speedKmh: Double? = null,
    val validated: Boolean = true,
    @SerialName("validation_reason")
    val validationReason: String? = null,
    @SerialName("location_confidence")
    val locationConfidence: String = "MEDIUM",
    @SerialName("is_initial")
    val isInitial: Boolean = false,
    @SerialName("idle_state_flag")
    val idleStateFlag: Boolean = false,
    @SerialName("transport_mode")
    val transportMode: String? = null
)

@Serializable
data class LocationLogInsertDto(
    @SerialName("user_id")
    val userId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double? = null,
    val battery: Int? = null,
    @SerialName("client_id")
    val clientId: Int? = null
)

@Serializable
data class TrackingStateResponse(
    @SerialName("is_active")
    val isActive: Boolean,
    @SerialName("is_paused")
    val isPaused: Boolean,
    @SerialName("was_idle")
    val wasIdle: Boolean,
    @SerialName("last_valid_location")
    val lastValidLocation: LastLocation? = null,
    @SerialName("consecutive_invalid")
    val consecutiveInvalid: Int = 0
)

@Serializable
data class LastLocation(
    val lat: Double,
    val lng: Double,
    val timestamp: String
)
