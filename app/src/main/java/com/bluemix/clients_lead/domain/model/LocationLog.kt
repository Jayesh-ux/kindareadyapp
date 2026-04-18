package com.bluemix.clients_lead.domain.model

data class LocationLog(
    val id: String,
    val userId: String,
    val userEmail: String? = null,
    val companyName: String? = null,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double?,
    val timestamp: String,
    val createdAt: String,
    val battery: Int?,
    val batteryStale: Boolean = false,
    val markActivity: String? = null,
    val markNotes: String? = null,
    val clientId: String? = null,
    val clientName: String? = null,
    val distanceDelta: Double? = null,
    val speedKmh: Double? = null,
    val validated: Boolean = true,
    val validationReason: String? = null,
    val locationConfidence: String = "MEDIUM",
    val isInitial: Boolean = false,
    val idleStateFlag: Boolean = false,
    val transportMode: String? = null
) {
    companion object {
        fun parseClientName(markNotes: String?): String? {
            if (markNotes.isNullOrBlank()) return null
            val regex = Regex(
                "(?:Heading to|At|journey to|ended journey to) (.+?)(?:\\s+via|\\s+site|ended|$)",
                RegexOption.IGNORE_CASE
            )
            return regex.find(markNotes)?.groupValues?.getOrNull(1)?.trim()
        }
    }
}

enum class TrackingState {
    IDLE,
    MOVING,
    PAUSED,
    SESSION_ENDED,
    UNKNOWN
}

data class TrackingUIState(
    val state: TrackingState,
    val battery: Int?,
    val batteryStale: Boolean,
    val validationStatus: String,
    val idle: Boolean,
    val sessionState: String,
    val lastValidated: Boolean,
    val lastValidationReason: String?,
    val lastLocationConfidence: String
) {
    companion object {
        fun fromLocationLog(log: LocationLog?): TrackingUIState {
            if (log == null) {
                return TrackingUIState(
                    state = TrackingState.UNKNOWN,
                    battery = null,
                    batteryStale = false,
                    validationStatus = "UNKNOWN",
                    idle = false,
                    sessionState = "UNKNOWN",
                    lastValidated = false,
                    lastValidationReason = null,
                    lastLocationConfidence = "LOW"
                )
            }

            val state = when {
                log.idleStateFlag -> TrackingState.IDLE
                log.validated && log.locationConfidence != "LOW" -> TrackingState.MOVING
                else -> TrackingState.IDLE
            }

            val validationStatus = when {
                !log.validated -> "REJECTED"
                log.locationConfidence == "LOW" -> "LOW_CONFIDENCE"
                else -> "VALID"
            }

            return TrackingUIState(
                state = state,
                battery = log.battery,
                batteryStale = log.batteryStale,
                validationStatus = validationStatus,
                idle = log.idleStateFlag,
                sessionState = "ACTIVE",
                lastValidated = log.validated,
                lastValidationReason = log.validationReason,
                lastLocationConfidence = log.locationConfidence
            )
        }
    }
}