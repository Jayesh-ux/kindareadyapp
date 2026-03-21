package com.bluemix.clients_lead.features.admin.presentation

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import com.bluemix.clients_lead.domain.model.LocationLog
import com.bluemix.clients_lead.domain.repository.AgentLocation
import com.bluemix.clients_lead.features.admin.vm.AdminJourneyViewModel
import com.bluemix.clients_lead.features.admin.vm.JourneyViewMode
import org.koin.androidx.compose.koinViewModel
import ui.AppTheme
import ui.components.Scaffold
import ui.components.Text
import ui.components.topbar.TopBar
import ui.components.topbar.TopBarDefaults
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminJourneyScreen(
    agentId: String? = null,
    viewModel: AdminJourneyViewModel = koinViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(agentId, uiState.agents) {
        if (agentId != null && uiState.agents.isNotEmpty() && uiState.selectedAgent == null) {
            val agent = uiState.agents.find { it.id == agentId }
            if (agent != null) {
                viewModel.selectAgent(agent)
            }
        }
    }

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
                        text = "Journey Tracking & Reports",
                        style = AppTheme.typography.h2,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { /* Export Logic */ },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Export", style = AppTheme.typography.label3, color = Color.White)
                        }
                        IconButton(onClick = { viewModel.selectDate(uiState.selectedDate) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFF020617)
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Selection Controls Section
            item {
                SelectionCard(
                    uiState = uiState,
                    onSelectAgent = { viewModel.selectAgent(it) },
                    onSelectDate = { showDatePicker = true },
                    onViewModeChange = { viewModel.setViewMode(it) }
                )
            }

            // Stats Dashboard
            item {
                StatsDashboard(uiState = uiState)
            }

            // Main Content Area (Map or Summary)
            item {
                when (uiState.viewMode) {
                    JourneyViewMode.MAP -> MapSection(uiState)
                    JourneyViewMode.SUMMARY -> SummarySection(uiState)
                }
            }
            
            // Log List (Always visible at bottom for detailed drill-down)
            item {
                Text(
                    text = "Raw Activity Logs",
                    style = AppTheme.typography.h3,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            
            if (uiState.logs.isEmpty()) {
                item {
                    EmptyLogsPlaceholder()
                }
            } else {
                items(uiState.logs.reversed()) { log ->
                    JourneyLogCard(log = log, uiState = uiState)
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.selectedDate.toEpochDay() * 24 * 60 * 60 * 1000
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        viewModel.selectDate(date)
                    }
                    showDatePicker = false
                }) { Text("Confirm", color = Color(0xFF3B82F6)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun AgentTab(
    agent: AgentLocation,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) Color(0xFF3B82F6) else Color(0xFF1E293B)
    val contentColor = if (isSelected) Color.White else Color.Gray

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(30.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = agent.fullName ?: agent.email.split("@")[0],
            style = AppTheme.typography.body2,
            color = contentColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SelectionCard(
    uiState: com.bluemix.clients_lead.features.admin.vm.AdminJourneyUiState,
    onSelectAgent: (AgentLocation) -> Unit,
    onSelectDate: () -> Unit,
    onViewModeChange: (JourneyViewMode) -> Unit
) {
    Surface(
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Agent Selector Dropdown-style
            Column {
                Text("Select Agent", style = AppTheme.typography.label3, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.agents) { agent ->
                        AgentTab(
                            agent = agent,
                            isSelected = uiState.selectedAgent?.id == agent.id,
                            onClick = { onSelectAgent(agent) }
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Date Selector
                Column(modifier = Modifier.weight(1f)) {
                    Text("Selected Date", style = AppTheme.typography.label3, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E293B))
                            .clickable(onClick = onSelectDate)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(uiState.selectedDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")), color = Color.White)
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // View Mode
                Column(modifier = Modifier.weight(1f)) {
                    Text("View Mode", style = AppTheme.typography.label3, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E293B))
                            .padding(2.dp)
                    ) {
                        ViewModeButton(
                            text = "Map View",
                            isSelected = uiState.viewMode == JourneyViewMode.MAP,
                            modifier = Modifier.weight(1f),
                            onClick = { onViewModeChange(JourneyViewMode.MAP) }
                        )
                        ViewModeButton(
                            text = "Summary",
                            isSelected = uiState.viewMode == JourneyViewMode.SUMMARY,
                            modifier = Modifier.weight(1f),
                            onClick = { onViewModeChange(JourneyViewMode.SUMMARY) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewModeButton(text: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) Color(0xFF3B82F6) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (isSelected) Color.White else Color.Gray, style = AppTheme.typography.label3)
    }
}

@Composable
private fun StatsDashboard(uiState: com.bluemix.clients_lead.features.admin.vm.AdminJourneyUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = "TOTAL DISTANCE",
                value = String.format("%.1f km", uiState.totalDistanceKm),
                subtext = "Total traveled",
                icon = Icons.Default.Directions,
                iconTint = Color(0xFF8B5CF6),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "DURATION",
                value = formatMinutes(uiState.activeDurationMinutes),
                subtext = "Active time",
                icon = Icons.Default.Timer,
                iconTint = Color(0xFF10B981),
                modifier = Modifier.weight(1f)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = "CLIENTS VISITED",
                value = "${uiState.clientsVisited}",
                subtext = "of ${uiState.plannedClients} planned",
                icon = Icons.Default.CheckCircle,
                iconTint = Color(0xFF3B82F6),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "LOCATION LOGS",
                value = "${uiState.gpsPointCount}",
                subtext = "GPS points",
                icon = Icons.Default.Timeline,
                iconTint = Color(0xFFF43F5E),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, subtext: String, icon: ImageVector, iconTint: Color, modifier: Modifier) {
    Surface(
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(label, style = AppTheme.typography.label3, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                Text(value, style = AppTheme.typography.h2, color = Color.White)
                Text(subtext, style = AppTheme.typography.label3, color = Color.Gray)
            }
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun MapSection(uiState: com.bluemix.clients_lead.features.admin.vm.AdminJourneyUiState) {
    val points = uiState.logs.map { LatLng(it.latitude, it.longitude) }
    val cameraPositionState = rememberCameraPositionState {
        position = if (points.isNotEmpty()) {
            CameraPosition.fromLatLngZoom(points.first(), 14f)
        } else {
            CameraPosition.fromLatLngZoom(LatLng(19.0, 72.0), 10f)
        }
    }

    LaunchedEffect(points) {
        if (points.isNotEmpty()) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(points.first(), 14f)
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().height(300.dp),
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(16.dp)
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(zoomControlsEnabled = false),
            properties = MapProperties(mapStyleOptions = MapStyleOptions(MapStyles.DARK_STYLE))
        ) {
            if (points.isNotEmpty()) {
                Polyline(
                    points = points,
                    color = Color(0xFF3B82F6),
                    width = 8f
                )
                
                // ✅ Key Markers
                uiState.logs.forEachIndexed { index, log ->
                    // Show start/end markers explicitly
                    if (index == 0) {
                        Marker(
                            state = MarkerState(position = LatLng(log.latitude, log.longitude)),
                            title = "Journey Start",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                        )
                    } else if (index == uiState.logs.size - 1) {
                         Marker(
                            state = MarkerState(position = LatLng(log.latitude, log.longitude)),
                            title = "Journey End",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                        )
                    }
                    
                    // Show Meeting and Clock Start/End markers
                    if (!log.markActivity.isNullOrBlank()) {
                        val hue = when(log.markActivity) {
                            "MEETING_START" -> BitmapDescriptorFactory.HUE_AZURE
                            "MEETING_END" -> BitmapDescriptorFactory.HUE_BLUE
                            "CLOCK_IN" -> BitmapDescriptorFactory.HUE_VIOLET
                            "CLOCK_OUT" -> BitmapDescriptorFactory.HUE_MAGENTA
                            else -> null
                        }
                        
                        hue?.let { h ->
                            Marker(
                                state = MarkerState(position = LatLng(log.latitude, log.longitude)),
                                title = log.markActivity.replace("_", " "),
                                snippet = log.markNotes,
                                icon = BitmapDescriptorFactory.defaultMarker(h)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummarySection(uiState: com.bluemix.clients_lead.features.admin.vm.AdminJourneyUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Timeline Header
        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Journey Overview", style = AppTheme.typography.h4, color = Color.White)
                
                if (uiState.logs.isNotEmpty()) {
                    val start = uiState.logs.first()
                    val end = uiState.logs.last()
                    
                    SummaryItem(
                        icon = Icons.Default.PlayArrow,
                        iconTint = Color(0xFF10B981),
                        title = "Start Location",
                        time = com.bluemix.clients_lead.core.common.utils.DateTimeUtils.formatTimeOnly(start.timestamp),
                        address = uiState.resolvedAddresses[start.id] ?: "Resolving..."
                    )
                    
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    
                    SummaryItem(
                        icon = Icons.Default.Flag,
                        iconTint = Color(0xFFF43F5E),
                        title = "End Location",
                        time = com.bluemix.clients_lead.core.common.utils.DateTimeUtils.formatTimeOnly(end.timestamp),
                        address = uiState.resolvedAddresses[end.id] ?: "Resolving..."
                    )
                } else {
                    Text("No journey data for summary", color = Color.Gray, style = AppTheme.typography.body2)
                }
            }
        }
        
        // Visit Summary
        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Visit Summary", style = AppTheme.typography.h4, color = Color.White)
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VisitStatMiniCard("Completed", "${uiState.clientsVisited}", Color(0xFF10B981), Modifier.weight(1f))
                    VisitStatMiniCard("In Progress", "${uiState.logs.count { it.markActivity == "MEETING_START" } - uiState.clientsVisited}", Color(0xFFFBBC04), Modifier.weight(1f))
                    VisitStatMiniCard("Planned", "${uiState.plannedClients}", Color(0xFF3B82F6), Modifier.weight(1f))
                }
            }
        }

        // Expenses Summary
        if (uiState.expenses.isNotEmpty()) {
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Expenses", style = AppTheme.typography.h4, color = Color.White)
                        Text(
                            text = "₹${String.format("%.2f", uiState.totalExpenses)}",
                            style = AppTheme.typography.h3,
                            color = Color(0xFF10B981)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    uiState.expenses.forEach { expense ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = expense.startLocation ?: "Unknown",
                                    color = Color.White,
                                    style = AppTheme.typography.body3
                                )
                                Text(
                                    text = "${expense.transportMode} · ${String.format("%.1f", expense.distanceKm)} km",
                                    color = Color.Gray,
                                    style = AppTheme.typography.label3
                                )
                            }
                            Text(
                                text = "₹${String.format("%.0f", expense.amountSpent)}",
                                color = Color(0xFF10B981),
                                style = AppTheme.typography.body2
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryItem(icon: ImageVector, iconTint: Color, title: String, time: String, address: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(iconTint.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        }
        Column {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(title, style = AppTheme.typography.label2, color = iconTint)
                Text(time, style = AppTheme.typography.body2, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Text(address, style = AppTheme.typography.label3, color = Color.Gray)
        }
    }
}

@Composable
private fun VisitStatMiniCard(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.1f)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(if (label == "Completed") Icons.Default.CheckCircle else if (label == "In Progress") Icons.Default.Timer else Icons.Default.Cancel, 
            contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(value, style = AppTheme.typography.h3, color = Color.White)
        Text(label, style = AppTheme.typography.label3, color = Color.Gray)
    }
}

@Composable
private fun EmptyLogsPlaceholder() {
    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Route, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(8.dp))
            Text("No movement logs found for this period", color = Color.Gray, style = AppTheme.typography.body3)
        }
    }
}

private fun formatMinutes(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private object MapStyles {
    val DARK_STYLE = """
        [
          { "elementType": "geometry", "stylers": [{ "color": "#212121" }] },
          { "elementType": "labels.icon", "stylers": [{ "visibility": "off" }] },
          { "elementType": "labels.text.fill", "stylers": [{ "color": "#757575" }] },
          { "elementType": "labels.text.stroke", "stylers": [{ "color": "#212121" }] },
          { "featureType": "administrative", "elementType": "geometry", "stylers": [{ "color": "#757575" }] },
          { "featureType": "administrative.country", "elementType": "labels.text.fill", "stylers": [{ "color": "#9e9e9e" }] },
          { "featureType": "landscape", "elementType": "geometry", "stylers": [{ "color": "#121212" }] },
          { "featureType": "poi", "elementType": "geometry", "stylers": [{ "color": "#141414" }] },
          { "featureType": "road", "elementType": "geometry.fill", "stylers": [{ "color": "#2c2c2c" }] },
          { "featureType": "road.highway", "elementType": "geometry", "stylers": [{ "color": "#3c3c3c" }] },
          { "featureType": "water", "elementType": "geometry", "stylers": [{ "color": "#000000" }] }
        ]
    """.trimIndent()
}

@Composable
private fun JourneyLogCard(log: LocationLog, uiState: com.bluemix.clients_lead.features.admin.vm.AdminJourneyUiState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F172A))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = com.bluemix.clients_lead.core.common.utils.DateTimeUtils.formatTimeOnly(log.timestamp),
                    style = AppTheme.typography.h4,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Battery
                    if (log.battery != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                if ((log.battery ?: 0) > 20) Icons.Default.BatteryFull else Icons.Default.BatteryAlert,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = if ((log.battery ?: 0) > 20) Color(0xFF10B981) else Color(0xFFEF4444)
                            )
                            Text("${log.battery}%", color = Color.Gray, style = AppTheme.typography.label3)
                        }
                    }

                    // Accuracy Badge
                    val accColor = when {
                        (log.accuracy ?: 100.0) < 20.0 -> Color(0xFF10B981)
                        (log.accuracy ?: 100.0) < 50.0 -> Color(0xFFFBBC04)
                        else -> Color(0xFFEF4444)
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(accColor.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("±${log.accuracy?.toInt() ?: "?"}m", color = accColor, style = AppTheme.typography.label3)
                    }
                }
            }

            // ✅ NEW: User-friendly address
            val address = uiState.resolvedAddresses[log.id]
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                Text(
                    text = address ?: "Resolving address...",
                    style = AppTheme.typography.body3,
                    color = if (address != null) Color.White else Color.Gray,
                    fontWeight = if (address != null) FontWeight.Medium else FontWeight.Normal
                )
            }

            // ✅ NEW: Activity and Notes for Admin
            if (!log.markActivity.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF3B82F6).copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = when(log.markActivity) {
                            "CLOCK_IN" -> Icons.Default.Login
                            "CLOCK_OUT" -> Icons.Default.Logout
                            "MEETING_START" -> Icons.Default.Handshake
                            "MEETING_END" -> Icons.Default.DoneAll
                            else -> Icons.Default.LocationOn
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF3B82F6)
                    )
                    Text(
                        text = log.markActivity.replace("_", " "),
                        style = AppTheme.typography.label2,
                        color = Color(0xFF3B82F6),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (!log.markNotes.isNullOrBlank()) {
                Text(
                    text = log.markNotes,
                    style = AppTheme.typography.body3,
                    color = Color.Gray,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}
