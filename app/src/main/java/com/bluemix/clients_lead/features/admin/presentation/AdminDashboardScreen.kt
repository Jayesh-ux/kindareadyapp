package com.bluemix.clients_lead.features.admin.presentation

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
    onNavigateToMeetingLogs: () -> Unit = {}
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
        containerColor = Color(0xFF020617) // Slate 950
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF020617), Color(0xFF0F172A))
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Summary Stats Grid
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Active Agents",
                        value = uiState.activeAgentsCount.toString(),
                        icon = Icons.Default.Groups,
                        color = Color(0xFF10B981), // Emerald 500
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Total Clients",
                        value = uiState.totalClients.toString(),
                        icon = Icons.Default.Store,
                        color = Color(0xFF3B82F6), // Blue 500
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Hidden Clients",
                        value = uiState.hiddenClientsCount.toString(),
                        icon = Icons.Default.LocationOff,
                        color = Color(0xFFEF4444), // Red 500
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Navigation Grid
            Text(
                text = "Operations Center",
                style = AppTheme.typography.h3,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AdminNavCard(
                        title = "Live Map",
                        subtitle = "Real-time tracking",
                        icon = Icons.Default.Map,
                        onClick = onNavigateToMap,
                        modifier = Modifier.weight(1f)
                    )
                    AdminNavCard(
                        title = "Journey Logs",
                        subtitle = "Daily route history",
                        icon = Icons.Default.Route,
                        onClick = { onNavigateToReports(null) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AdminNavCard(
                        title = "Manage Team",
                        subtitle = "Agents & Access",
                        icon = Icons.Default.People,
                        onClick = onNavigateToUsers,
                        modifier = Modifier.weight(1f)
                    )
                    AdminNavCard(
                        title = "Client Services",
                        subtitle = "Database & visits",
                        icon = Icons.Default.Inventory,
                        onClick = onNavigateToClientServices,
                        modifier = Modifier.weight(1f)
                    )
                }
                AdminNavCard(
                    title = "Location Recovery",
                    subtitle = "Trigger self-heal for hidden clients",
                    icon = Icons.Default.AutoFixHigh,
                    onClick = viewModel::retryGeocoding,
                    modifier = Modifier.fillMaxWidth()
                )
                
                AdminNavCard(
                    title = "Meeting Logs",
                    subtitle = "Team visit history",
                    icon = Icons.Default.History,
                    onClick = onNavigateToMeetingLogs,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Financials & Subscription
            Text(
                text = "Financials & Subscription",
                style = AppTheme.typography.h3,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AdminNavCard(
                        title = "Bank Accounts",
                        subtitle = "Manager user UPI/Bank",
                        icon = Icons.Default.AccountBalance,
                        onClick = onNavigateToBankAccount,
                        modifier = Modifier.weight(1f)
                    )
                    AdminNavCard(
                        title = "Expand Capacity",
                        subtitle = "Purchase more slots",
                        icon = Icons.Default.AddBusiness,
                        onClick = onNavigateToSlotExpansion,
                        modifier = Modifier.weight(1f)
                    )
                }
                AdminNavCard(
                    title = "Plan Usage",
                    subtitle = "Subscription & limits",
                    icon = Icons.Default.Analytics,
                    onClick = onNavigateToPlanUsage,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Team Activity List Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Team Activity",
                    style = AppTheme.typography.h3,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.isClearingLogs) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = { viewModel.clearAllLogs() }) {
                        Text("Clear", color = Color(0xFFEF4444), fontSize = 12.sp)
                    }
                    IconButton(onClick = viewModel::refreshDashboard) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF3B82F6))
                    }
                }
            }

            if (uiState.isLoading && uiState.agents.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF3B82F6))
                }
            } else {
                uiState.agents.forEach { agent ->
                    AgentActivityRow(
                        agent = agent,
                        onClick = { onNavigateToAgentDetail(agent.id) }
                    )
                }
                
                if (uiState.agents.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No recent team activity",
                            color = Color.Gray,
                            style = AppTheme.typography.body2
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
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
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = value,
                style = AppTheme.typography.h2,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp
            )
            Text(
                text = title,
                style = AppTheme.typography.label2,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun AdminNavCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                )
            )
            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF3B82F6).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = AppTheme.typography.h4,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = subtitle,
                style = AppTheme.typography.body3,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                lineHeight = 14.sp
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
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.6f))
            .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Status indicator with glow
            Box(contentAlignment = Alignment.Center) {
                val statusColor = when {
                    !agent.isActive -> Color(0xFFEF4444)
                    isInMeeting -> Color(0xFF8B5CF6)
                    isOnline -> Color(0xFF10B981)
                    else -> Color(0xFF64748B)
                }
                
                // Glow effect
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.2f))
                )
                
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = agent.fullName ?: agent.email,
                        style = AppTheme.typography.body1,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    if (isInMeeting) {
                        Badge(text = "MEETING", color = Color(0xFF8B5CF6))
                    }
                    if (!agent.isActive) {
                        Badge(text = "DISABLED", color = Color(0xFFEF4444))
                    }
                }
                Text(
                    text = when {
                        !agent.isActive -> "Account is currently disabled"
                        isInMeeting -> "Currently in a meeting"
                        isOnline -> agent.activity ?: "Active"
                        else -> "Last seen ${com.bluemix.clients_lead.core.common.utils.DateTimeUtils.formatLastSeen(agent.timestamp)}"
                    },
                    style = AppTheme.typography.body3,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }

            val battery = agent.battery
            if (battery != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        imageVector = if (battery > 80) Icons.Default.BatteryFull 
                                     else if (battery > 20) Icons.Default.BatteryChargingFull 
                                     else Icons.Default.BatteryAlert,
                        contentDescription = null,
                        tint = if (battery <= 20) Color(0xFFEF4444) else Color(0xFF10B981),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${battery}%",
                        style = AppTheme.typography.body3,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(20.dp)
            )
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
