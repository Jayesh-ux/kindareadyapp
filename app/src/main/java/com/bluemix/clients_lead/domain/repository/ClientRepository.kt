package com.bluemix.clients_lead.domain.repository

import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.model.Client
import com.bluemix.clients_lead.domain.model.DailySummary

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
     * ✅ NEW: Direct coordinate update for Phase 1 (GPS Tagging)
     */
    suspend fun updateClientLocation(
        clientId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Double? = null
    ): AppResult<Client>

    /**
     * Get current locations of all team members (Admins only)
     */
    suspend fun getTeamLocations(): AppResult<List<AgentLocation>>
    
    /**
     * Get services for a specific client
     */
    suspend fun getClientServices(clientId: String): AppResult<List<com.bluemix.clients_lead.domain.model.ClientService>>
    
    /**
     * Get all client services across all clients (Admin only)
     */
    suspend fun getAllClientServices(): AppResult<List<com.bluemix.clients_lead.domain.model.ClientService>>
    
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

    /**
     * ✅ NEW: Get real-time status of all agents (Smart Status, Battery, Visits)
     */
    suspend fun getLiveAgents(): AppResult<List<AgentLocation>>

    /**
     * ✅ NEW: Get daily performance summary for the company
     */
    suspend fun getDailySummary(): AppResult<DailySummary>

    /**
     * ✅ NEW: Trigger background geocoding for all clients with missing coordinates
     */
    suspend fun retryGeocoding(): AppResult<Unit>

    /**
     * Self-Heal Database: Recovery System for clients with missing GPS coordinates
     * Falls back from agent logs -> Google Geocoding API
     */
    suspend fun selfHealClients(): AppResult<SelfHealResult>

    /**
     * Phase 1: Agent tags client location with their current GPS
     */
    suspend fun tagClientLocation(
        clientId: String,
        latitude: Double,
        longitude: Double,
        source: String = "AGENT"
    ): AppResult<TagLocationResponse>

    /**
     * Phase 2 (Admin): Set client location manually
     */
    suspend fun setClientLocation(
        clientId: String,
        latitude: Double,
        longitude: Double,
        source: String = "ADMIN"
    ): AppResult<Client>

    /**
     * Admin: Get missing locations report
     */
    suspend fun getMissingLocations(): AppResult<MissingLocationsResult>

    /**
     * Admin: Get location status report
     */
    suspend fun getLocationReport(): AppResult<LocationReport>
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
    val smartStatus: String? = null,
    val visitCount: Int = 0,
    val isActive: Boolean = true,
    // S7: Enriched fields — derived from markNotes / activity strings
    val currentClientName: String? = null,
    val transportMode: String? = null,
    val currentActivity: String? = null  // Friendly label e.g. "Traveling to Acme Corp"
) {
    companion object {
        /** Parse structured context from free-text markNotes like "Heading to Acme Corp via Bike" */
        fun parseContext(activity: String?, markNotes: String?): Triple<String?, String?, String?> {
            if (markNotes.isNullOrBlank()) return Triple(null, null, activity)
            // "Heading to [Client] via [Mode]"
            val headingRegex = Regex("Heading to (.+?) via (.+)", RegexOption.IGNORE_CASE)
            headingRegex.find(markNotes)?.let {
                return Triple(it.groupValues[1].trim(), it.groupValues[2].trim(), "Traveling to ${it.groupValues[1].trim()}")
            }
            // "At [Client] site"
            val atSiteRegex = Regex("At (.+?) site", RegexOption.IGNORE_CASE)
            atSiteRegex.find(markNotes)?.let {
                return Triple(it.groupValues[1].trim(), null, "At ${it.groupValues[1].trim()} site")
            }
            // "Agent started journey to [Client] via [Mode]"
            val journeyRegex = Regex("journey to (.+?) via (.+)", RegexOption.IGNORE_CASE)
            journeyRegex.find(markNotes)?.let {
                return Triple(it.groupValues[1].trim(), it.groupValues[2].trim(), "Started journey to ${it.groupValues[1].trim()}")
            }
            return Triple(null, null, activity)
        }
    }
}

/**
 * Encapsulates client selection for Map view along with potential status messages
 */
data class ClientsResult(
    val clients: List<Client>,
    val message: String? = null
)

/**
 * Result from Self-Heal Database operation
 */
data class SelfHealResult(
    val total: Int,
    val healedByLogs: Int,
    val healedByApi: Int,
    val skipped: Int,
    val failed: Int
)

/**
 * Location report for admin dashboard
 */
data class LocationReport(
    val total: Int,
    val withGps: Int,
    val missingGps: Int,
    val bySource: Map<String, Int>
)

/**
 * Tag location response
 */
data class TagLocationResponse(
    val success: Boolean,
    val message: String
)

/**
 * Missing client data
 */
data class MissingClient(
    val id: String,
    val name: String,
    val address: String?,
    val phone: String?,
    val status: String?,
    val locationAccuracy: String?,
    val locationSource: String?,
    val recommendedMethod: String,
    val recommendationReason: String,
    val priority: Int
)

/**
 * Missing locations result
 */
data class MissingLocationsResult(
    val totalMissing: Int,
    val breakdown: MissingBreakdown,
    val clients: List<MissingClient>
)

data class MissingBreakdown(
    val needsVerification: Int,
    val completelyMissing: Int,
    val agentVisitRecommended: Int
)