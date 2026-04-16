package com.bluemix.clients_lead.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ActiveTrip(
    val id: String = "",
    val status: String = "IN_PROGRESS",
    val currentLegIndex: Int = 0,
    val startLocation: String = "",
    val endLocation: String = "",
    val transportMode: String = "",
    val isMultiLeg: Boolean = false,
    val legs: List<ActiveTripLeg> = emptyList(),
    val startTime: Long? = null,
    val endTime: Long? = null
)

@Serializable
data class ActiveTripLeg(
    val id: String = "",
    val legNumber: Int = 0,
    val startLocation: String = "",
    val endLocation: String = "",
    val distanceKm: Double = 0.0,
    val transportMode: String = "",
    val amountSpent: Double = 0.0,
    val status: String = "PENDING",
    val notes: String? = null
)