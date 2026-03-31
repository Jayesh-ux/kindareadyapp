package com.bluemix.clients_lead.domain.usecases

import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.repository.AgentLocation
import com.bluemix.clients_lead.domain.repository.IClientRepository

class GetLiveAgents(
    private val repository: IClientRepository
) {
    suspend operator fun invoke(): AppResult<List<AgentLocation>> =
        repository.getLiveAgents()
}
