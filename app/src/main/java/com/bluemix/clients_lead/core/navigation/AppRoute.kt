package com.bluemix.clients_lead.core.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes using Kotlin Serialization.
 * Requires: implementation(libs.plugins.kotlin.serialization)
 */
@Serializable
sealed class Route {
    @Serializable
    data object Gate : Route()

    @Serializable
    data object Auth : Route()

    @Serializable
    data object Map : Route()

    @Serializable
    data object Clients : Route()

    @Serializable
    data object Activity : Route()

    @Serializable
    data object Profile : Route()

    @Serializable
    data class ClientDetail(val clientId: String) : Route()

    // NEW SCREEN FOR EXPENSE FORM
    @Serializable
    data object ExpenseForm : Route()

    @Serializable
    data object MultiLegExpenseForm : Route()


    @Serializable
    data object CreateClient : Route()

    // ADMIN DASHBOARD ROUTES
    @Serializable
    data object AdminDashboard : Route()
    
    @Serializable
    data class AdminJourneyReports(val agentId: String? = null) : Route()

    @Serializable
    data object AdminClientServices : Route()

    @Serializable
    data object AdminTeamActivity : Route()

    @Serializable
    data object AdminUserManagement : Route()

    @Serializable
    data object AdminAddService : Route()

    @Serializable
    data object AdminBankAccount : Route()

    @Serializable
    data object AdminSlotExpansion : Route()

    @Serializable
    data object AdminPinClients : Route()

    @Serializable
    data object AdminPlanUsage : Route()
    
    @Serializable
    data class AdminAgentDetail(val agentId: String) : Route()

    @Serializable
    data object AdminMeetingLogs : Route()

    @Serializable
    data object MissingClients : Route()
}
