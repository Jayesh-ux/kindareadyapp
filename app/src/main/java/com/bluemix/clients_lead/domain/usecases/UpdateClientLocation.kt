package com.bluemix.clients_lead.domain.usecases

import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.model.Client
import com.bluemix.clients_lead.domain.repository.IClientRepository

class UpdateClientLocation(
    private val clientRepository: IClientRepository
) {
    suspend operator fun invoke(
        clientId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Double? = null
    ): AppResult<Client> {
        return clientRepository.updateClientLocation(clientId, latitude, longitude, accuracy)
    }
}
