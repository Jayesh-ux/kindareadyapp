package com.bluemix.clients_lead.features.timesheet.presentation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemix.clients_lead.domain.model.LocationLog
import com.bluemix.clients_lead.domain.model.ClientService
import com.bluemix.clients_lead.domain.model.Meeting
import com.bluemix.clients_lead.domain.model.MeetingStatus
import com.bluemix.clients_lead.features.timesheet.vm.ActivityViewModel
import com.bluemix.clients_lead.features.expense.presentation.components.MiniRouteMap
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.koin.androidx.compose.koinViewModel
import ui.AppTheme
import ui.components.Scaffold
import ui.components.topbar.TopBar
import ui.components.topbar.TopBarDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // 4 tabs: Logs | Summary | Meetings | Services
    val tabs = listOf("Logs", "Summary", "Meetings", "Services")

    Scaffold(
        modifier = Modifier.background(AppTheme.colors.background),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopBar(
                colors = TopBarDefaults.topBarColors(
                    containerColor = AppTheme.colors.background,
                    scrolledContainerColor = AppTheme.colors.surface
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ui.components.Text(
                            text = "Activity Center",
                            style = AppTheme.typography.h2,
                            color = AppTheme.colors.text
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Admin toggle: All Agents / My Activity
                            if (uiState.isAdmin) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (uiState.showAllAgents) Color(0xFF10B981).copy(alpha = 0.2f) else AppTheme.colors.surface)
                                        .clickable { viewModel.toggleAllAgents() }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(
                                            imageVector = if (uiState.showAllAgents) Icons.Default.Groups else Icons.Default.Person,
                                            contentDescription = null,
                                            tint = if (uiState.showAllAgents) Color(0xFF10B981) else AppTheme.colors.textSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (uiState.showAllAgents) "All" else "Me",
                                            fontSize = 11.sp,
                                            color = if (uiState.showAllAgents) Color(0xFF10B981) else AppTheme.colors.textSecondary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Stats Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AppTheme.colors.primary.copy(alpha = 0.1f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                ui.components.Text(
                                    text = when (uiState.selectedTab) {
                                        0 -> "${uiState.logs.size} Logs"
                                        1 -> "${String.format("%.1f", uiState.totalDistanceKm)} KM"
                                        2 -> "${uiState.meetings.size} Meetings"
                                        else -> "${uiState.services.size} Services"
                                    },
                                    style = AppTheme.typography.label2,
                                    color = AppTheme.colors.primary
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // 4-tab row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppTheme.colors.surface)
                            .padding(4.dp)
                    ) {
                        tabs.forEachIndexed { index, label ->
                            TabItem(
                                text = label,
                                isSelected = uiState.selectedTab == index,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.onTabSelected(index) }
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Crossfade(
            targetState = when {
                uiState.isLoading && uiState.logs.isEmpty() && uiState.services.isEmpty() && uiState.expenses.isEmpty() -> "loading"
                uiState.selectedTab == 0 && uiState.logs.isEmpty() -> "empty_logs"
                uiState.selectedTab == 1 && uiState.logs.isEmpty() && uiState.expenses.isEmpty() -> "empty_summary"
                uiState.selectedTab == 2 && uiState.meetings.isEmpty() -> "empty_meetings"
                uiState.selectedTab == 3 && uiState.services.isEmpty() -> "empty_services"
                else -> "content"
            },
            label = "activityCrossfade"
        ) { state ->
            when (state) {
                "loading" -> LoadingContent(paddingValues)
                "empty_logs" -> EmptyContent(paddingValues, "No location logs yet", "Enable location tracking to see history")
                "empty_summary" -> EmptyContent(paddingValues, "No summary available", "Start tracking to build your daily summary")
                "empty_meetings" -> EmptyContent(paddingValues, "No meetings today", "Start a meeting from the Clients screen")
                "empty_services" -> EmptyContent(paddingValues, "No services assigned", "Assigned client services will appear here")
                "content" -> {
                    when (uiState.selectedTab) {
                        0 -> AnimatedActivityContent(paddingValues, uiState.logs, viewModel)
                        1 -> DailySummaryContent(paddingValues, uiState)
                        2 -> MeetingsContent(paddingValues, uiState.meetings)
                        3 -> ServicesContent(paddingValues, uiState.services, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabItem(text: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val backgroundColor by animateColorAsState(
        if (isSelected) AppTheme.colors.primary else Color.Transparent,
        label = "tabBackground"
    )
    val textColor by animateColorAsState(
        if (isSelected) Color.White else AppTheme.colors.textSecondary,
        label = "tabText"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = textColor, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
    }
}

// ============================================================
// MEETINGS TAB
// ============================================================

@Composable
private fun MeetingsContent(
    paddingValues: PaddingValues,
    meetings: List<Meeting>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(meetings) { _, meeting ->
            MeetingCard(meeting)
        }
    }
}

@Composable
private fun MeetingCard(meeting: Meeting) {
    val isCompleted = meeting.status == MeetingStatus.COMPLETED
    val isInProgress = meeting.status == MeetingStatus.IN_PROGRESS

    val statusColor = when (meeting.status) {
        MeetingStatus.COMPLETED   -> Color(0xFF10B981)
        MeetingStatus.IN_PROGRESS -> Color(0xFFFBBC04)
        MeetingStatus.CANCELLED   -> Color(0xFFEF4444)
    }

    val startFormatted = remember(meeting.startTime) {
        try {
            val instant = Instant.parse(meeting.startTime)
            instant.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("hh:mm a"))
        } catch (e: Exception) { meeting.startTime }
    }

    val endFormatted = remember(meeting.endTime) {
        meeting.endTime?.let {
            try {
                val instant = Instant.parse(it)
                instant.atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("hh:mm a"))
            } catch (e: Exception) { it }
        }
    }

    val durationText = remember(meeting.startTime, meeting.endTime) {
        if (meeting.endTime != null) {
            try {
                val startMs = Instant.parse(meeting.startTime).toEpochMilli()
                val endMs = Instant.parse(meeting.endTime).toEpochMilli()
                val mins = (endMs - startMs) / 60000
                if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
            } catch (e: Exception) { null }
        } else null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colors.surface)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header row: status dot + status text + time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = meeting.status.name.replace("_", " "),
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = startFormatted,
                    style = AppTheme.typography.label2,
                    color = AppTheme.colors.textSecondary
                )
            }

            // Client ID row (backend returns clientId; we show it since clientName isn't in the domain model) 
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.Store,
                    contentDescription = null,
                    tint = AppTheme.colors.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Client Visit",
                    style = AppTheme.typography.body2,
                    color = AppTheme.colors.text,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Time range + duration
            if (endFormatted != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = AppTheme.colors.textSecondary, modifier = Modifier.size(14.dp))
                        Text(
                            text = "$startFormatted → $endFormatted",
                            style = AppTheme.typography.label3,
                            color = AppTheme.colors.textSecondary
                        )
                    }
                    durationText?.let {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AppTheme.colors.primary.copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(it, color = AppTheme.colors.primary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            } else if (isInProgress) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.RadioButtonChecked, contentDescription = null, tint = statusColor, modifier = Modifier.size(14.dp))
                    Text("Meeting in progress since $startFormatted", style = AppTheme.typography.label3, color = statusColor)
                }
            }

            // Comments (if any)
            if (!meeting.comments.isNullOrBlank()) {
                androidx.compose.material3.HorizontalDivider(color = AppTheme.colors.outline.copy(alpha = 0.3f))
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Comment, contentDescription = null, tint = AppTheme.colors.textSecondary, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                    Text(
                        text = meeting.comments,
                        style = AppTheme.typography.body3,
                        color = AppTheme.colors.textSecondary
                    )
                }
            }
        }
    }
}

// ============================================================
// SERVICES TAB
// ============================================================

@Composable
private fun ServicesContent(
    paddingValues: PaddingValues,
    services: List<ClientService>,
    viewModel: ActivityViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(services.size) { index ->
            val service = services[index]
            ServiceCard(service, onAccept = { viewModel.acceptService(service.id) })
        }
    }
}

@Composable
private fun ServiceCard(service: ClientService, onAccept: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Build, contentDescription = null, tint = AppTheme.colors.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(service.name, style = AppTheme.typography.h3, color = AppTheme.colors.text)
                Spacer(Modifier.weight(1f))
                StatusChip(service.status)
            }

            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.HorizontalDivider(color = AppTheme.colors.outline.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))

            InfoRow(Icons.Default.Person, "Client", service.clientName)
            InfoRow(Icons.Default.Event, "Starts", service.startDate)
            InfoRow(Icons.Default.EventAvailable, "Expires", service.expiryDate)

            if (service.status.equals("active", ignoreCase = true).not()) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onAccept,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primary)
                ) {
                    Text("Accept Assignment", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val color = when (status.lowercase()) {
        "active" -> Color(0xFF10B981)
        "expiring" -> Color(0xFFFBBC04)
        else -> AppTheme.colors.textDisabled
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(status.uppercase(), color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(icon, contentDescription = null, tint = AppTheme.colors.textSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = AppTheme.colors.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = AppTheme.colors.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LoadingContent(paddingValues: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = AppTheme.colors.primary)
    }
}

@Composable
private fun EmptyContent(paddingValues: PaddingValues, title: String, subtitle: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(64.dp), tint = AppTheme.colors.textDisabled)
            Spacer(Modifier.height(16.dp))
            Text(title, style = AppTheme.typography.h3, color = AppTheme.colors.text)
            Text(subtitle, style = AppTheme.typography.body2, color = AppTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun AnimatedActivityContent(paddingValues: PaddingValues, logs: List<LocationLog>, viewModel: ActivityViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var expanded by remember { mutableStateOf(false) }

    // ✅ NEW: Trigger pagination when reaching end of list
    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItemsCount = listState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItemIndex >= totalItemsCount - 5 && !uiState.isLoading && !uiState.isEndReached
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadDailySummary(isRefresh = false)
        }
    }

    // ✅ Agent Selector at top
    if (uiState.isAdmin && uiState.agents.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = true },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2C3A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Filter by Agent",
                                style = AppTheme.typography.label3,
                                color = Color(0xFF9AA4B2)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = uiState.selectedAgent?.email ?: "All Agents",
                                style = AppTheme.typography.body1,
                                color = Color.White
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                    }
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(Color(0xFF1A2C3A))
                ) {
                    // "All Agents" option
                    DropdownMenuItem(
                        text = { Text("All Agents", color = Color.White) },
                        onClick = {
                            viewModel.selectAgent(null)
                            expanded = false
                        },
                        leadingIcon = { Icon(Icons.Default.Group, contentDescription = null, tint = Color(0xFF10B981)) }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    // Individual agents
                    uiState.agents.forEach { agent ->
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Text(agent.email ?: agent.fullName ?: "Unknown", color = Color.White)
                                    Text(
                                        "ID: ${agent.id.take(8)}...", 
                                        style = AppTheme.typography.label3, 
                                        color = Color(0xFF9AA4B2)
                                    )
                                }
                            },
                            onClick = {
                                viewModel.selectAgent(agent)
                                expanded = false
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (agent.email ?: "?").take(1).uppercase(),
                                        color = Color(0xFF10B981),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (uiState.isAdmin && uiState.showAllAgents) {
            // ✅ Bifurcate by email for Admins
            val groupedLogs = logs.groupBy { it.userEmail ?: "Unknown Agent (${it.userId})" }
            
            groupedLogs.forEach { (email, agentLogs) ->
                item(key = email) {
                    // Agent Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.1f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = email,
                            style = AppTheme.typography.h4,
                            color = Color(0xFF10B981)
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${agentLogs.size} logs",
                            style = AppTheme.typography.label3,
                            color = AppTheme.colors.textSecondary
                        )
                    }
                }
                
                itemsIndexed(agentLogs, key = { _, log -> log.id }) { index, log ->
                    TimelineLocationLogItem(log, index, index == agentLogs.lastIndex && uiState.isEndReached, viewModel)
                }
            }
        } else {
            // ✅ Standard single-agent timeline (or filtered by selected agent)
            itemsIndexed(logs, key = { _, log -> log.id }) { index, log ->
                TimelineLocationLogItem(log, index, index == logs.lastIndex && uiState.isEndReached, viewModel)
            }
        }

        // ✅ Loading Indicator at bottom
        if (uiState.isLoading && logs.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = AppTheme.colors.primary,
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineLocationLogItem(log: LocationLog, index: Int, isLast: Boolean, viewModel: ActivityViewModel) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(32.dp)) {
            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(AppTheme.colors.primary))
            if (!isLast) {
                Box(modifier = Modifier.width(2.dp).height(100.dp).background(AppTheme.colors.primary.copy(alpha = 0.2f)))
            }
        }
        AnimatedLogCard(log, index, viewModel)
    }
}

@Composable
private fun AnimatedLogCard(log: LocationLog, index: Int, viewModel: ActivityViewModel) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, bottom = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colors.surface)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = com.bluemix.clients_lead.core.common.utils.DateTimeUtils.formatTime(log.timestamp),
                    style = AppTheme.typography.h4,
                    color = AppTheme.colors.text
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Show User Email if Admin is viewing all logs
                    if (viewModel.uiState.collectAsState().value.isAdmin && !log.userEmail.isNullOrBlank()) {
                        Text(
                            text = log.userEmail,
                            style = AppTheme.typography.label3,
                            color = AppTheme.colors.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }

                    log.accuracy?.let { AccuracyBadge(it) }

                    // Battery indicator
                    val battery = log.battery
                    if (battery != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (battery > 20) Icons.Default.BatteryFull else Icons.Default.BatteryAlert,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = if (battery > 20) AppTheme.colors.primary else AppTheme.colors.error
                            )
                            Spacer(Modifier.width(2.dp))
                            Text("${battery}%", style = AppTheme.typography.label3, color = AppTheme.colors.textSecondary, fontSize = 10.sp)
                        }
                    }
                }
            }

            // Activity Marker if present
            if (!log.markActivity.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppTheme.colors.primary.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            imageVector = when(log.markActivity) {
                                "CLOCK_IN"      -> Icons.AutoMirrored.Filled.Login
                                "CLOCK_OUT"     -> Icons.AutoMirrored.Filled.Logout
                                "MEETING_START" -> Icons.Default.Handshake
                                "MEETING_END"   -> Icons.Default.DoneAll
                                else            -> Icons.Default.LocationOn
                            },
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = AppTheme.colors.primary
                        )
                        Text(
                            text = log.markActivity.replace("_", " "),
                            style = AppTheme.typography.label2,
                            color = AppTheme.colors.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (!log.markNotes.isNullOrBlank()) {
                Text(
                    text = log.markNotes,
                    style = AppTheme.typography.body3,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            val address = viewModel.uiState.collectAsState().value.resolvedAddresses[log.id]
            Text(
                text = if (address != null) "📍 $address" else "📍 Resolving address...",
                style = AppTheme.typography.label3,
                color = if (address != null) AppTheme.colors.textSecondary else AppTheme.colors.textDisabled
            )
        }
    }
}

@Composable
private fun AccuracyBadge(accuracy: Double) {
    val color = if (accuracy < 20) Color(0xFF10B981) else if (accuracy < 50) Color(0xFFFBBC04) else Color(0xFFEF4444)
    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.1f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text("±${accuracy.toInt()}m", color = color, fontSize = 10.sp)
    }
}

// ============================================================
// SUMMARY TAB
// ============================================================

@Composable
private fun DailySummaryContent(paddingValues: PaddingValues, uiState: com.bluemix.clients_lead.features.timesheet.vm.ActivityUiState) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Daily Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DailyStatCard(
                title = "Total Distance",
                value = "${String.format("%.1f", uiState.totalDistanceKm)} KM",
                icon = Icons.Default.Route,
                modifier = Modifier.weight(1f)
            )
            DailyStatCard(
                title = "Meetings Done",
                value = "${uiState.clientsVisitedCount}",
                icon = Icons.Default.Handshake,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DailyStatCard(
                title = "Expenses",
                value = "₹ ${String.format("%.2f", uiState.totalExpenseAmount)}",
                icon = Icons.Default.CurrencyRupee,
                modifier = Modifier.weight(1f)
            )
            DailyStatCard(
                title = "Active Time",
                value = formatDuration(uiState.activeDurationMinutes),
                icon = Icons.Default.Timer,
                modifier = Modifier.weight(1f)
            )
        }

        // Today's meetings quick-list (if any)
        if (uiState.meetings.isNotEmpty()) {
            Text(
                text = "Today's Meetings",
                style = AppTheme.typography.h3,
                color = AppTheme.colors.text,
                modifier = Modifier.padding(top = 4.dp)
            )
            uiState.meetings.forEach { meeting ->
                MeetingCard(meeting)
            }
        }

        // Journey path map
        if (uiState.logs.isNotEmpty()) {
            Text(
                text = "Journey Path",
                style = AppTheme.typography.h3,
                color = AppTheme.colors.text,
                modifier = Modifier.padding(top = 8.dp)
            )

            val routePolyline = uiState.logs.map { LatLng(it.latitude, it.longitude) }
            val startLog = uiState.logs.first()
            val endLog = uiState.logs.last()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppTheme.colors.surface)
                    .padding(8.dp)
            ) {
                MiniRouteMap(
                    routePolyline = routePolyline,
                    startLocation = LatLng(startLog.latitude, startLog.longitude),
                    endLocation = LatLng(endLog.latitude, endLog.longitude),
                    distanceKm = uiState.totalDistanceKm,
                    durationMinutes = uiState.activeDurationMinutes.toInt(),
                    transportMode = "DAILY"
                )
            }
        }
    }
}

@Composable
private fun DailyStatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = AppTheme.colors.primary, modifier = Modifier.size(24.dp))
            Text(text = title, style = AppTheme.typography.label2, color = AppTheme.colors.textSecondary)
            Text(text = value, style = AppTheme.typography.h2, color = AppTheme.colors.primary)
        }
    }
}

private fun formatDuration(minutes: Long): String {
    val hrs = minutes / 60
    val mins = minutes % 60
    return if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
}
