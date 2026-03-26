package com.bluemix.clients_lead.domain.model

data class ClientService(
    val id: String,
    val name: String,
    val clientName: String,
    val clientEmail: String?,
    val status: String, // "active", "expiring", "expired"
    val price: String?,
    val startDate: String,
    val expiryDate: String,
    val daysLeft: Int
)
