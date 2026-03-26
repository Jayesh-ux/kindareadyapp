package com.bluemix.clients_lead.domain.model

data class SlotOrder(
    val id: Int? = null,
    val companyId: String,
    val userSlots: Int,
    val clientSlots: Int,
    val totalAmount: Double,
    val payerName: String?,
    val status: String = "completed"
)
