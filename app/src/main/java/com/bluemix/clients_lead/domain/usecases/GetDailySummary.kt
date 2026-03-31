package com.bluemix.clients_lead.domain.usecases

import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.model.DailySummary
import com.bluemix.clients_lead.domain.repository.IClientRepository

class GetDailySummary(
    private val repository: IClientRepository
) {
    suspend operator fun invoke(): AppResult<DailySummary> =
        repository.getDailySummary()
}
