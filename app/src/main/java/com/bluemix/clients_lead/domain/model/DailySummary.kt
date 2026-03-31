package com.bluemix.clients_lead.domain.model

data class DailySummary(
    val activeAgents: Int,
    val idleAgents: Int,
    val totalMeetings: Int,
    val verifiedMeetings: Int,
    val totalDistance: Double,
    val alertsCount: Int = 0
)
