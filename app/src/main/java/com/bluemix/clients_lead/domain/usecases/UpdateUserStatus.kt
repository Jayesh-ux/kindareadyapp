package com.bluemix.clients_lead.domain.usecases

import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.repository.IClientRepository

class UpdateUserStatus(
    private val repository: IClientRepository
) {
    suspend operator fun invoke(userId: String, isActive: Boolean): AppResult<Unit> =
        repository.updateUserStatus(userId, isActive)
}
