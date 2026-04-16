package com.bluemix.clients_lead.data.repository

import com.bluemix.clients_lead.core.common.extensions.runAppCatching
import com.bluemix.clients_lead.core.common.extensions.toAppError
import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.core.common.utils.AppError
import com.bluemix.clients_lead.core.network.ApiEndpoints
import com.bluemix.clients_lead.data.local.dao.PendingLocationLogDao
import com.bluemix.clients_lead.data.local.entity.PendingLocationLog
import com.bluemix.clients_lead.data.mapper.toDomain
import com.bluemix.clients_lead.data.models.LocationLogDto
import com.bluemix.clients_lead.domain.model.LocationLog
import com.bluemix.clients_lead.domain.repository.ILocationRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * Updated LocationRepository using REST API instead of Supabase
 */
class LocationRepositoryImpl(
    private val httpClient: io.ktor.client.HttpClient,
    private val context: android.content.Context,
    private val localDao: Any?
) : ILocationRepository {

    override suspend fun insertLocationLog(
        userId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Double?,
        battery: Int?,
        clientId: String?,
        markActivity: String?,
        markNotes: String?,
        isRetry: Boolean,
        timestamp: String?
    ): AppResult<LocationLog> = withContext(Dispatchers.IO) {
        // Try network first
        try {
            val response = httpClient.post(ApiEndpoints.Location.LOGS) {
                setBody(
                    CreateLocationRequest(
                        latitude = latitude,
                        longitude = longitude,
                        accuracy = accuracy,
                        battery = battery,
                        clientId = clientId?.toIntOrNull(),
                        markActivity = markActivity,
                        markNotes = markNotes,
                        timestamp = timestamp
                    )
                )
            }.body<CreateLocationResponse>()

            return@withContext AppResult.Success(response.log.toLocationLogDto().toDomain())
        } catch (e: Exception) {
            // Network/Server failed - buffer to Room for later sync
            if (!isRetry) {
                try {
                    val pendingLog = PendingLocationLog(
                        latitude = latitude,
                        longitude = longitude,
                        timestamp = timestamp ?: java.time.Instant.now().toString(),
                        accuracy = accuracy,
                        battery = battery,
                        markActivity = markActivity,
                        markNotes = markNotes,
                        clientId = clientId,
                        synced = false
                    )
                    
                    // Save to Room buffer
                    val database = com.bluemix.clients_lead.data.local.AppDatabase.getInstance(context)
                    database.pendingLocationLogDao().insert(pendingLog)
                    
                    timber.log.Timber.d("✅ Log buffered to Room for later sync")
                } catch (bufferError: Exception) {
                    timber.log.Timber.e(bufferError, "❌ Failed to buffer log")
                }
            }
            
            return@withContext AppResult.Error(AppError.Network("Offline: Log saved for sync"))
        }
    }

    private suspend fun saveToLocalBuffer(
        latitude: Double,
        longitude: Double,
        accuracy: Double?,
        battery: Int?,
        clientId: String?,
        markActivity: String?,
        markNotes: String?,
        timestamp: String? = null
    ) {
        // TODO: Implement PendingLocationLogDao
//        localDao?.insert(
//            com.bluemix.clients_lead.data.local.entity.PendingLocationLog(
//                latitude = latitude,
//                longitude = longitude,
//                timestamp = timestamp ?: java.time.Instant.now().toString(),
//                accuracy = accuracy,
//                battery = battery,
//                markActivity = markActivity,
//                markNotes = markNotes,
//                clientId = clientId,
//                synced = false
//            )
//        )
        // ✅ TRIGGER WORKER: Sync as soon as network is back
        // com.bluemix.clients_lead.features.location.LocationSyncManager.scheduleSync(context)
    }

    override suspend fun getLocationLogs(
        userId: String,
        limit: Int
    ): AppResult<List<LocationLog>> = withContext(Dispatchers.IO) {
        runAppCatching(mapper = { it.toAppError() }) {
            val response = httpClient.get(ApiEndpoints.Location.LOGS) {
                parameter("userId", userId)
                parameter("limit", limit)
            }.body<LocationLogsResponse>()

            response.logs.map { it.toLocationLogDto() }.toDomain()
        }
    }

    override suspend fun getLocationLogsByDateRange(
        userId: String,
        startDate: String,
        endDate: String,
        limit: Int,
        page: Int
    ): AppResult<List<LocationLog>> = withContext(Dispatchers.IO) {
        runAppCatching(mapper = { it.toAppError() }) {
            val response = httpClient.get(ApiEndpoints.Location.LOGS) {
                parameter("userId", userId)
                parameter("startDate", startDate)
                parameter("endDate", endDate)
                parameter("limit", limit)
                parameter("page", page)
            }.body<LocationLogsResponse>()

            response.logs.map { it.toLocationLogDto() }.toDomain()
        }
    }

    override suspend fun deleteOldLogs(olderThanDays: Int): AppResult<Int> =
        withContext(Dispatchers.IO) {
            runAppCatching(mapper = { it.toAppError() }) {
                0
            }
        }

    override suspend fun clearAllLogs(): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            runAppCatching(mapper = { it.toAppError() }) {
                httpClient.delete(ApiEndpoints.Location.CLEAR_ALL)
                Unit
            }
        }
}

// ==================== Request Models ====================

@Serializable
data class CreateLocationRequest(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double? = null,
    val markActivity: String? = null,
    val markNotes: String? = null,
    val battery: Int? = null,
    val clientId: Int? = null,
    val timestamp: String? = null // ✅ ADDED for offline sync preservation
)

// ==================== Response Models ====================

@Serializable
data class CreateLocationResponse(
    val message: String,
    val log: BackendLocationLog
)

@Serializable
data class LocationLogsResponse(
    val logs: List<BackendLocationLog>,
    val pagination: LocationPaginationData? = null
)

@Serializable
data class BackendLocationLog(
    val id: String,
    val userId: String,
    val email: String? = null, // Added for admin clarity
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double? = null,
    val markActivity: String? = null,
    val markNotes: String? = null,
    val timestamp: String,
    val battery: Int? = null,
    val clientId: String? = null
)

@Serializable
data class LocationPaginationData(
    val page: Int,
    val limit: Int
)

// ==================== Mapping Functions ====================

/**
 * Convert backend location log to app's LocationLogDto
 */
fun BackendLocationLog.toLocationLogDto(): LocationLogDto {
    return LocationLogDto(
        id = this.id,
        userId = this.userId,
        userEmail = this.email, // Map the new field
        latitude = this.latitude,
        longitude = this.longitude,
        accuracy = this.accuracy,
        timestamp = this.timestamp,
        battery = this.battery,
        markActivity = this.markActivity,
        markNotes = this.markNotes,
        clientId = this.clientId
    )
}