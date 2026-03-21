package com.bluemix.clients_lead.features.admin.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemix.clients_lead.features.admin.vm.AdminAgentDetailViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import ui.AppTheme
import ui.components.Scaffold
import ui.components.Text
import ui.components.topbar.TopBar
import ui.components.topbar.TopBarDefaults

@Composable
fun AdminAgentDetailScreen(
    agentId: String,
    viewModel: AdminAgentDetailViewModel = koinViewModel(parameters = { parametersOf(agentId) }),
    onNavigateBack: () -> Unit,
    onNavigateToReports: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val agent = uiState.agent

    Scaffold(
        topBar = {
            TopBar(
                colors = TopBarDefaults.topBarColors(
                    containerColor = Color(0xFF0F172A),
                    scrolledContainerColor = Color(0xFF0F172A)
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        text = "Agent Profile",
                        style = AppTheme.typography.h2,
                        color = Color.White
                    )
                }
            }
        },
        containerColor = Color(0xFF020617)
    ) { padding ->
        if (uiState.isLoading && agent == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF3B82F6))
            }
        } else if (agent == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Agent not found", color = Color.Gray)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Profile Header Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF3B82F6).copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (agent.fullName ?: agent.email).take(1).uppercase(),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF3B82F6)
                            )
                        }

                        Text(
                            text = agent.fullName ?: "Unnamed Agent",
                            style = AppTheme.typography.h2,
                            color = Color.White
                        )
                        Text(
                            text = agent.email,
                            style = AppTheme.typography.body2,
                            color = Color.White.copy(alpha = 0.5f)
                        )

                        // Online Status with "Last Seen"
                        val isOnline = com.bluemix.clients_lead.core.common.utils.DateTimeUtils.isRecent(agent.timestamp)
                        val lastSeenText = com.bluemix.clients_lead.core.common.utils.DateTimeUtils.formatLastSeen(agent.timestamp)
                        
                        Text(
                            text = if (isOnline) "Active now" else "Last seen $lastSeenText",
                            style = AppTheme.typography.label2,
                            color = if (isOnline) Color(0xFF10B981) else Color.White.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Medium
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val isOnline = com.bluemix.clients_lead.core.common.utils.DateTimeUtils.isRecent(agent.timestamp)
                            val isInMeeting = agent.activity?.contains("meeting", ignoreCase = true) == true
                            
                            OnlineStatusBadge(
                                isOnline = isOnline,
                                isInMeeting = isInMeeting,
                                isAccountActive = agent.isActive
                            )
                            
                            val battery = agent.battery
                            if (battery != null) {
                                BatteryIndicator(battery)
                            }
                        }
                    }
                }

                // Today's Performance Metrics
                Text("Today's Performance", style = AppTheme.typography.h3, color = Color.White)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        label = "Distance",
                        value = "%.2f km".format(uiState.todayDistanceKm),
                        icon = Icons.Default.Route,
                        color = Color(0xFFA855F7), // Purple 500
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "Logs today",
                        value = "${uiState.recentLogs.size}",
                        icon = Icons.Default.History,
                        color = Color(0xFF10B981), // Emerald 500
                        modifier = Modifier.weight(1f)
                    )
                }

                // Actions Section
                val isUpdatingStatus = uiState.isUpdatingStatus
                Text("Actions", style = AppTheme.typography.h3, color = Color.White)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton(
                        text = "View Reports",
                        icon = Icons.Default.Assessment,
                        containerColor = Color(0xFF3B82F6),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToReports(agentId) }
                    )
                    ActionButton(
                        text = if (agent.isActive) "Disable" else "Enable",
                        icon = if (agent.isActive) Icons.Default.PersonOff else Icons.Default.PersonAdd,
                        containerColor = if (agent.isActive) Color(0xFFEF4444) else Color(0xFF10B981),
                        modifier = Modifier.weight(1f),
                        enabled = !isUpdatingStatus,
                        onClick = { viewModel.toggleStatus(!agent.isActive) }
                    )
                }

                // Recent Activity
                Text("Recent Activity (Today)", style = AppTheme.typography.h3, color = Color.White)
                if (uiState.recentLogs.isEmpty()) {
                    Text("No activity logged today", color = Color.Gray, modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            uiState.recentLogs.take(5).forEach { log ->
                                ActivityItem(log)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineStatusBadge(
    isOnline: Boolean,
    isInMeeting: Boolean,
    isAccountActive: Boolean
) {
    val color = when {
        !isAccountActive -> Color(0xFFEF4444) // Red for Disabled
        isInMeeting -> Color(0xFF8B5CF6) // Violet for Meeting
        isOnline -> Color(0xFF10B981) // Green for Online
        else -> Color(0xFF64748B) // Gray for Offline
    }
    
    val text = when {
        !isAccountActive -> "DISABLED"
        isInMeeting -> "IN MEETING"
        isOnline -> "ACTIVE"
        else -> "OFFLINE"
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun BatteryIndicator(level: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            imageVector = Icons.Default.BatteryChargingFull,
            contentDescription = null,
            tint = if (level < 20) Color.Red else Color.Green,
            modifier = Modifier.size(16.dp)
        )
        Text("${level}%", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
            Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            disabledContainerColor = containerColor.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp),
        enabled = enabled
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActivityItem(log: com.bluemix.clients_lead.domain.model.LocationLog) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(16.dp))
        }
        Column {
            Text(
                text = "Location Update",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = com.bluemix.clients_lead.core.common.utils.DateTimeUtils.formatLastSeen(log.timestamp),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }
    }
}
