package com.bluemix.clients_lead.features.admin.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemix.clients_lead.domain.repository.AgentLocation
import com.bluemix.clients_lead.features.admin.vm.AdminUserManagementViewModel
import org.koin.androidx.compose.koinViewModel
import ui.AppTheme
import ui.components.Scaffold
import ui.components.Text
import ui.components.topbar.TopBar
import ui.components.topbar.TopBarDefaults
import ui.components.textfield.TextField

@Composable
fun AdminUserManagementScreen(
    viewModel: AdminUserManagementViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToAgentDetail: (String) -> Unit = {}
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        text = "User Management",
                        style = AppTheme.typography.h2,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        containerColor = Color(0xFF020617)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar
            Box(modifier = Modifier.padding(16.dp)) {
                TextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("Search by name or email...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B)),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray)
                            }
                        }
                    }
                )
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF3B82F6))
                }
            } else if (uiState.searchResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PersonOff, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(48.dp))
                        Text("No users found", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.searchResults) { agent ->
                        UserCard(
                            agent = agent,
                            onToggleStatus = { viewModel.toggleUserStatus(agent.id, !agent.isActive) },
                            onClick = { onNavigateToAgentDetail(agent.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserCard(
    agent: AgentLocation,
    onToggleStatus: () -> Unit,
    onClick: () -> Unit
) {
    val isOnline = com.bluemix.clients_lead.core.common.utils.DateTimeUtils.isRecent(agent.timestamp)
    val isInMeeting = agent.activity?.contains("meeting", ignoreCase = true) ?: false
    val lastSeen = com.bluemix.clients_lead.core.common.utils.DateTimeUtils.formatLastSeen(agent.timestamp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Avatar with Online Status
            Box {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) Color(0xFF10B981).copy(alpha = 0.1f) else Color(0xFF475569).copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (agent.fullName ?: agent.email).take(1).uppercase(),
                        style = AppTheme.typography.h3,
                        color = if (isOnline) Color(0xFF10B981) else Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Online Dot
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A))
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(if (isOnline) Color(0xFF10B981) else Color(0xFFEF4444))
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = agent.fullName ?: "Unnamed Agent",
                        style = AppTheme.typography.body1,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (isInMeeting) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF8B5CF6).copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("IN MEETING", color = Color(0xFF8B5CF6), fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
                
                Text(
                    text = agent.email,
                    style = AppTheme.typography.body3,
                    color = Color.White.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(6.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val battery = agent.battery
                    if (battery != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                imageVector = Icons.Default.BatteryChargingFull,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = if (battery <= 20) Color.Red else Color.Green
                            )
                            Text("${battery}%", style = AppTheme.typography.label3, color = Color.Gray)
                        }
                    }
                    
                    // Last Seen
                    val statusText = when {
                        !agent.isActive -> "Disabled"
                        isInMeeting -> "In Meeting"
                        isOnline -> "Active now"
                        else -> "Last seen $lastSeen"
                    }
                    Text(statusText, style = AppTheme.typography.label3, color = if (isOnline) Color(0xFF10B981) else Color.Gray)
                }
            }

            // Status Toggle
            Column(horizontalAlignment = Alignment.End) {
                Switch(
                    checked = agent.isActive,
                    onCheckedChange = { onToggleStatus() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF3B82F6),
                        uncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                        uncheckedTrackColor = Color(0xFF1E293B)
                    ),
                    modifier = Modifier.scale(0.8f)
                )
                Text(
                    text = if (agent.isActive) "ENABLED" else "DISABLED",
                    style = AppTheme.typography.label3,
                    color = if (agent.isActive) Color(0xFF3B82F6) else Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
