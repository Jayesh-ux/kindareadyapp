// data/models/LocationLogDto.kt
package com.bluemix.clients_lead.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LocationLogDto(
    val id: String? = null,
    @SerialName("user_id")
    val userId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double? = null,
    val timestamp: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val battery: Int? = null,
    val markActivity: String? = null,
    val markNotes: String? = null,
    @SerialName("client_id")
    val clientId: String? = null
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
