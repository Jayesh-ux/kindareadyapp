package com.bluemix.clients_lead.domain.repository

import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.model.Client

/**
 * Repository interface for client data operations.
 * Uses AppResult for consistent error handling across the app.
 */
interface IClientRepository {

    /**
     * Get all clients for a user with pagination
     */
    suspend fun getAllClients(userId: String, page: Int = 1, limit: Int = 50): AppResult<List<Client>>

    /**
     * Get clients by status with pagination
     */
    suspend fun getClientsByStatus(
        userId: String,
        status: String,
        page: Int = 1,
        limit: Int = 50
    ): AppResult<List<Client>>

    /**
     * Get clients with location (for map display).
     * @param isAdmin if true, fetches ALL company clients (remote mode).
     *                if false, fetches only clients in the agent's territory (local mode).
     */
    suspend fun getClientsWithLocation(userId: String, isAdmin: Boolean = false): AppResult<ClientsResult>

    /**
     * Get single client by ID
     */
    suspend fun getClientById(clientId: String): AppResult<Client>

    /**
     * Search clients by name (local search within user's pincode)
     */
    suspend fun searchClients(
        userId: String,
        query: String
    ): AppResult<List<Client>>

    /**
     * ✅ NEW: Search clients across ALL pincodes (remote search)
     * @param query Search term for name/email/phone
     * @param filterType Type of filter: "pincode", "city", "state", or null
     * @param filterValue Value to filter by (e.g., "400001", "Mumbai", "Maharashtra")
     */
    suspend fun searchRemoteClients(
        userId: String,
        query: String,
        filterType: String? = null,
        filterValue: String? = null
    ): AppResult<List<Client>>


    suspend fun createClient(
        name: String,
        phone: String?,
        email: String?,
        address: String?,
        pincode: String?,
        notes: String?,
        latitude: Double? = null,
        longitude: Double? = null
    ): AppResult<Client>

    suspend fun updateClientAddress(
        clientId: String,
        newAddress: String
    ): AppResult<Client>

    /**
     * Get current locations of all team members (Admins only)
     */
    suspend fun getTeamLocations(): AppResult<List<AgentLocation>>
    
    /**
     * Get all client services (Subscription/Maintenance data)
     */
    suspend fun getClientServices(): AppResult<List<com.bluemix.clients_lead.domain.model.ClientService>>
    
    /**
     * Add a new client service with optional assignments
     */
    suspend fun addClientService(
        name: String,
        clientId: String? = null,
        center: String? = null,
        agentId: String? = null,
        status: String? = "active",
        startDate: String? = null,
        expiryDate: String? = null,
        price: String? = null
    ): AppResult<Unit>

    /**
     * Update agent active status (Admins only)
     */
    suspend fun updateUserStatus(userId: String, isActive: Boolean): AppResult<Unit>

    /**
     * Update client service status (Acceptance/Completion)
     */
    suspend fun updateClientServiceStatus(serviceId: String, status: String): AppResult<Unit>

    /**
     * Upload Excel file with clients
     */
    suspend fun uploadExcelFile(file: ByteArray, token: String): Boolean

    /**
     * Get aggregate dashboard statistics (Admins only)
     */
    suspend fun getDashboardStats(): AppResult<com.bluemix.clients_lead.domain.model.DashboardStats>
}

data class AgentLocation(
    val id: String,
    val email: String,
    val fullName: String?,
    val latitude: Double?,
    val longitude: Double?,
    val accuracy: Double?,
    val timestamp: String?,
    val activity: String?,
    val battery: Int?,
    val isActive: Boolean = true
)

/**
 * Encapsulates client selection for Map view along with potential status messages
 */
data class ClientsResult(
    val clients: List<Client>,
    val message: String? = null
)