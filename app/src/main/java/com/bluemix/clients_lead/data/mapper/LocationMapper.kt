package com.bluemix.clients_lead.data.mapper


import com.bluemix.clients_lead.data.models.LocationLogDto
import com.bluemix.clients_lead.domain.model.LocationLog

fun LocationLogDto.toDomain(): LocationLog {
    return LocationLog(
        id = id ?: "",
        userId = userId,
        userEmail = userEmail,
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        timestamp = timestamp ?: "",
        createdAt = createdAt ?: "",
        battery = battery,
        markActivity = markActivity,
        markNotes = markNotes,
        clientId = clientId,
        clientName = LocationLog.parseClientName(markNotes) // S12: Structured parse
    )
}

fun List<LocationLogDto>.toDomain(): List<LocationLog> {
    return map { it.toDomain() }
}
