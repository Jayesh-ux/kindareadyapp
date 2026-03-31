package com.bluemix.clients_lead.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_location_logs")
data class PendingLocationLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val timestamp: String,
    val accuracy: Double? = null,
    val battery: Int? = null,
    val markActivity: String? = null,
    val markNotes: String? = null,
    val clientId: String? = null,
    val synced: Boolean = false
)
