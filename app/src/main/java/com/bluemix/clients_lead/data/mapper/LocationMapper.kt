package com.bluemix.clients_lead.data.mapper


import com.bluemix.clients_lead.data.models.LocationLogDto
import com.bluemix.clients_lead.domain.model.LocationLog

fun LocationLogDto.toDomain(): LocationLog {
    return LocationLog(
        id = id ?: "",
        userId = userId,
        userEmail = userEmail,
        companyName = companyName,
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        timestamp = timestamp ?: "",
        createdAt = createdAt ?: "",
        battery = battery,
        batteryStale = batteryStale,
        markActivity = markActivity,
        markNotes = markNotes,
        clientId = clientId,
        clientName = LocationLog.parseClientName(markNotes),
        distanceDelta = distanceDelta,
        speedKmh = speedKmh,
        validated = validated,
        validationReason = validationReason,
        locationConfidence = locationConfidence,
        isInitial = isInitial,
        idleStateFlag = idleStateFlag,
        transportMode = transportMode
    )
}

fun List<LocationLogDto>.toDomain(): List<LocationLog> {
    return map { it.toDomain() }
}
