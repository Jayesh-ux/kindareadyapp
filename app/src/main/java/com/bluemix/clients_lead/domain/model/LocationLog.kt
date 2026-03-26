package com.bluemix.clients_lead.domain.model

data class LocationLog(
    val id: String,
    val userId: String,
    val userEmail: String? = null, // Added for admin clarity
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double?,
    val timestamp: String,
    val createdAt: String,
    val battery: Int?,
    val markActivity: String? = null,
    val markNotes: String? = null,
    val clientId: String? = null,
    // S12: Structured field parsed from markNotes — avoids backend schema change
    val clientName: String? = null
) {
    companion object {
        /** Extract client name from notes like "Heading to Acme Corp via Bike" */
        fun parseClientName(markNotes: String?): String? {
            if (markNotes.isNullOrBlank()) return null
            val regex = Regex(
                "(?:Heading to|At|journey to|ended journey to) (.+?)(?:\\s+via|\\s+site|ended|$)",
                RegexOption.IGNORE_CASE
            )
            return regex.find(markNotes)?.groupValues?.getOrNull(1)?.trim()
        }
    }
}