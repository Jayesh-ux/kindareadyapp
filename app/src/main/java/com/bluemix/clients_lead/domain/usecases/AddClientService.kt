package com.bluemix.clients_lead.domain.usecases

import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.repository.IClientRepository

class AddClientService(
    private val repository: IClientRepository
) {
    suspend operator fun invoke(
        name: String,
        clientId: String? = null,
        center: String? = null,
        agentId: String? = null,
        status: String? = "active",
        startDate: String? = null,
        expiryDate: String? = null,
        price: String? = null
    ): AppResult<Unit> = repository.addClientService(
        name = name,
        clientId = clientId,
        center = center,
        agentId = agentId,
        status = status,
        startDate = startDate,
        expiryDate = expiryDate,
        price = price
    )
}
