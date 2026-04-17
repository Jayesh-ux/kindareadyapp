package com.bluemix.clients_lead.features.location

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import timber.log.Timber

/**
 * Handles detection and prompting for battery optimization exclusion.
 * Rule 8: Prompt user ONLY ONCE and handle OEM restrictions.
 */
object BatteryOptimizationHandler {
    private const val PREFS_NAME = "battery_prefs"
    private const val KEY_PROMPTED = "optimization_prompted"
    private const val TAG = "TrackingSystem"

    /**
     * Checks if battery optimization is already disabled for the app.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Prompts the user to disable battery optimizations, but only if they haven't been prompted before.
     */
    fun promptToDisableOptimization(context: Context) {
        if (isIgnoringBatteryOptimizations(context)) {
            Timber.tag(TAG).d("Battery optimization is already disabled.")
            return
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val alreadyPrompted = prefs.getBoolean(KEY_PROMPTED, false)

        if (!alreadyPrompted) {
            Timber.tag(TAG).w("Prompting user to disable battery optimization for reliable tracking.")
            
            try {
                // S8: Direct intent to request ignoring battery optimization
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                
                // Persist flag to avoid repeated nagging
                prefs.edit().putBoolean(KEY_PROMPTED, true).apply()
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to launch battery optimization settings")
                
                // Fallback to general battery settings if direct intent fails
                try {
                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                    prefs.edit().putBoolean(KEY_PROMPTED, true).apply()
                } catch (e2: Exception) {
                    Timber.tag(TAG).e(e2, "Final fallback failed")
                }
            }
        } else {
            Timber.tag(TAG).d("User already prompted for battery optimization. Skipping to avoid nagging.")
        }
    }

    /**
     * Allows resetting the prompt flag if needed (e.g. from settings).
     */
    fun resetPromptFlag(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PROMPTED, false)
            .apply()
    }
}
