package com.bluemix.clients_lead.features.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bluemix.clients_lead.core.network.SessionManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Ensures location tracking restarts automatically after a device reboot.
 */
class BootReceiver : BroadcastReceiver(), KoinComponent {

    private val sessionManager: SessionManager by inject()
    private val trackingStateManager: LocationTrackingStateManager by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val tag = "TrackingSystem"
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.tag(tag).d("🚀 Device reboot detected. Checking if tracking should restart...")

            val userId = sessionManager.getCurrentUserId()
            if (userId != null) {
                Timber.tag(tag).d("✅ User is logged in (ID: $userId). Auto-starting location tracking...")
                // auto-starting location tracking
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        trackingStateManager.startTracking()
                    } catch (e: Exception) {
                        Timber.tag(tag).e(e, "Failed to auto-start tracking on boot")
                    }
                }
            } else {
                Timber.tag(tag).d("ℹ️ No active session found. Skipping auto-start.")
            }
        }
    }
}
