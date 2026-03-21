package com.bluemix.clients_lead.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a client meeting/visit
 */
data class Meeting(
    val id: String,
    val clientId: String,
    val userId: String,
    val startTime: String,
    val endTime: String?,
    val status: MeetingStatus,
    val comments: String?,
    val attachments: List<String> = emptyList(),
    val location: MeetingLocation?,
    val createdAt: String,
    val updatedAt: String,
    val proximity: ProximityResult? = null,
    val clientName: String? = null,     // ✅ NEW: Read-only display info
    val clientAddress: String? = null  // ✅ NEW: Read-only display info
)

/**
 * Result of the backend PostGIS proximity check on meeting start.
 * Null when the backend didn't run the check (no GPS sent).
 */
data class ProximityResult(
    val verified: Boolean,
    val distanceMetres: Double?,
    val reason: String  // WithinRange | OutOfRange | ClientLocationUnknown | NotChecked | MissingCoordinates
)

enum class MeetingStatus {
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

data class MeetingLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double?
)

/**
 * DTO for creating a new meeting
 */
@Serializable
data class CreateMeetingRequest(
    val clientId: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Double? = null
)

/**
 * DTO for updating an existing meeting
 */
@Serializable
data class UpdateMeetingRequest(
    val endTime: String? = null,
    val status: String? = "COMPLETED",
    val comments: String? = null,
    val attachments: List<String>? = null,
    val clientStatus: String? = null, // ✅ NEW: Update client status (active/inactive/completed)
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Double? = null
)