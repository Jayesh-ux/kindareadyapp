package com.bluemix.clients_lead.core.network

/**
 * Central API endpoint definitions
 * All backend routes are defined here for easy maintenance
 */

//https://client-backend-wm7s.onrender.com
//https://backup-server-q2dc.onrender.com
object ApiEndpoints {

    val BASE_URL = com.bluemix.clients_lead.BuildConfig.API_BASE_URL

    /**
     * Authentication endpoints
     */
    object Auth {
        const val SIGNUP = "/auth/signup"
        const val LOGIN = "/auth/login"
        const val PROFILE = "/auth/profile"
    }

    /**
     * Clients endpoints
     */
    object Clients {
        const val BASE = "/clients"
        const val UPLOAD_EXCEL = "/clients/upload-excel"
        const val RETRY_GEOCODING = "$BASE/retry-geocoding"
        const val SELF_HEAL_CLIENTS = "/admin/self-heal-clients"

        const val MANUAL_CREATE = "/api/manual-clients"

        fun byId(clientId: String) = "$BASE/$clientId"
        fun manualById(clientId: String) = "/manual-clients/$clientId"
        fun updateAddress(clientId: String) = "$BASE/$clientId/address"
        
        // Phase 1: Agent tags GPS
        fun tagLocation(clientId: String) = "$BASE/$clientId/tag-location"
    }

    /**
     * Admin endpoints for GPS
     */
    object Admin {
        // Existing
        const val TEAM_LOCATIONS = "/admin/team-locations"
        const val LIVE_AGENTS = "/admin/agents/live"
        const val DAILY_SUMMARY = "/admin/daily-summary"
        const val DASHBOARD_STATS = "/admin/stats"
        
        fun updateUserStatus(userId: String) = "/admin/users/$userId/status"
        fun agentJourney(agentId: String, date: String) = "/admin/journey/$agentId/$date"
        
        // Phase 2: Admin pins location
        fun setClientLocation(clientId: String) = "/admin/clients/$clientId/set-location"
        
        // Missing locations report
        const val MISSING_LOCATIONS = "/admin/clients/missing-locations"
        
        // Location report
        const val LOCATION_REPORT = "/admin/clients/location-report"
    }

    /**
     * Client Services endpoints
     */
    object Services {
        const val BASE = "/services"
        fun clientServices(clientId: String) = "/services/client/$clientId"
        fun byId(serviceId: String) = "/services/$serviceId"
        fun updateStatus(serviceId: String) = "/services/$serviceId/status"
    }

    /**
     * Location logs endpoints
     */
    object Location {
        const val LOGS = "/location-logs"
        const val CLEAR_ALL = "/location-logs/clear-all"
        const val TRACKING_STATE = "/location/tracking-state"
    }

    /**
     * Trip Expenses endpoints
     */
    object Expenses {
        const val BASE = "/expenses"
        const val UPLOAD_RECEIPT = "/expenses/receipts"
        const val MY_EXPENSES = "$BASE/my-expenses"
        const val MY_TOTAL = "/expenses/my-total"
        const val GET_RECEIPTS = "$BASE/receipts"
        const val LINK_RECEIPT = "$BASE/link-receipt"

        fun byId(expenseId: String) = "$BASE/$expenseId"
        
        // Active Trip APIs
        fun activeTrip(agentId: String) = "$BASE/active-trip/$agentId"
        fun startTrip(agentId: String) = "$BASE/start-trip/$agentId"
        fun completeLeg(agentId: String) = "$BASE/complete-leg/$agentId"
    }

    /**
     * Meeting endpoints
     */
    object Meetings {
        const val BASE = "/meetings"

        // Get active meeting for a specific client
        fun activeMeeting(clientId: String) = "$BASE/active/$clientId"

        // Dynamic route for single meeting
        fun byId(meetingId: String) = "$BASE/$meetingId"

        // Upload attachment to a meeting
        fun attachments(meetingId: String) = "$BASE/$meetingId/attachments"
    }

    /**
     * Payment endpoints
     */
    object Payments {
        const val BASE = "/api/payments"
        fun userBankAccount(userId: String) = "$BASE/admin/user-bank-account/$userId"
        fun updateUserBankAccount(userId: String) = "$BASE/admin/update-user-bank-account/$userId"
    }

    /**
     * Plan and Subscription endpoints
     */
    object Plans {
        const val BASE = "/api/plans"
        const val MY_PLAN = "$BASE/my-plan"
        const val PURCHASE_SLOTS = "$BASE/purchase-slots"
    }

    /**
     * Utility endpoints
     */
    object Utility {
        const val ROOT = "/"
        const val DB_TEST = "/dbtest"
    }
}