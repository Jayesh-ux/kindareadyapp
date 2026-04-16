package com.bluemix.clients_lead.data.local.dao

import androidx.room.*
import com.bluemix.clients_lead.data.local.entity.PendingLocationLog
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingLocationLogDao {
    
    @Query("SELECT * FROM pending_location_logs WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedLogs(): List<PendingLocationLog>
    
    @Query("SELECT COUNT(*) FROM pending_location_logs WHERE synced = 0")
    fun getUnsyncedCount(): Flow<Int>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: PendingLocationLog): Long
    
    @Query("UPDATE pending_location_logs SET synced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)
    
    @Query("DELETE FROM pending_location_logs WHERE synced = 1 AND timestamp < :beforeTimestamp")
    suspend fun deleteSyncedOlderThan(beforeTimestamp: String)
    
    @Query("DELETE FROM pending_location_logs")
    suspend fun deleteAll()
}