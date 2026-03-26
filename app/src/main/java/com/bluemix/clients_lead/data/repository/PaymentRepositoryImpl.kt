package com.bluemix.clients_lead.data.repository

import com.bluemix.clients_lead.core.common.extensions.runAppCatching
import com.bluemix.clients_lead.core.common.extensions.toAppError
import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.core.network.ApiEndpoints
import com.bluemix.clients_lead.domain.repository.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

class PaymentRepositoryImpl(
    private val httpClient: HttpClient
) : PaymentRepository {

    override suspend fun getUserBankAccount(userId: String): AppResult<BankAccount?> =
        runAppCatching(mapper = { it.toAppError() }) {
            val response = httpClient.get(ApiEndpoints.Payments.userBankAccount(userId)).body<BankAccountResponse>()
            response.bankAccount
        }

    override suspend fun updateUserBankAccount(userId: String, bankAccount: BankAccount): AppResult<Unit> =
        runAppCatching(mapper = { it.toAppError() }) {
            httpClient.post(ApiEndpoints.Payments.updateUserBankAccount(userId)) {
                setBody(bankAccount)
            }
            Unit
        }

    override suspend fun getMyPlan(): AppResult<PlanData> =
        runAppCatching(mapper = { it.toAppError() }) {
            httpClient.get(ApiEndpoints.Plans.MY_PLAN).body<PlanData>()
        }

    override suspend fun purchaseSlots(additionalUsers: Int, additionalClients: Int, totalAmount: Int): AppResult<Unit> =
        runAppCatching(mapper = { it.toAppError() }) {
            httpClient.post(ApiEndpoints.Plans.PURCHASE_SLOTS) {
                setBody(PurchaseSlotsRequest(additionalUsers, additionalClients, totalAmount))
            }
            Unit
        }
}

@Serializable
data class PurchaseSlotsRequest(
    val additionalUsers: Int,
    val additionalClients: Int,
    val totalAmount: Int
)
