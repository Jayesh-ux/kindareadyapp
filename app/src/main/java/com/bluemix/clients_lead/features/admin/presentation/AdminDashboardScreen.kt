package com.bluemix.clients_lead.features.admin.presentation

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemix.clients_lead.domain.repository.AgentLocation
import com.bluemix.clients_lead.features.admin.vm.AdminDashboardViewModel
import com.bluemix.clients_lead.domain.repository.VisibilityFilter
import org.koin.androidx.compose.koinViewModel
import ui.AppTheme
import ui.components.Scaffold
import ui.components.Text
import ui.components.topbar.TopBar
import ui.components.topbar.TopBarDefaults

@Composable
fun AdminDashboardScreen(
    viewModel: AdminDashboardViewModel = koinViewModel(),
    onNavigateToMap: () -> Unit = {},
    onNavigateToReports: (String?) -> Unit = {},
    onNavigateToUsers: () -> Unit = {},
    onNavigateToClientServices: () -> Unit = {},
    onNavigateToBankAccount: () -> Unit = {},
    onNavigateToSlotExpansion: () -> Unit = {},
    onNavigateToPlanUsage: () -> Unit = {},
    onNavigateToAgentDetail: (String) -> Unit = {},
    onNavigateToMeetingLogs: () -> Unit = {},
    onNavigateToPinClients: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopBar(
                colors = TopBarDefaults.topBarColors(
                    containerColor = Color(0xFF0F172A),
                    scrolledContainerColor = Color(0xFF0F172A)
                )
            ) {
                Text(
                    text = "Admin Panel",
                    style = AppTheme.typography.h2,
                    color = Color.White
                )
            }
        },
        containerColor = AppTheme.colors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(AppTheme.colors.background, AppTheme.colors.surface)
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // Summary Stats Grid - Glass Effect
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Overview",
                    style = AppTheme.typography.label1,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Active",
                        value = uiState.activeAgentsCount.toString(),
                        icon = Icons.Default.Bolt,
                        color = Color(0xFF10B981), // Emerald 500
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Verified",
                        value = "${uiState.gpsVerifiedCount}%",
                        icon = Icons.Default.Verified,
                        color = Color(0xFF3B82F6), // Blue 500
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Pending",
                        value = uiState.hiddenClientsCount.toString(),
                        icon = Icons.Default.Warning,
                        color = Color(0xFFF59E0B), // Amber 500
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Operations Center
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Command Center",
                    style = AppTheme.typography.h3,
                    color = AppTheme.colors.text,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AdminNavCard(
                            title = "Live Tracking",
                            subtitle = "Monitor team movement",
                            icon = Icons.Default.MyLocation,
                            onClick = onNavigateToMap,
                            modifier = Modifier.weight(1f),
                            accentColor = Color(0xFF3B82F6)
                        )
                        AdminNavCard(
                            title = "Journey Tracking & Reports",
                            subtitle = "Analyze past logs",
                            icon = Icons.Default.HistoryEdu,
                            onClick = { onNavigateToReports(null) },
                            modifier = Modifier.weight(1f),
                            accentColor = Color(0xFF8B5CF6)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AdminNavCard(
                            title = "Team",
                            subtitle = "Manage permissions",
                            icon = Icons.Default.AdminPanelSettings,
                            onClick = onNavigateToUsers,
                            modifier = Modifier.weight(1f),
                            accentColor = Color(0xFF10B981)
                        )
                        AdminNavCard(
                            title = "Clients",
                            subtitle = "Service database",
                            icon = Icons.Default.AutoGraph,
                            onClick = onNavigateToClientServices,
                            modifier = Modifier.weight(1f),
                            accentColor = Color(0xFFF59E0B)
                        )
                    }
                }
            }

            // Phase 2: Pin Missing Clients Button
            Surface(
                onClick = { onNavigateToPinClients() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = AppTheme.colors.surface.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, AppTheme.colors.outline)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Map, null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                    Column {
                        Text(
                            "Pin Missing Clients",
                            color = AppTheme.colors.text,
                            style = AppTheme.typography.body1,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Manually pin client locations on map",
                            color = AppTheme.colors.textSecondary,
                            style = AppTheme.typography.body3
                        )
                    }
                }
            }

            // Team Activity List Header
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Real-time Field Activity",
                        style = AppTheme.typography.h3,
                        color = AppTheme.colors.text,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = viewModel::refreshDashboard) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF3B82F6))
                    }
                }

                if (uiState.isLoading && uiState.agents.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF3B82F6), strokeWidth = 3.dp)
                    }
                } else {
                    // Visibility Filters
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VisibilityFilter.values().forEach { filter ->
                            val isSelected = uiState.visibilityFilter == filter
                            val color = when(filter) {
                                VisibilityFilter.ALL -> Color(0xFF6366F1)
                                VisibilityFilter.SEEN_TODAY -> Color(0xFF10B981)
                                VisibilityFilter.UNSEEN_TODAY -> Color(0xFFEF4444)
                            }

                            Surface(
                                onClick = { viewModel.onVisibilityFilterChanged(filter) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) color.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                                border = BorderStroke(1.dp, if (isSelected) color else Color.White.copy(alpha = 0.1f))
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when(filter) {
                                            VisibilityFilter.ALL -> "All"
                                            VisibilityFilter.SEEN_TODAY -> "Seen"
                                            VisibilityFilter.UNSEEN_TODAY -> "Unseen"
                                        },
                                        color = if (isSelected) color else AppTheme.colors.textSecondary,
                                        style = AppTheme.typography.label2,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.filteredAgents.forEach { agent ->
                            AgentActivityRow(
                                agent = agent,
                                onClick = { onNavigateToAgentDetail(agent.id) }
                            )
                        }
                    }
                    
                    if (uiState.agents.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Waiting for team movement...",
                                color = AppTheme.colors.textSecondary,
                                style = AppTheme.typography.body2
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.15f), color.copy(alpha = 0.05f))
                )
            )
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(10.dp),
                color = color.copy(alpha = 0.2f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column {
                Text(
                    text = value,
                    style = AppTheme.typography.h2,
                    color = AppTheme.colors.text,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp
                )
                Text(
                    text = title,
                    style = AppTheme.typography.label2,
                    color = AppTheme.colors.textSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun AdminNavCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF3B82F6)
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF1E293B).copy(alpha = 0.4f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = AppTheme.typography.h4,
                color = AppTheme.colors.text,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
            Text(
                text = subtitle,
                style = AppTheme.typography.body3,
                color = AppTheme.colors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun AgentActivityRow(
    agent: AgentLocation,
    onClick: () -> Unit = {}
) {
    val isOnline = com.bluemix.clients_lead.core.common.utils.DateTimeUtils.isRecent(agent.timestamp)
    val isInMeeting = agent.activity?.contains("meeting", ignoreCase = true) == true
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppTheme.colors.surface.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status indicator
            Box(contentAlignment = Alignment.Center) {
                val statusColor = when {
                    !agent.isActive -> Color(0xFFEF4444)
                    isInMeeting -> Color(0xFF8B5CF6)
                    isOnline -> Color(0xFF10B981)
                    else -> Color(0xFF64748B)
                }
                
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = statusColor.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                !agent.isActive -> Icons.Default.Block
                                isInMeeting -> Icons.Default.Handshake
                                isOnline -> Icons.Default.DirectionsRun
                                else -> Icons.Default.NightsStay
                            },
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                // Active Pulse for online agents
                if (isOnline && agent.isActive && !isInMeeting) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.4f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "scale"
                    )
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "alpha"
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .graphicsLayer { 
                                scaleX = pulseScale
                                scaleY = pulseScale
                                alpha = pulseAlpha
                            }
                            .border(2.dp, statusColor, CircleShape)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = agent.fullName ?: agent.email,
                    style = AppTheme.typography.body1,
                    color = AppTheme.colors.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = when {
                        !agent.isActive -> "Access Restricted"
                        isInMeeting -> "In Visit with Client"
                        isOnline -> agent.activity ?: "Moving on territory"
                        else -> "Inactive for ${com.bluemix.clients_lead.core.common.utils.DateTimeUtils.formatLastSeen(agent.timestamp)}"
                    },
                    style = AppTheme.typography.body3,
                    color = AppTheme.colors.textSecondary,
                    fontSize = 13.sp
                )
            }

            val battery = agent.battery
            if (battery != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Icon(
                        imageVector = if (battery > 20) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
                        contentDescription = null,
                        tint = if (battery <= 20) Color(0xFFEF4444) else Color(0xFF10B981).copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${battery}%",
                        style = AppTheme.typography.body3,
                        color = AppTheme.colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}


@Composable
private fun Badge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}
