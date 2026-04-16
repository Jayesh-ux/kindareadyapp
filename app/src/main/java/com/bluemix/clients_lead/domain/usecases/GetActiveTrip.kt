package com.bluemix.clients_lead.domain.usecases

import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.model.ActiveTrip
import com.bluemix.clients_lead.domain.repository.ExpenseRepository

/**
 * Get active trip for the current agent
 * Used for app restart recovery
 */
class GetActiveTrip(
    private val repository: ExpenseRepository
) {
    suspend operator fun invoke(agentId: String): AppResult<ActiveTrip?> =
        repository.getActiveTrip(agentId)
}