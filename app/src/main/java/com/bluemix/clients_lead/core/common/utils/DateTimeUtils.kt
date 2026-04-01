package com.bluemix.clients_lead.core.common.utils

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object DateTimeUtils {
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

    fun isRecent(timestamp: String?): Boolean {
        val date = parseDate(timestamp) ?: return false
        val now = System.currentTimeMillis()
        val diff = Math.abs(now - date.time)
        return diff < TimeUnit.MINUTES.toMillis(10)
    }

    fun isToday(timestamp: String?): Boolean {
        if (timestamp == null) return false
        val date = parseDate(timestamp) ?: return false
        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance()
        cal1.time = date
        cal2.time = Date()
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    fun formatLastSeen(timestamp: String?): String {
        val date = parseDate(timestamp) ?: return "Never"
        val now = System.currentTimeMillis()
        val diff = now - date.time
        val absDiff = Math.abs(diff)
        if (diff < -60000) return "Just now"
        return when {
            absDiff < 60000 -> "Just now"
            absDiff < 3600000 -> "${absDiff / 60000}m ago"
            absDiff < 86400000 -> "${absDiff / 3600000}h ago"
            absDiff < 604800000 -> "${absDiff / 86400000}d ago"
            else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(date)
        }
    }

    fun formatTime(timestamp: String?): String {
        val date = parseDate(timestamp) ?: return ""
        return try {
            SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(date)
        } catch (e: Exception) {
            timestamp ?: ""
        }
    }

    fun formatTimeOnly(timestamp: String?): String {
        val date = parseDate(timestamp) ?: return ""
        return try {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
        } catch (e: Exception) {
            timestamp ?: ""
        }
    }
}
