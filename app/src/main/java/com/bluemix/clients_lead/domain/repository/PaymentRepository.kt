package com.bluemix.clients_lead.domain.repository

import com.bluemix.clients_lead.core.common.utils.AppResult
import kotlinx.serialization.Serializable

@Serializable
data class BankAccount(
    val account_number: String? = null,
    val ifsc_code: String? = null,
    val account_holder_name: String? = null,
    val bank_name: String? = null,
    val upi_id: String? = null
)

@Serializable
data class BankAccountResponse(
    val bankAccount: BankAccount? = null
)

@Serializable
data class PlanData(
    val plan: PlanInfo,
    val usage: PlanUsage
)

@Serializable
data class PlanInfo(
    val planName: String,
    val displayName: String,
    val priceINR: Int,
    val limits: PlanLimits,
    val features: PlanFeatures
)

@Serializable
data class PlanLimits(
    val users: ResourceLimit,
    val clients: ResourceLimit,
    val storage: StorageLimit
)

@Serializable
data class ResourceLimit(
    val max: Int? = null
)

@Serializable
data class StorageLimit(
    val maxGB: Float? = null
)

@Serializable
data class PlanFeatures(
    val services: Boolean,
    val tallySync: Boolean,
    val apiAccess: Boolean,
    val advancedAnalytics: Boolean,
    val customReports: Boolean,
    val interactiveMaps: Boolean,
    val bulkOperations: Boolean,
    val whiteLabel: Boolean
)

@Serializable
data class PlanUsage(
    val users: UsageStats,
    val clients: UsageStats,
    val services: Int,
    val meetings: Int,
    val expenses: Int,
    val locationLogs: Int,
    val storage_used_mb: Float? = null
)

@Serializable
data class UsageStats(
    val current: Int,
    val max: Int? = null,
    val unlimited: Boolean = false
)

interface PaymentRepository {
    suspend fun getUserBankAccount(userId: String): AppResult<BankAccount?>
    suspend fun updateUserBankAccount(userId: String, bankAccount: BankAccount): AppResult<Unit>
    suspend fun getMyPlan(): AppResult<PlanData>
    suspend fun purchaseSlots(additionalUsers: Int, additionalClients: Int, totalAmount: Int): AppResult<Unit>
}
