package com.bluemix.clients_lead.core.common.utils

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object DateTimeUtils {
    /**
     * Parse ISO 8601 timestamp with fallback for milliseconds
     */
    fun parseDate(timestamp: String?): Date? {
        if (timestamp.isNullOrBlank()) return null
        
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        )

        for (pattern in formats) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US)
                if (pattern.contains("'Z'") || pattern.contains("T")) {
                    format.timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = format.parse(timestamp)
                if (date != null) return date
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }

    /**
     * Checks if a timestamp is within the last 5 minutes (for Online status)
     */
    fun isRecent(timestamp: String?): Boolean {
        val date = parseDate(timestamp) ?: return false
        val now = System.currentTimeMillis()
        val diff = Math.abs(now - date.time)
        return diff < TimeUnit.MINUTES.toMillis(3) // Reduced from 10 to 3 for better real-time accuracy
    }

    /**
     * Returns a human readable "Last seen" string
     */
    fun formatLastSeen(timestamp: String?): String {
        val date = parseDate(timestamp) ?: return "Never"
        val now = System.currentTimeMillis()
        val diff = now - date.time
        
        val absDiff = Math.abs(diff)
        
        if (diff < -TimeUnit.MINUTES.toMillis(1)) return "Just now"

        return when {
            absDiff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
            absDiff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(absDiff)}m ago"
            absDiff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(absDiff)}h ago"
            absDiff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(absDiff)}d ago"
            else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(date)
        }
    }

    /**
     * Format timestamp to readable time string
     */
    fun formatTime(timestamp: String?): String {
        val date = parseDate(timestamp) ?: return ""
        return try {
            val outputFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
            outputFormat.format(date)
        } catch (e: Exception) {
            timestamp ?: ""
        }
    }

    /**
     * Format timestamp to readable time string (HH:mm AM/PM)
     */
    fun formatTimeOnly(timestamp: String?): String {
        val date = parseDate(timestamp) ?: return ""
        return try {
            val outputFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            outputFormat.format(date)
        } catch (e: Exception) {
            timestamp ?: ""
        }
    }
}
