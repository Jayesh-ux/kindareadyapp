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
import io.ktor.client.*
import io.ktor.client.request.forms.formData
import io.ktor.client.call.*
import io.ktor.http.Headers
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import android.util.Log
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.ContentType

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
                    if (isAdmin) {
                        parameter("searchMode", "remote")
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
                val response = httpClient.get(ApiEndpoints.Clients.byId(clientId))
                    .body<SingleClientResponse>()

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
                httpClient.patch("${ApiEndpoints.Admin.CLIENT_SERVICES}/$serviceId/status") {
                    contentType(io.ktor.http.ContentType.Application.Json)
                    setBody(mapOf("status" to status))
                }
                Unit
            }
        }

    override suspend fun getClientServices(): AppResult<List<com.bluemix.clients_lead.domain.model.ClientService>> =
        withContext(Dispatchers.IO) {
            runAppCatching(mapper = { it.toAppError() }) {
                Log.d("CLIENT_REPO", "📡 Fetching client services...")
                val response = httpClient.get(ApiEndpoints.Admin.CLIENT_SERVICES).body<ClientServicesResponse>()
                
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
            Log.d("CLIENT_REPO", "🔨 Adding client service: $name")
            val payload = mapOf(
                "name" to name,
                "clientId" to clientId,
                "center" to center,
                "agentId" to agentId,
                "status" to status,
                "startDate" to startDate,
                "expiryDate" to expiryDate,
                "price" to price
            )
            httpClient.post(ApiEndpoints.Admin.CLIENT_SERVICES) {
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
                    coverage = response.coverage
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
    val coverage: Int
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
    val lastVisitNotes: String? = null
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
        lastVisitDate = this.lastVisitDate,
        lastVisitNotes = this.lastVisitNotes
    )
}

@Serializable
data class UpdateAddressRequest(
    val address: String
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
    val isActive: Boolean? = null
)

fun AgentLocationDto.toDomain() = com.bluemix.clients_lead.domain.repository.AgentLocation(
    id = id, 
    email = email, 
    fullName = fullName, 
    latitude = latitude, 
    longitude = longitude, 
    accuracy = accuracy, 
    timestamp = timestamp, 
    activity = activity, 
    battery = battery,
    isActive = isActive ?: true
)

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
