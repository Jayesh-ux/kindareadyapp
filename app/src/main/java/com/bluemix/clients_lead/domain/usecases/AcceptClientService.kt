package com.bluemix.clients_lead.domain.usecases

import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.repository.IClientRepository

/**
 * Use case for agents to accept or update a client service assignment
 */
class AcceptClientService(
    private val repository: IClientRepository
) {
    suspend operator fun invoke(serviceId: String, status: String = "active"): AppResult<Unit> =
        repository.updateClientServiceStatus(serviceId, status)
}
