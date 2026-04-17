package com.bluemix.clients_lead

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.bluemix.clients_lead.core.navigation.AppNavHost
import com.bluemix.clients_lead.core.network.SessionManager
import com.bluemix.clients_lead.features.location.BlockingTrackingScreen
import com.bluemix.clients_lead.features.location.LocationTrackingStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import ui.AppTheme

class MainActivity : ComponentActivity() {

    private val trackingStateManager: LocationTrackingStateManager by inject()
    private val sessionManager: SessionManager by inject()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        try {
            android.util.Log.d("MainActivity", "Permission result: $permissions")
            val locationGranted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (!locationGranted) {
                Toast.makeText(this, "Location permission is required for map & tracking", Toast.LENGTH_LONG).show()
            }
            android.util.Log.d("MainActivity", "Permission handled successfully")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error in permission callback", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ask runtime permissions only when the UI needs them (first run is fine)
        requestRuntimePermissions()

        setContent { AppRoot() }
    }

    private fun requestRuntimePermissions() {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }
}

@Composable
fun AppRoot() {
    AppTheme {
        AppNavHost()
    }
}
