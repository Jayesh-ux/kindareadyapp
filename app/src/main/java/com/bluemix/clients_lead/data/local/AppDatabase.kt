package com.bluemix.clients_lead.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.bluemix.clients_lead.data.local.dao.PendingLocationLogDao
import com.bluemix.clients_lead.data.local.entity.PendingLocationLog

@Database(
    entities = [PendingLocationLog::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun pendingLocationLogDao(): PendingLocationLogDao
    
    companion object {
        private const val DATABASE_NAME = "kindaready_app_db"
        
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }
        
        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            ).build()
        }
    }
}