package com.bluemix.clients_lead.domain.model

data class DailySummary(
    val activeAgents: Int? = 0,
    val idleAgents: Int? = 0,
    val totalMeetings: Int? = 0,
    val verifiedMeetings: Int? = 0,
    val totalDistance: Double? = 0.0,
    val alertsCount: Int? = 0
)
