// File: app/src/main/java/com/bluemix/clients_lead/features/location/MockLocationJoystick.kt
package com.bluemix.clients_lead.features.location

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import ui.AppTheme
import kotlin.math.*

@Composable
fun MockLocationJoystick(
    mockProvider: MockLocationProvider,
    modifier: Modifier = Modifier,
    onLocationUpdate: (Double, Double) -> Unit = { _, _ -> }
) {
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var isActive by remember { mutableStateOf(false) }
    var speedMultiplier by remember { mutableStateOf(1.0) }
    var isMinimized by remember { mutableStateOf(false) }
    var isLoggingEnabled by remember { mutableStateOf(mockProvider.isLoggingEnabled) }  // ✅ NEW

    val scope = rememberCoroutineScope()
    val mockLocation by mockProvider.mockLocation.collectAsState()

    // Auto-move while joystick is held
    LaunchedEffect(isActive, offsetX, offsetY, speedMultiplier) {
        while (isActive && (abs(offsetX) > 0.1f || abs(offsetY) > 0.1f)) {
            val magnitude = sqrt(offsetX * offsetX + offsetY * offsetY)
            val normalizedX = offsetX / magnitude
            val normalizedY = offsetY / magnitude

            val direction = when {
                normalizedY < -0.5 && abs(normalizedX) < 0.5 -> "north"
                normalizedY > 0.5 && abs(normalizedX) < 0.5 -> "south"
                normalizedX > 0.5 && abs(normalizedY) < 0.5 -> "east"
                normalizedX < -0.5 && abs(normalizedY) < 0.5 -> "west"
                normalizedY < 0 && normalizedX > 0 -> "ne"
                normalizedY < 0 && normalizedX < 0 -> "nw"
                normalizedY > 0 && normalizedX > 0 -> "se"
                else -> "sw"
            }

            val baseSpeed = when (speedMultiplier) {
                1.0 -> 1.4
                2.0 -> 4.2
                else -> 11.1
            }

            mockProvider.moveInDirection(direction, baseSpeed * magnitude)
            mockLocation?.let {
                onLocationUpdate(it.latitude, it.longitude)
            }

            delay(100)
        }
    }

    // Minimized view
    if (isMinimized) {
        Box(
            modifier = modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(AppTheme.colors.primary.copy(alpha = 0.9f))
                .clickable { isMinimized = false }
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(text = "🎮", fontSize = 20.sp)
                Text(
                    text = "GPS",
                    fontSize = 9.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        return
    }

    // Full joystick UI
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AppTheme.colors.surface.copy(alpha = 0.85f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header with minimize button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🎮", fontSize = 20.sp)
                Column {
                    Text(
                        text = "GPS Demo",
                        style = AppTheme.typography.body1,
                        color = AppTheme.colors.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    // ✅ NEW: Show logging status
                    if (isLoggingEnabled) {
                        Text(
                            text = "💾 Logging to DB",
                            style = AppTheme.typography.body3,
                            color = AppTheme.colors.success,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // ✅ NEW: Logging toggle button
                IconButton(
                    onClick = {
                        isLoggingEnabled = !isLoggingEnabled
                        mockProvider.isLoggingEnabled = isLoggingEnabled
                        Timber.d("🎮 Database logging ${if (isLoggingEnabled) "ENABLED" else "DISABLED"}")
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isLoggingEnabled) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = if (isLoggingEnabled) "Disable Logging" else "Enable Logging",
                        tint = if (isLoggingEnabled) AppTheme.colors.success else AppTheme.colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { mockProvider.reset() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset",
                        tint = AppTheme.colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { isMinimized = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Minimize",
                        tint = AppTheme.colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Current coordinates
        mockLocation?.let { location ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Lat: ${String.format("%.5f", location.latitude)}",
                    style = AppTheme.typography.body2,
                    color = AppTheme.colors.text,
                    fontSize = 11.sp
                )
                Text(
                    text = "Lng: ${String.format("%.5f", location.longitude)}",
                    style = AppTheme.typography.body2,
                    color = AppTheme.colors.text,
                    fontSize = 11.sp
                )
            }
        }

        // Speed selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SpeedButton(
                icon = "🚶",
                label = "Walk",
                isSelected = speedMultiplier == 1.0,
                onClick = { speedMultiplier = 1.0 },
                modifier = Modifier.weight(1f)
            )
            SpeedButton(
                icon = "🃏",
                label = "Run",
                isSelected = speedMultiplier == 2.0,
                onClick = { speedMultiplier = 2.0 },
                modifier = Modifier.weight(1f)
            )
            SpeedButton(
                icon = "🚗",
                label = "Drive",
                isSelected = speedMultiplier == 5.0,
                onClick = { speedMultiplier = 5.0 },
                modifier = Modifier.weight(1f)
            )
        }

        // Joystick
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(AppTheme.colors.background.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { isActive = true },
                            onDragEnd = {
                                isActive = false
                                offsetX = 0f
                                offsetY = 0f
                            },
                            onDragCancel = {
                                isActive = false
                                offsetX = 0f
                                offsetY = 0f
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            val maxRadius = 70f
                            offsetX = (offsetX + dragAmount.x).coerceIn(-maxRadius, maxRadius)
                            offsetY = (offsetY + dragAmount.y).coerceIn(-maxRadius, maxRadius)
                        }
                    }
            ) {
                val center = Offset(size.width / 2, size.height / 2)

                drawCircle(
                    color = Color(0xFF5E92F3),
                    radius = size.minDimension / 2,
                    style = Stroke(width = 3.dp.toPx())
                )

                drawLine(
                    color = Color(0xFF5E92F3).copy(alpha = 0.3f),
                    start = Offset(center.x - 15f, center.y),
                    end = Offset(center.x + 15f, center.y),
                    strokeWidth = 2.dp.toPx()
                )
                drawLine(
                    color = Color(0xFF5E92F3).copy(alpha = 0.3f),
                    start = Offset(center.x, center.y - 15f),
                    end = Offset(center.x, center.y + 15f),
                    strokeWidth = 2.dp.toPx()
                )

                drawCircle(
                    color = Color(0xFF5E92F3),
                    radius = 25.dp.toPx(),
                    center = Offset(center.x + offsetX, center.y + offsetY)
                )
            }
        }
    }
}

@Composable
private fun SpeedButton(
    icon: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) AppTheme.colors.primary else AppTheme.colors.background,
            contentColor = if (isSelected) Color.White else AppTheme.colors.text
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = icon, fontSize = 16.sp)
            Text(text = label, fontSize = 10.sp)
        }
    }
}