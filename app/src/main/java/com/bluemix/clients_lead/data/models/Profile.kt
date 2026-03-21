package com.bluemix.clients_lead.data.models


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// DTO
@Serializable
data class ProfileDto(
    @SerialName("id")
    val id: String,  // Your existing primary key

    @SerialName("email")
    val email: String?,

    @SerialName("fullName")
    val fullName: String?,

    @SerialName("department")
    val department: String?,

    @SerialName("workHoursStart")
    val workHoursStart: String?,

    @SerialName("workHoursEnd")
    val workHoursEnd: String?,

    @SerialName("lastSeen")
    val lastSeen: String? = null,

    @SerialName("batteryPercentage")
    val batteryPercentage: Int? = null,

    @SerialName("currentActivity")
    val currentActivity: String? = null,

    @SerialName("createdAt")
    val createdAt: String,

    @SerialName("updatedAt")
    val updatedAt: String? = null
)


@Serializable
data class ProfileInsertDto(
    @SerialName("id")
    val id: String,  // This is the user_id from auth.users

    @SerialName("email")
    val email: String?,

    @SerialName("fullName")
    val fullName: String?
)

@Serializable
data class ProfileUpdateDto(
    @SerialName("fullName")
    val fullName: String?,

    @SerialName("department")
    val department: String?,

    @SerialName("workHoursStart")
    val workHoursStart: String?,

    @SerialName("workHoursEnd")
    val workHoursEnd: String?,

    @SerialName("lastSeen")
    val lastSeen: String? = null,

    @SerialName("batteryPercentage")
    val batteryPercentage: Int? = null,

    @SerialName("currentActivity")
    val currentActivity: String? = null
)
