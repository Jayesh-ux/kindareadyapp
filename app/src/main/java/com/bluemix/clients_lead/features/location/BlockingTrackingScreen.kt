package com.bluemix.clients_lead.features.location

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemix.clients_lead.core.network.SessionManager
import org.koin.compose.koinInject
import ui.AppTheme

/**
 * A full-screen blocking UI that prevents app usage if mandatory tracking is not active.
 * Rule 9: Prevent navigation until resolved.
 */
@Composable
fun BlockingTrackingScreen(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val trackingStateManager: LocationTrackingStateManager = koinInject()
    val sessionManager: SessionManager = koinInject()
    
    val authUser by sessionManager.authState.collectAsState()
    val isTrackingActive by trackingStateManager.trackingState.collectAsState()
    val isGpsEnabled by trackingStateManager.gpsState.collectAsState()
    val isPermissionGranted by trackingStateManager.permissionState.collectAsState()

    // Determine if blocking is required (Admins are exempt)
    val isLoggedIn = authUser != null
    val isAgent = authUser?.isAdmin == false
    val needsFix = isLoggedIn && isAgent && (!isTrackingActive || !isGpsEnabled || !isPermissionGranted)

    // Flicker prevention: Only show overlay after a small delay if needsFix is still true
    var showOverlay by remember { mutableStateOf(false) }
    LaunchedEffect(needsFix) {
        if (needsFix) {
            kotlinx.coroutines.delay(800) // 800ms grace period
            showOverlay = true
        } else {
            showOverlay = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main App Content
        content()

        // Blocking Overlay
        if (showOverlay && needsFix) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val (icon, title, message, buttonText, action) = when {
                        !isPermissionGranted -> Quintet(
                            Icons.Default.Security,
                            "Permissions Required",
                            "This app requires background location permissions to function. Please grant them in settings.",
                            "Open App Settings",
                            { openAppSettings(context) }
                        )
                        !isGpsEnabled -> Quintet(
                            Icons.Default.LocationOff,
                            "GPS is Disabled",
                            "Location services are turned off. Please enable GPS to continue using the app.",
                            "Enable GPS",
                            { openLocationSettings(context) }
                        )
                        else -> Quintet(
                            Icons.Default.Error,
                            "Tracking Inactive",
                            "The mandatory background tracking service is not running correctly.",
                            "Repair Tracking",
                            { 
                                // Attempt immediate manual repair
                                // Component scopes are already in trackingStateManager
                            }
                        )
                    }

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AppTheme.colors.error,
                        modifier = Modifier.size(80.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = title,
                        style = AppTheme.typography.h2,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = message,
                        style = AppTheme.typography.body1,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                    
                    Spacer(modifier = Modifier.height(40.dp))
                    
                    Button(
                        onClick = action,
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            text = buttonText,
                            style = AppTheme.typography.label1,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    TextButton(onClick = { trackingStateManager.updatePermissionState() }) {
                        Text(
                            text = "Refresh Status",
                            color = AppTheme.colors.primary,
                            style = AppTheme.typography.label2
                        )
                    }
                }
            }
        }
    }
}

private data class Quintet<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = android.net.Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun openLocationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
