package com.bluemix.clients_lead.domain.usecases

import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.model.Client
import com.bluemix.clients_lead.domain.repository.IClientRepository

class GetAllClients(
    private val repository: IClientRepository
) {
    suspend operator fun invoke(userId: String, page: Int = 1, limit: Int = 50): AppResult<List<Client>> =
        repository.getAllClients(userId, page, limit)
}

class GetClientsByStatus(
    private val repository: IClientRepository
) {
    suspend operator fun invoke(userId: String, status: String, page: Int = 1, limit: Int = 50): AppResult<List<Client>> =
        repository.getClientsByStatus(userId, status, page, limit)
}

class GetClientById(
    private val repository: IClientRepository
) {
    suspend operator fun invoke(clientId: String): AppResult<Client> =
        repository.getClientById(clientId)
}

class GetClientsWithLocation(
    private val repository: IClientRepository
) {
    suspend operator fun invoke(userId: String, isAdmin: Boolean = false): AppResult<com.bluemix.clients_lead.domain.repository.ClientsResult> =
        repository.getClientsWithLocation(userId, isAdmin)
}

class SearchClients(
    private val repository: IClientRepository
) {
    suspend operator fun invoke(userId: String, query: String): AppResult<List<Client>> =
        repository.searchClients(userId, query)
}

// ✅ NEW: Remote search use case with filters
class SearchRemoteClients(
    private val repository: IClientRepository
) {
    suspend operator fun invoke(
        userId: String,
        query: String,
        filterType: String? = null,
        filterValue: String? = null
    ): AppResult<List<Client>> =
        repository.searchRemoteClients(userId, query, filterType, filterValue)
}

class CreateClient(
    private val repository: IClientRepository
) {
    suspend operator fun invoke(
        name: String,
        phone: String?,
        email: String?,
        address: String?,
        pincode: String?,
        notes: String?,
        latitude: Double? = null,
        longitude: Double? = null
    ): AppResult<Client> = repository.createClient(
        name = name,
        phone = phone,
        email = email,
        address = address,
        pincode = pincode,
        notes = notes,
        latitude = latitude,
        longitude = longitude
    )
}