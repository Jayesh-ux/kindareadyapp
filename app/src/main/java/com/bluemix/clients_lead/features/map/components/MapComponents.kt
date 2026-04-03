package com.bluemix.clients_lead.features.map.components

import android.Manifest
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemix.clients_lead.domain.model.Client
import com.bluemix.clients_lead.domain.model.VisitStatus
import com.bluemix.clients_lead.features.map.vm.MapViewModel
import com.bluemix.clients_lead.features.map.vm.MapUiState
import com.bluemix.clients_lead.features.meeting.utils.ProximityDetector
import com.bluemix.clients_lead.features.meeting.vm.MeetingUiState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import ui.AppTheme

@Composable
fun TrackingRequiredOverlay(
    modifier: Modifier = Modifier,
    onEnableTracking: () -> Unit,
    onRefreshStatus: () -> Unit
) {
    Box(
        modifier = modifier.background(AppTheme.colors.background.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.LocationOff,
                contentDescription = null,
                tint = AppTheme.colors.error,
                modifier = Modifier.size(64.dp)
            )
            androidx.compose.material3.Text(
                text = "Tracking Required",
                style = AppTheme.typography.h2,
                color = AppTheme.colors.text,
                textAlign = TextAlign.Center
            )
            androidx.compose.material3.Text(
                text = "To ensure safety and accurate logs, please enable background tracking.",
                style = AppTheme.typography.body2,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onEnableTracking,
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primary)
            ) {
                androidx.compose.material3.Text("Enable Tracking", color = AppTheme.colors.onPrimary)
            }
            TextButton(onClick = onRefreshStatus) {
                androidx.compose.material3.Text("I've enabled it", color = AppTheme.colors.primary)
            }
        }
    }
}

@Composable
fun TrackingBenefitItem(text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF10B981),
            modifier = Modifier.size(20.dp)
        )
        androidx.compose.material3.Text(
            text = text,
            style = AppTheme.typography.body2,
            color = AppTheme.colors.text.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun AnimatedClientBottomSheet(
    client: Client,
    uiState: MapUiState,
    viewModel: MapViewModel,
    cameraPositionState: CameraPositionState,
    onClose: () -> Unit,
    onViewDetails: () -> Unit,
    onStartMeeting: () -> Unit,
    meetingUiState: MeetingUiState,
    onQuickVisit: (String) -> Unit
) {
    var isEditingAddress by remember { mutableStateOf(false) }
    var editedAddress by remember { mutableStateOf(client.address ?: "") }
    var showVisitStatusMenu by remember { mutableStateOf(false) }
    val distanceMeters = uiState.currentLocation?.let {
        ProximityDetector.calculateDistance(LatLng(it.latitude, it.longitude), LatLng(client.latitude ?: 0.0, client.longitude ?: 0.0))
    } ?: Double.MAX_VALUE

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(AppTheme.colors.surface)
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    androidx.compose.material3.Text(
                        text = client.name,
                        style = AppTheme.typography.h3,
                        color = AppTheme.colors.text,
                        fontWeight = FontWeight.Bold
                    )
                    androidx.compose.material3.Text(
                        text = "Client ID: ${client.id}",
                        style = AppTheme.typography.label2,
                        color = AppTheme.colors.textSecondary
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            VisitStatusIndicator(status = client.getVisitStatusColor())

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = AppTheme.colors.primary)
                    androidx.compose.material3.Text(text = "Primary Address", style = AppTheme.typography.label1, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { isEditingAddress = !isEditingAddress }) {
                        Icon(imageVector = if (isEditingAddress) Icons.Default.Close else Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                    }
                }
                if (isEditingAddress) {
                    TextField(
                        value = editedAddress,
                        onValueChange = { editedAddress = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = AppTheme.typography.body2
                    )
                    Button(onClick = { viewModel.updateAddress(client.id, editedAddress); isEditingAddress = false }) {
                        androidx.compose.material3.Text("Update")
                    }
                } else {
                    androidx.compose.material3.Text(text = client.address ?: "No address", style = AppTheme.typography.body2)
                }
            }

            val canStartMeeting = distanceMeters <= 50.0 && !uiState.isAdmin
            if (canStartMeeting) {
                Button(onClick = onStartMeeting, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Text("Start Meeting")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onViewDetails, modifier = Modifier.weight(1f)) {
                    androidx.compose.material3.Text("Details")
                }
                OutlinedButton(onClick = {
                    client.latitude?.let { lat ->
                        client.longitude?.let { lng ->
                            cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 15f))
                        }
                    }
                }, modifier = Modifier.weight(1f)) {
                    androidx.compose.material3.Text("Focus")
                }
            }
        }
    }
}

@Composable
fun VisitStatusIndicator(status: VisitStatus) {
    val (color, label) = when (status) {
        VisitStatus.NEVER_VISITED -> Color(0xFFEF4444) to "Never Visited"
        VisitStatus.RECENT -> Color(0xFF10B981) to "Recent"
        VisitStatus.MODERATE -> Color(0xFFF59E0B) to "Follow-up"
        VisitStatus.OVERDUE -> Color(0xFFF97316) to "Overdue"
    }
    Box(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        androidx.compose.material3.Text(text = label, color = color, style = AppTheme.typography.label2, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AnimatedAgentBottomSheet(
    agent: com.bluemix.clients_lead.domain.repository.AgentLocation,
    uiState: MapUiState,
    onClose: () -> Unit,
    onViewProfile: (String) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(24.dp)).background(AppTheme.colors.surface).padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    androidx.compose.material3.Text(text = agent.fullName ?: "Agent", style = AppTheme.typography.h3, fontWeight = FontWeight.Bold)
                    androidx.compose.material3.Text(text = agent.smartStatus ?: "Active", style = AppTheme.typography.body2)
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, null) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AgentStatCard(label = "Visits", value = "${agent.visitCount}", icon = Icons.Default.DirectionsRun, color = Color.Blue, modifier = Modifier.weight(1f))
                AgentStatCard(label = "Battery", value = "${agent.battery ?: 0}%", icon = Icons.Default.BatteryChargingFull, color = Color.Green, modifier = Modifier.weight(1f))
            }
            Button(onClick = { onViewProfile(agent.id) }, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.Text("View Profile")
            }
        }
    }
}

@Composable
fun AgentStatCard(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.05f)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        androidx.compose.material3.Text(value, fontWeight = FontWeight.Bold)
        androidx.compose.material3.Text(label, style = AppTheme.typography.label2)
    }
}

@Composable
fun QuickVisitOption(icon: String, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Text(icon, fontSize = 20.sp)
        androidx.compose.material3.Text(label, style = AppTheme.typography.body2)
    }
}

@Composable
fun AnimatedPermissionPrompt(onGrant: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(48.dp))
        androidx.compose.material3.Text("Location Permission Required", style = AppTheme.typography.h3)
        Button(onClick = onGrant) { androidx.compose.material3.Text("Grant") }
    }
}

@Composable
fun EnhancedMapLegend(
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    clientCounts: Map<VisitStatus, Int>,
    filteredStatuses: Set<VisitStatus>,
    onFilterChange: (VisitStatus) -> Unit,
    agentCount: Int? = null
) {
    Column(
        modifier = modifier
            .width(260.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colors.surface.copy(alpha = 0.98f))
            .border(1.dp, AppTheme.colors.outline.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle), horizontalArrangement = Arrangement.SpaceBetween) {
            androidx.compose.material3.Text("Legend", fontWeight = FontWeight.Bold, color = AppTheme.colors.text)
            Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (isExpanded) "Collapse legend" else "Expand legend", tint = AppTheme.colors.textSecondary)
        }
        if (isExpanded) {
            VisitStatus.values().forEach { status ->
                EnhancedLegendItem(
                    label = status.name.replace("_", " ").lowercase().capitalize(),
                    color = when(status) {
                        VisitStatus.NEVER_VISITED -> Color(0xFFEF4444)
                        VisitStatus.RECENT -> Color(0xFF10B981)
                        VisitStatus.MODERATE -> Color(0xFFF59E0B)
                        VisitStatus.OVERDUE -> Color(0xFFF97316)
                    },
                    count = clientCounts[status] ?: 0,
                    isEnabled = filteredStatuses.contains(status),
                    onClick = { onFilterChange(status) }
                )
            }
            agentCount?.let {
                EnhancedLegendItem(label = "Live agents", color = Color(0xFF3B82F6), count = it, isEnabled = true, onClick = {})
            }
        }
    }
}

@Composable
fun EnhancedLegendItem(label: String, color: Color, count: Int, isEnabled: Boolean, onClick: () -> Unit, showCount: Boolean = true) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(if (isEnabled) color else color.copy(alpha = 0.2f)))
            androidx.compose.material3.Text(label, style = AppTheme.typography.body2)
        }
        if (showCount) androidx.compose.material3.Text("$count")
    }
}

@Composable
fun ExpenseTypeCard(icon: String, title: String, description: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(8.dp)) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            androidx.compose.material3.Text(icon, fontSize = 24.sp)
            Column {
                androidx.compose.material3.Text(title, fontWeight = FontWeight.Bold)
                androidx.compose.material3.Text(description, style = AppTheme.typography.body2)
            }
        }
    }
}

@Composable
fun AdminFilterChip(text: String, isSelected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = isSelected, onClick = onClick, label = { androidx.compose.material3.Text(text) })
}

@Composable
fun AgentRosterSheet(agents: List<com.bluemix.clients_lead.domain.repository.AgentLocation>, selectedAgent: com.bluemix.clients_lead.domain.repository.AgentLocation?, onAgentClick: (com.bluemix.clients_lead.domain.repository.AgentLocation) -> Unit, onDismiss: () -> Unit) {
     LazyColumn {
         items(agents) { agent ->
             AgentRosterItem(agent = agent, isSelected = agent.id == selectedAgent?.id, onClick = { onAgentClick(agent) })
         }
     }
}

@Composable
fun AgentRosterItem(agent: com.bluemix.clients_lead.domain.repository.AgentLocation, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isSelected) AppTheme.colors.primary.copy(alpha = 0.08f) else Color.Transparent)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val isOnline = com.bluemix.clients_lead.core.common.utils.DateTimeUtils.isRecent(agent.timestamp)
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isOnline) Color(0xFF10B981) else AppTheme.colors.textDisabled)
        )
        androidx.compose.material3.Text(
            text = agent.fullName ?: agent.email,
            color = if (isSelected) AppTheme.colors.primary else AppTheme.colors.text,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
