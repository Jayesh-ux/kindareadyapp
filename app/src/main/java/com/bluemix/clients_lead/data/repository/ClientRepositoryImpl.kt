package com.bluemix.clients_lead.data.repository

import com.bluemix.clients_lead.core.common.extensions.runAppCatching
import com.bluemix.clients_lead.core.common.extensions.toAppError
import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.core.network.ApiEndpoints
import com.bluemix.clients_lead.data.mapper.toDomain
import com.bluemix.clients_lead.data.models.ClientDto
import com.bluemix.clients_lead.domain.model.Client
import io.ktor.client.request.forms.submitFormWithBinaryData
import com.bluemix.clients_lead.domain.repository.IClientRepository
import com.bluemix.clients_lead.data.models.CreateClientRequest
import com.bluemix.clients_lead.domain.repository.SelfHealResult
import com.bluemix.clients_lead.domain.repository.TagLocationResponse
import com.bluemix.clients_lead.domain.repository.MissingLocationsResult
import com.bluemix.clients_lead.domain.repository.MissingClient
import com.bluemix.clients_lead.domain.repository.LocationReport
import com.bluemix.clients_lead.domain.repository.MissingBreakdown
import io.ktor.client.*
import io.ktor.client.request.forms.formData
import io.ktor.client.call.*
import io.ktor.http.Headers
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import android.util.Log
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.ContentType
import com.bluemix.clients_lead.domain.model.DailySummary

class ClientRepositoryImpl(
    private val httpClient: HttpClient
) : IClientRepository {

    override suspend fun getAllClients(userId: String, page: Int, limit: Int): AppResult<List<Client>> =
        withContext(Dispatchers.IO) {
            Log.d("CLIENT_REPO", "📋 Fetching clients (Page: $page, Limit: $limit)...")

            runAppCatching(mapper = { it.toAppError() }) {
                Log.d("CLIENT_REPO", "📡 Making request to: ${ApiEndpoints.BASE_URL}${ApiEndpoints.Clients.BASE}")

                val response = httpClient.get(ApiEndpoints.Clients.BASE) {
                    parameter("page", page)
                    parameter("limit", limit)
                }.body<ClientsResponse>()

                Log.d("CLIENT_REPO", "✅ Got ${response.clients.size} clients")

                response.clients.map { it.toClientDto() }.toDomain()
            }
        }

    override suspend fun getClientsByStatus(
        userId: String,
        status: String,
        page: Int,
        limit: Int
    ): AppResult<List<Client>> = withContext(Dispatchers.IO) {
        runAppCatching(mapper = { it.toAppError() }) {
            val response = httpClient.get(ApiEndpoints.Clients.BASE) {
                parameter("status", status)
                parameter("page", page)
                parameter("limit", limit)
            }.body<ClientsResponse>()

            response.clients.map { it.toClientDto() }.toDomain()
        }
    }

    override suspend fun getClientsWithLocation(userId: String, isAdmin: Boolean): AppResult<com.bluemix.clients_lead.domain.repository.ClientsResult> =
        withContext(Dispatchers.IO) {
            runAppCatching(mapper = { it.toAppError() }) {
                val response = httpClient.get(ApiEndpoints.Clients.BASE) {
                    parameter("limit", 2000)
                    if (isAdmin) {
                        parameter("searchMode", "remote")
                        // When admin selects an agent, filter clients by that agent's ID
                        if (userId.isNotEmpty()) {
                            parameter("created_by", userId)
                        }
                    }
                }.body<ClientsResponse>()

                val clients = response.clients
                    .map { it.toClientDto() }
                    .toDomain()

                com.bluemix.clients_lead.domain.repository.ClientsResult(
                    clients = clients,
                    message = response.message
                )
            }
        }

    override suspend fun getClientById(clientId: String): AppResult<Client> =
        withContext(Dispatchers.IO) {
            runAppCatching(mapper ={ it.toAppError() }) {
                Log.d("CLIENT_REPO", "📋 Fetching client by ID: $clientId")
                val response = httpClient.get(ApiEndpoints.Clients.byId(clientId))
                    .body<SingleClientResponse>()

                Log.d("CLIENT_REPO", "✅ Client response: ${response.client.name}")
                response.client.toClientDto().toDomain()
            }
        }

    override suspend fun searchClients(
        userId: String,
        query: String
    ): AppResult<List<Client>> = withContext(Dispatchers.IO) {
        runAppCatching(mapper = { it.toAppError() }) {
            Log.d("CLIENT_REPO", "🔍 Local search: $query")

            val response = httpClient.get(ApiEndpoints.Clients.BASE) {
                parameter("search", query)
                parameter("searchMode", "local")
                parameter("limit", 2000)
            }.body<ClientsResponse>()

            Log.d("CLIENT_REPO", "✅ Found ${response.clients.size} local clients")
            response.clients.map { it.toClientDto() }.toDomain()
        }
    }

    override suspend fun searchRemoteClients(
        userId: String,
        query: String,
        filterType: String?,
        filterValue: String?
    ): AppResult<List<Client>> = withContext(Dispatchers.IO) {
        runAppCatching(mapper = { it.toAppError() }) {
            Log.d("CLIENT_REPO", "🌐 Remote search: query='$query', filterType='$filterType', filterValue='$filterValue'")

            val response = httpClient.get(ApiEndpoints.Clients.BASE) {
                parameter("search", query)
                parameter("searchMode", "remote")
                parameter("limit", 2000)

                if (filterType != null && filterValue != null) {
                    parameter("filterType", filterType)
                    parameter("filterValue", filterValue)
                }
            }.body<ClientsResponse>()

            Log.d("CLIENT_REPO", "✅ Found ${response.clients.size} remote clients")
            response.clients.map { it.toClientDto() }.toDomain()
        }
    }

    override suspend fun updateClientAddress(
        clientId: String,
        newAddress: String
    ): AppResult<Client> = withContext(Dispatchers.IO) {
        runAppCatching(mapper = { it.toAppError() }) {
            Log.d("CLIENT_REPO", "📍 Updating address for client: $clientId")

            val request = UpdateAddressRequest(address = newAddress)

            val response = httpClient.patch(ApiEndpoints.Clients.updateAddress(clientId)) {
                setBody(request)
            }.body<SingleClientResponse>()

            Log.d("CLIENT_REPO", "✅ Address updated successfully")
            response.client.toClientDto().toDomain()
        }
    }

    override suspend fun updateClientLocation(
        clientId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Double?
    ): AppResult<Client> = withContext(Dispatchers.IO) {
        runAppCatching(mapper = { it.toAppError() }) {
            Log.d("CLIENT_REPO", "📍 Tagging GPS location for client: $clientId")

            val request = UpdateLocationRequest(
                latitude = latitude,
                longitude = longitude,
                accuracy = accuracy
            )

            val response = httpClient.patch("${ApiEndpoints.Clients.BASE}/$clientId/location") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body<SingleClientResponse>()

            Log.d("CLIENT_REPO", "✅ GPS Location tagged successfully")
            response.client.toClientDto().toDomain()
        }
    }


    override suspend fun createClient(
        name: String,
        phone: String?,
        email: String?,
        address: String?,
        pincode: String?,
        notes: String?,
        latitude: Double?,
        longitude: Double?
    ): AppResult<Client> = withContext(Dispatchers.IO) {
        runAppCatching(mapper = { it.toAppError() }) {
            Log.d("CLIENT_REPO", "🔨 Creating client: $name")

            val request = CreateClientRequest(
                name = name,
                phone = phone,
                email = email,
                address = address,
                pincode = pincode,
                notes = notes,
                latitude = latitude,
                longitude = longitude
            )

            val response = httpClient.post(ApiEndpoints.Clients.MANUAL_CREATE) {
                setBody(request)
            }.body<SingleClientResponse>()

            Log.d("CLIENT_REPO", "✅ Client created: ${response.client.id}")

            response.client.toClientDto().toDomain()
        }
    }


    override suspend fun getTeamLocations(): AppResult<List<com.bluemix.clients_lead.domain.repository.AgentLocation>> =
        withContext(Dispatchers.IO) {
            runAppCatching(mapper = { it.toAppError() }) {
                Log.d("CLIENT_REPO", "📡 Fetching team locations...")
                val response = httpClient.get(ApiEndpoints.Admin.TEAM_LOCATIONS).body<TeamLocationsResponse>()
                
                response.agents.map { it.toDomain() }
            }
        }

    override suspend fun getLiveAgents(): AppResult<List<com.bluemix.clients_lead.domain.repository.AgentLocation>> =
        withContext(Dispatchers.IO) {
            runAppCatching(mapper = { it.toAppError() }) {
                Log.d("CLIENT_REPO", "📡 Fetching LIVE agents...")
                val response = httpClient.get(ApiEndpoints.Admin.LIVE_AGENTS).body<TeamLocationsResponse>()
                
                response.agents.map { it.toDomain() }
            }
        }

    override suspend fun getDailySummary(): AppResult<DailySummary> =
        withContext(Dispatchers.IO) {
            runAppCatching(mapper = { it.toAppError() }) {
                Log.d("CLIENT_REPO", "📡 Fetching daily summary...")
                val response = httpClient.get(ApiEndpoints.Admin.DAILY_SUMMARY).body<DailySummaryDto>()
                
                DailySummary(
                    activeAgents = response.activeAgents,
                    idleAgents = response.idleAgents,
                    totalMeetings = response.totalMeetings,
                    verifiedMeetings = response.verifiedMeetings,
                    totalDistance = response.totalDistance,
                    alertsCount = response.alertsCount
                )
            }
        }

    override suspend fun updateUserStatus(userId: String, isActive: Boolean): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            runAppCatching(mapper = { it.toAppError() }) {
                Log.d("CLIENT_REPO", "👤 Updating user status: $userId -> isActive=$isActive")
                val response = httpClient.patch(ApiEndpoints.Admin.updateUserStatus(userId)) {
                    contentType(io.ktor.http.ContentType.Application.Json)
                    setBody(mapOf("isActive" to isActive))
                }
                Log.d("CLIENT_REPO", "✅ User status update response: ${response.status}")
                Unit
            }
        }

    override suspend fun updateClientServiceStatus(serviceId: String, status: String): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            runAppCatching(mapper = { it.toAppError() }) {
                Log.d("CLIENT_REPO", "🔧 Updating service status: $serviceId -> $status")
                httpClient.patch(ApiEndpoints.Services.updateStatus(serviceId)) {
                    contentType(io.ktor.http.ContentType.Application.Json)
                    setBody(mapOf("status" to status))
                }
                Unit
            }
        }

    override suspend fun getClientServices(clientId: String): AppResult<List<com.bluemix.clients_lead.domain.model.ClientService>> =
        withContext(Dispatchers.IO) {
            runAppCatching(mapper = { it.toAppError() }) {
                Log.d("CLIENT_REPO", "📡 Fetching services for client: $clientId")
                val response = httpClient.get(ApiEndpoints.Services.clientServices(clientId)).body<ClientServicesResponse>()
                
                response.services.map { it.toDomain() }
            }
        }
    
    override suspend fun getAllClientServices(): AppResult<List<com.bluemix.clients_lead.domain.model.ClientService>> =
        withContext(Dispatchers.IO) {
            runAppCatching(mapper = { it.toAppError() }) {
                Log.d("CLIENT_REPO", "📡 Fetching all client services...")
                val response = httpClient.get("${ApiEndpoints.Services.BASE}/all").body<ClientServicesResponse>()
                
                response.services.map { it.toDomain() }
            }
        }
    
    override suspend fun addClientService(
        name: String,
        clientId: String?,
        center: String?,
        agentId: String?,
        status: String?,
        startDate: String?,
        expiryDate: String?,
        price: String?
    ): AppResult<Unit> = withContext(Dispatchers.IO) {
        runAppCatching(mapper = { it.toAppError() }) {
            if (clientId == null) {
                throw Exception("Client ID is required")
            }
            Log.d("CLIENT_REPO", "🔨 Adding client service: $name for client: $clientId")
            val payload = mapOf(
                "serviceName" to name,
                "description" to center,
                "status" to (status ?: "active"),
                "startDate" to startDate,
                "expiryDate" to expiryDate,
                "price" to price
            )
            httpClient.post(ApiEndpoints.Services.clientServices(clientId)) {
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(payload)
            }
            Unit
        }
    }

    override suspend fun uploadExcelFile(file: ByteArray, token: String): Boolean {
        return try {
            httpClient.submitFormWithBinaryData(
                url = ApiEndpoints.Clients.UPLOAD_EXCEL,
                formData = formData {
                    append(
                        "file",
                        file,
                        Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=clients.xlsx")
                            append(
                                HttpHeaders.ContentType,
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            )
                        }
                    )
                }
            ) {
                header("Authorization", "Bearer $token")
            }.status.value in 200..299
        } catch (e: Exception) {
            println("Upload failed: ${e.message}")
            false
        }
    }

    override suspend fun getDashboardStats(): AppResult<com.bluemix.clients_lead.domain.model.DashboardStats> = 
        withContext(Dispatchers.IO) {
            runAppCatching(mapper = { it.toAppError() }) {
                Log.d("CLIENT_REPO", "📡 Fetching dashboard stats...")
                val response = httpClient.get(ApiEndpoints.Admin.DASHBOARD_STATS).body<DashboardStatsDto>()
                
                com.bluemix.clients_lead.domain.model.DashboardStats(
                    activeAgents = response.activeAgents,
                    totalClients = response.totalClients,
                    gpsVerified = response.gpsVerified,
                    coverage = response.coverage,
                    hiddenClients = response.hiddenClients
                )
            }
        }

    override suspend fun retryGeocoding(): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            runAppCatching(mapper = { it.toAppError() }) {
                Log.d("CLIENT_REPO", "🔄 Requesting geocoding retry...")
                httpClient.post(ApiEndpoints.Clients.RETRY_GEOCODING)
                Unit
            }
        }

    override suspend fun selfHealClients(): AppResult<SelfHealResult> =
        withContext(Dispatchers.IO) {
            runAppCatching(mapper = { it.toAppError() }) {
                Log.d("CLIENT_REPO", "🏥 Starting Self-Heal Database...")
                val response = httpClient.post(ApiEndpoints.Clients.SELF_HEAL_CLIENTS).body<SelfHealResponse>()
                Log.d("CLIENT_REPO", "✅ Self-Heal complete: ${response.healedByLogs} logs, ${response.healedByApi} API, ${response.skipped} skipped, ${response.failed} failed")
                SelfHealResult(
                    total = response.total,
                    healedByLogs = response.healedByLogs,
                    healedByApi = response.healedByApi,
                    skipped = response.skipped,
                    failed = response.failed
                )
            }
        }

    // ========== Phase 1: Agent tags GPS location ==========
    override suspend fun tagClientLocation(
        clientId: String,
        latitude: Double,
        longitude: Double,
        source: String
    ): AppResult<TagLocationResponse> = withContext(Dispatchers.IO) {
        runAppCatching(mapper = { it.toAppError() }) {
            Log.d("CLIENT_REPO", "📍 Tagging location for client: $clientId ($latitude, $longitude)")
            
            val request = TagLocationRequest(
                latitude = latitude,
                longitude = longitude,
                source = source
            )
            
            val response = httpClient.post(ApiEndpoints.Clients.tagLocation(clientId)) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body<TagLocationResponseDto>()
            
            TagLocationResponse(
                success = response.success,
                message = response.message
            )
        }
    }

    // ========== Admin sets location ==========
    override suspend fun setClientLocation(
        clientId: String,
        latitude: Double,
        longitude: Double,
        source: String
    ): AppResult<Client> = withContext(Dispatchers.IO) {
        runAppCatching(mapper = { it.toAppError() }) {
            Log.d("CLIENT_REPO", "📍 Admin setting location for client: $clientId")
            
            val request = SetLocationRequest(
                latitude = latitude,
                longitude = longitude,
                source = source
            )
            
            val response = httpClient.post(ApiEndpoints.Admin.setClientLocation(clientId)) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body<SetLocationResponse>()
            
            response.client.toClientDto().toDomain()
        }
    }

    // ========== Missing Locations Report ==========
    override suspend fun getMissingLocations(): AppResult<MissingLocationsResult> = withContext(Dispatchers.IO) {
        runAppCatching(mapper = { it.toAppError() }) {
            Log.d("CLIENT_REPO", "📋 Fetching missing locations report...")
            val response = httpClient.get(ApiEndpoints.Admin.MISSING_LOCATIONS).body<MissingLocationsResponseDto>()
            
            MissingLocationsResult(
                totalMissing = response.totalMissing,
                breakdown = MissingBreakdown(
                    needsVerification = response.breakdown.needsVerification,
                    completelyMissing = response.breakdown.completelyMissing,
                    agentVisitRecommended = response.breakdown.agentVisitRecommended
                ),
                clients = response.clients.map { dto ->
                    MissingClient(
                        id = dto.id,
                        name = dto.name,
                        address = dto.address,
                        phone = dto.phone,
                        status = dto.status,
                        locationAccuracy = dto.locationAccuracy,
                        locationSource = dto.locationSource,
                        recommendedMethod = dto.recommendedMethod,
                        recommendationReason = dto.recommendationReason,
                        priority = dto.priority
                    )
                }
            )
        }
    }

    // ========== Location Report ==========
    override suspend fun getLocationReport(): AppResult<LocationReport> = withContext(Dispatchers.IO) {
        runAppCatching(mapper = { it.toAppError() }) {
            Log.d("CLIENT_REPO", "📊 Fetching location report...")
            val response = httpClient.get(ApiEndpoints.Admin.LOCATION_REPORT).body<LocationReportDto>()
            
            LocationReport(
                total = response.total,
                withGps = response.with_gps,
                missingGps = response.missing_gps,
                bySource = response.by_source
            )
        }
    }
}

// ==================== Response Models ====================

@Serializable
data class DashboardStatsDto(
    val activeAgents: Int,
    val totalClients: Int,
    val gpsVerified: Int,
    val coverage: Int,
    val hiddenClients: Int = 0
)

@Serializable
data class SelfHealResponse(
    val total: Int,
    val healedByLogs: Int,
    val healedByApi: Int,
    val skipped: Int,
    val failed: Int
)

@Serializable
data class DailySummaryDto(
    val activeAgents: Int,
    val idleAgents: Int,
    val totalMeetings: Int,
    val verifiedMeetings: Int,
    val totalDistance: Double,
    val alertsCount: Int = 0
)

@Serializable
data class ClientsResponse(
    val clients: List<BackendClient>,
    val pagination: PaginationData? = null,
    val userPincode: String? = null,
    val filteredByPincode: Boolean? = null,
    val message: String? = null
)

@Serializable
data class SingleClientResponse(
    val client: BackendClient
)

@Serializable
data class BackendClient(
    val id: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val pincode: String? = null,
    val status: String? = null,
    val notes: String? = null,
    val createdBy: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val lastVisitDate: String? = null,
    val lastVisitType: String? = null,
    val lastVisitNotes: String? = null,
    @SerialName("location_accuracy")
    val locationAccuracy: String? = null,
    @SerialName("location_source")
    val locationSource: String? = null
)

@Serializable
data class PaginationData(
    val page: Int,
    val limit: Int,
    val total: Int,
    val totalPages: Int
)

// ==================== Mapping Functions ====================

fun BackendClient.toClientDto(): ClientDto {
    return ClientDto(
        id = this.id,
        name = this.name,
        email = this.email,
        phone = this.phone,
        address = this.address,
        latitude = this.latitude,
        longitude = this.longitude,
        pincode = this.pincode,
        status = this.status ?: "active",
        notes = this.notes,
        createdBy = this.createdBy ?: "",
        createdAt = this.createdAt ?: "",
        updatedAt = this.updatedAt ?: "",
        hasLocation = (this.latitude != null && this.longitude != null),
        lastVisitType = this.lastVisitType,
        lastVisitDate = this.lastVisitDate,
        lastVisitNotes = this.lastVisitNotes,
        locationAccuracy = this.locationAccuracy,
        locationSource = this.locationSource
    )
}

@Serializable
data class UpdateAddressRequest(
    val address: String
)

@Serializable
data class UpdateLocationRequest(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double? = null
)

@Serializable
data class TeamLocationsResponse(val agents: List<AgentLocationDto>)

@Serializable
data class AgentLocationDto(
    val id: String, 
    val email: String, 
    val fullName: String? = null, 
    val latitude: Double? = null, 
    val longitude: Double? = null, 
    val accuracy: Double? = null, 
    val timestamp: String? = null, 
    val activity: String? = null, 
    val battery: Int? = null,
    val battery_level: Int? = null, // Sync from backend camelCase issue? Backend sends battery_level
    val smart_status: String? = null,
    val visit_count: Int? = null,
    val isActive: Boolean? = null,
    val markNotes: String? = null // S7: Used to parse structured context (client name, transport mode)
)

fun AgentLocationDto.toDomain(): com.bluemix.clients_lead.domain.repository.AgentLocation {
    val (clientName, transportMode, currentActivity) = com.bluemix.clients_lead.domain.repository.AgentLocation.parseContext(activity, markNotes)
    return com.bluemix.clients_lead.domain.repository.AgentLocation(
        id = id,
        email = email,
        fullName = fullName ?: email.substringBefore("@"), // Fallback to email prefix
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        timestamp = timestamp ?: System.currentTimeMillis().toString(),
        activity = activity ?: "Active",
        battery = battery ?: battery_level ?: 0,
        smartStatus = smart_status ?: activity ?: "Active",
        visitCount = visit_count ?: 0,
        isActive = isActive ?: true,
        currentClientName = clientName,
        transportMode = transportMode,
        currentActivity = currentActivity ?: activity ?: "Active"
    )
}

@Serializable
data class ClientServicesResponse(val services: List<ClientServiceDto>)

@Serializable
data class ClientServiceDto(
    val id: String,
    val name: String,
    val clientName: String,
    val clientEmail: String? = null,
    val status: String,
    val price: String? = null,
    val startDate: String,
    val expiryDate: String,
    val daysLeft: Int
)

fun ClientServiceDto.toDomain() = com.bluemix.clients_lead.domain.model.ClientService(
    id = id,
    name = name,
    clientName = clientName,
    clientEmail = clientEmail,
    status = status,
    price = price,
    startDate = startDate,
    expiryDate = expiryDate,
    daysLeft = daysLeft
)

// ========== Phase 1: Tag Location ==========
@Serializable
data class TagLocationRequest(
    val latitude: Double,
    val longitude: Double,
    val source: String = "AGENT"
)

@Serializable
data class TagLocationResponseDto(
    val success: Boolean,
    val message: String
)

// ========== Phase 3: Landmark Search ==========
@Serializable
data class LandmarkSearchResponse(
    val results: List<LandmarkDto>,
    val cached: Boolean = false
)

@Serializable
data class LandmarkDto(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

// ========== Phase 2: Admin Set Location ==========
@Serializable
data class SetLocationRequest(
    val latitude: Double,
    val longitude: Double,
    val source: String = "ADMIN"
)

@Serializable
data class SetLocationResponse(
    val success: Boolean,
    val client: BackendClient
)

// ========== Missing Locations Response ==========
@Serializable
data class MissingLocationsResponseDto(
    val totalMissing: Int,
    val breakdown: MissingBreakdownDto,
    val clients: List<MissingClientDto>
)

@Serializable
data class MissingBreakdownDto(
    val needsVerification: Int,
    val completelyMissing: Int,
    val agentVisitRecommended: Int
)

@Serializable
data class MissingClientDto(
    val id: String,
    val name: String,
    val address: String? = null,
    val phone: String? = null,
    val status: String? = null,
    @SerialName("location_accuracy")
    val locationAccuracy: String? = null,
    @SerialName("location_source")
    val locationSource: String? = null,
    @SerialName("recommended_method")
    val recommendedMethod: String,
    @SerialName("recommendation_reason")
    val recommendationReason: String,
    val priority: Int
)

// ========== Location Report ==========
@Serializable
data class LocationReportDto(
    val total: Int,
    val with_gps: Int,
    val missing_gps: Int,
    val by_source: Map<String, Int>
)
