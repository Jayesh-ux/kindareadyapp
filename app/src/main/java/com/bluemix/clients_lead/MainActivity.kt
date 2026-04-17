package com.bluemix.clients_lead

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import com.bluemix.clients_lead.core.navigation.AppNavHost
import com.bluemix.clients_lead.core.network.SessionManager
import com.bluemix.clients_lead.features.location.LocationTrackingStateManager
import org.koin.android.ext.android.inject
import ui.AppTheme

class MainActivity : ComponentActivity() {

    private val trackingStateManager: LocationTrackingStateManager by inject()
    private val sessionManager: SessionManager by inject()

    private var foregroundPermissionRequested = false
    private var backgroundPermissionRequested = false

    private val foregroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (fineLocationGranted || coarseLocationGranted) {
            android.util.Log.d("MainActivity", "Foreground permissions granted")
            
            // Now request background location separately (required for Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !backgroundPermissionRequested) {
                requestBackgroundLocationPermission()
            } else {
                trackingStateManager.updatePermissionState()
            }
        } else {
            Toast.makeText(this, "Location permission is required for map & tracking", Toast.LENGTH_LONG).show()
            trackingStateManager.updatePermissionState()
        }
    }

    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        backgroundPermissionRequested = true
        android.util.Log.d("MainActivity", "Background permission result: $granted")
        trackingStateManager.updatePermissionState()
        
        if (!granted) {
            Toast.makeText(this, "Background location is recommended for better tracking", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ask runtime permissions only when the UI needs them (first run is fine)
        requestRuntimePermissions()

        setContent { AppRoot() }
    }

    override fun onResume() {
        super.onResume()
        // Refresh permission state when returning from settings
        trackingStateManager.updatePermissionState()
    }

    private fun requestRuntimePermissions() {
        // First request foreground permissions
        if (!hasForegroundLocationPermission()) {
            requestForegroundLocationPermissions()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundLocationPermission()) {
            // Foreground already granted, request background
            requestBackgroundLocationPermission()
        }
    }

    private fun requestForegroundLocationPermissions() {
        if (foregroundPermissionRequested) return
        foregroundPermissionRequested = true
        
        val permissions = buildList {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        android.util.Log.d("MainActivity", "Requesting foreground permissions")
        foregroundPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun requestBackgroundLocationPermission() {
        if (backgroundPermissionRequested) return
        backgroundPermissionRequested = true
        
        android.util.Log.d("MainActivity", "Requesting background location permission")
        
        // Check if we should show rationale
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            Toast.makeText(
                this,
                "Background location is needed for tracking when app is closed",
                Toast.LENGTH_LONG
            ).show()
        }
        
        backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    fun hasForegroundLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasBackgroundLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Background permission not required before Android 10
            true
        }
    }

    fun hasAllLocationPermissions(): Boolean {
        return hasForegroundLocationPermission() && hasBackgroundLocationPermission()
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    fun refreshPermissionState() {
        trackingStateManager.updatePermissionState()
    }
}

@Composable
fun AppRoot() {
    AppTheme {
        AppNavHost()
    }
}
