package com.bluemix.clients_lead.domain.model

data class DashboardStats(
    val activeAgents: Int,
    val totalClients: Int,
    val gpsVerified: Int, // Percentage
    val coverage: Int // Percentage
)
