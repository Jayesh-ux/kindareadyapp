package com.bluemix.clients_lead.domain.usecases

import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.model.LocationLog
import com.bluemix.clients_lead.domain.repository.ILocationRepository

/**
 * Insert a location log entry
 */
class InsertLocationLog(
    private val repository: ILocationRepository
) {
    suspend operator fun invoke(
        userId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Double? = null,
        battery: Int? = null,
        clientId: String? = null,
        markActivity: String? = null,
        markNotes: String? = null
    ): AppResult<LocationLog> = repository.insertLocationLog(userId, latitude, longitude, accuracy, battery, clientId, markActivity, markNotes)
}

/**
 * Get location logs for a user
 */
class GetLocationLogs(
    private val repository: ILocationRepository
) {
    suspend operator fun invoke(
        userId: String,
        limit: Int = 100
    ): AppResult<List<LocationLog>> = repository.getLocationLogs(userId, limit)
}

/**
 * Get location logs within a date range
 */
class GetLocationLogsByDateRange(
    private val repository: ILocationRepository
) {
    suspend operator fun invoke(
        userId: String,
        startDate: String,
        endDate: String,
        limit: Int = 100,
        page: Int = 1
    ): AppResult<List<LocationLog>> = repository.getLocationLogsByDateRange(userId, startDate, endDate, limit, page)
}

/**
 * Delete old location logs for cleanup
 */
class DeleteOldLocationLogs(
    private val repository: ILocationRepository
) {
    suspend operator fun invoke(olderThanDays: Int): AppResult<Int> =
        repository.deleteOldLogs(olderThanDays)

    suspend fun clearAll(): AppResult<Unit> =
        repository.clearAllLogs()
}
