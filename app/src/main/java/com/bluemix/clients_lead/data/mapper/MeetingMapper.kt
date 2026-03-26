package com.bluemix.clients_lead.data.mapper

import com.bluemix.clients_lead.data.models.MeetingDto
import com.bluemix.clients_lead.domain.model.Meeting
import com.bluemix.clients_lead.domain.model.MeetingLocation
import com.bluemix.clients_lead.domain.model.MeetingStatus
import com.bluemix.clients_lead.domain.model.ProximityResult

object MeetingMapper {

    /** Map a MeetingDto to the domain Meeting model (no proximity). */
    fun toDomain(dto: MeetingDto): Meeting = toDomain(dto, proximity = null)

    /**
     * Map a MeetingDto + optional ProximityResult (from start-meeting response wrapper)
     * to the domain Meeting model.
     */
    fun toDomain(dto: MeetingDto, proximity: ProximityResult?): Meeting {
        val location = if (dto.startLatitude != null && dto.startLongitude != null) {
            MeetingLocation(
                latitude = dto.startLatitude,
                longitude = dto.startLongitude,
                accuracy = dto.startAccuracy
            )
        } else null

        return Meeting(
            id = dto.id,
            clientId = dto.clientId,
            userId = dto.userId,
            startTime = dto.startTime,
            endTime = dto.endTime,
            status = when (dto.status.uppercase()) {
                "IN_PROGRESS" -> MeetingStatus.IN_PROGRESS
                "COMPLETED" -> MeetingStatus.COMPLETED
                "CANCELLED" -> MeetingStatus.CANCELLED
                else -> MeetingStatus.IN_PROGRESS
            },
            comments = dto.comments,
            attachments = dto.attachments,
            location = location,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            proximity = proximity, // ✅ attach PostGIS proximity result
            clientName = dto.clientName,
            clientAddress = dto.clientAddress
        )
    }
}