package com.bluemix.clients_lead.domain.model

data class LocationLog(
    val id: String,
    val userId: String,
    val userEmail: String? = null, // Added for admin clarity
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double?,
    val timestamp: String,
    val createdAt: String,
    val battery: Int?,
    val markActivity: String? = null,
    val markNotes: String? = null,
    val clientId: String? = null
)