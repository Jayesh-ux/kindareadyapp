package com.bluemix.clients_lead.domain.usecases

import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.model.ClientService
import com.bluemix.clients_lead.domain.repository.IClientRepository

class GetClientServices(
    private val repository: IClientRepository
) {
    suspend operator fun invoke(): AppResult<List<ClientService>> =
        repository.getClientServices()
}
