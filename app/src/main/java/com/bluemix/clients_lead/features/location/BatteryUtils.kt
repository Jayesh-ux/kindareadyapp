package com.bluemix.clients_lead.features.location

import android.content.Context
import android.os.BatteryManager


object BatteryUtils {
    fun getBatteryPercentage(context: Context): Int? {
        return try {
            // Priority 1: BatteryManager property (More reliable on modern Android)
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val propertyLevel = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            
            // Priority 2: Sticky Intent (Fallback)
            val iFilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, iFilter)
            
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            
            val intentPercentage = if (level != -1 && scale != -1) {
                (level * 100 / scale.toFloat()).toInt()
            } else -1

            // Select the best value: prefer Intent if available and >= 0, else property, else null
            val result = when {
                intentPercentage >= 0 -> intentPercentage
                propertyLevel >= 0 -> propertyLevel
                else -> null // Failure fallback
            }

            timber.log.Timber.d("Battery determined: $result% (Intent: $intentPercentage, Prop: $propertyLevel)")
            
            result
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error getting battery percentage")
            null
        }
    }
}
