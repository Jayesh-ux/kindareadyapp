package com.bluemix.clients_lead.domain.usecases

import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.core.common.utils.AppError
import com.bluemix.clients_lead.domain.model.ActiveTrip
import com.bluemix.clients_lead.domain.repository.ExpenseRepository
import com.bluemix.clients_lead.core.common.extensions.toAppError
import com.bluemix.clients_lead.core.common.extensions.runAppCatching
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import com.bluemix.clients_lead.core.network.ApiEndpoints
import kotlinx.serialization.Serializable

class StartTrip(
    private val httpClient: HttpClient
) {
    suspend operator fun invoke(agentId: String, expenseId: String): AppResult<ActiveTrip> =
        runAppCatching(mapper = { it.toAppError() }) {
            val response = httpClient.post(ApiEndpoints.Expenses.startTrip(agentId)) {
                setBody(StartTripRequest(expenseId = expenseId))
            }.body<StartTripResponse>()
            
            response.trip
        }
}

@Serializable
private data class StartTripRequest(
    val expenseId: String
)

@Serializable
private data class StartTripResponse(
    val trip: ActiveTrip
)