package com.bluemix.clients_lead.domain.model

data class UserBankAccount(
    val id: Int? = null,
    val userId: String,
    val companyId: String? = null,
    val holderName: String?,
    val accountNumber: String?,
    val ifscCode: String?,
    val bankName: String?,
    val upiId: String?,
    val updatedAt: String? = null
)
