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

        const val MANUAL_CREATE = "/api/manual-clients"

        fun byId(clientId: String) = "$BASE/$clientId"
        fun manualById(clientId: String) = "/manual-clients/$clientId"
        fun updateAddress(clientId: String) = "$BASE/$clientId/address"
    }

    object User {
        const val CLEAR_PINCODE = "/auth/clear-pincode"
    }

    object Admin {
        const val TEAM_LOCATIONS = "/admin/team-locations"
        const val CLIENT_SERVICES = "/admin/client-services"
        const val DASHBOARD_STATS = "/admin/stats"
        fun updateUserStatus(userId: String) = "/admin/users/$userId/status"
    }

    /**
     * Location logs endpoints
     */
    object Location {
        const val LOGS = "/location-logs"
        const val CLEAR_ALL = "/location-logs/clear-all"
    }

    /**
     * Trip Expenses endpoints
     */
    object Expenses {
        const val BASE = "/expenses"
        const val UPLOAD_RECEIPT = "/expenses/receipts"
        const val MY_EXPENSES = "$BASE/my-expenses"
        const val MY_TOTAL = "/expenses/my-total"

        fun byId(expenseId: String) = "$BASE/$expenseId"
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
     * Utility endpoints
     */
    object Utility {
        const val ROOT = "/"
        const val DB_TEST = "/dbtest"
    }
}