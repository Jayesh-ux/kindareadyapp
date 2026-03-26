package com.bluemix.clients_lead.domain.model

// Domain Model - use id as userId
data class UserProfile(
    val userId: String,  // This maps to profiles.id
    val email: String?,
    val fullName: String?,
    val department: String?,
    val workHoursStart: String?,
    val workHoursEnd: String?,
    val lastSeen: String?, // NEW: Last activity timestamp
    val batteryPercentage: Int?, // NEW: Agent's battery level
    val currentActivity: String?, // NEW: e.g., "Walking", "Still"
    val createdAt: String,
    val updatedAt: String?
)


data class LocationTrackingPreference(
    val isEnabled: Boolean
)
