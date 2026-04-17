package com.bluemix.clients_lead.features.location

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
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
    val isAgent = authUser?.isAdmin == false && authUser?.isSuperAdmin == false
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
                            "This app requires location permissions to function.\n\n1. Tap 'Grant Permission' below\n2. Select 'Allow all the time'\n3. Return to this app",
                            "Grant Permission",
                            { openLocationPermissionSettings(context) }
                        )
                        !isGpsEnabled -> Quintet(
                            Icons.Default.LocationOff,
                            "GPS is Disabled",
                            "Location services are turned off.\n\nPlease enable GPS to continue using the app.",
                            "Enable GPS",
                            { openGpsSettings(context) }
                        )
                        else -> Quintet(
                            Icons.Default.Refresh,
                            "Tracking Inactive",
                            "The background tracking service is not running.\n\nPlease restart the app or check settings.",
                            "Refresh",
                            { trackingStateManager.updatePermissionState() }
                        )
                    }

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
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
                        color = Color(0xFFB0B0B0),
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                    
                    Spacer(modifier = Modifier.height(40.dp))
                    
                    // ✅ FIXED: Button with proper styling and visible text
                    Button(
                        onClick = {
                            action()
                            // Refresh state after returning from settings
                            MainScope().launch {
                                kotlinx.coroutines.delay(500)
                                trackingStateManager.updatePermissionState()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 8.dp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                !isPermissionGranted -> Icons.Default.LockOpen
                                !isGpsEnabled -> Icons.Default.LocationOn
                                else -> Icons.Default.Refresh
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = buttonText,
                            style = AppTheme.typography.label1,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // ✅ FIXED: Refresh button with proper styling
                    OutlinedButton(
                        onClick = { 
                            trackingStateManager.updatePermissionState()
                            // Also trigger GPS check
                            android.util.Log.d("BlockingScreen", "Refresh clicked")
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF10B981)
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF10B981))
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Refresh Status",
                            style = AppTheme.typography.label2,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // ✅ FIXED: Show instructions text
                    if (!isPermissionGranted) {
                        Text(
                            text = "Note: Select 'Allow all the time' for background tracking",
                            style = AppTheme.typography.body3,
                            color = Color(0xFF808080),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
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

private fun openLocationPermissionSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // For Android 10+, need to go to app settings for background permission
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    } else {
        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        android.util.Log.e("BlockingScreen", "Failed to open settings", e)
    }
}

private fun openGpsSettings(context: Context) {
    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        android.util.Log.e("BlockingScreen", "Failed to open GPS settings", e)
    }
}
