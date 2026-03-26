package com.bluemix.clients_lead.features.map.presentation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.google.maps.android.compose.rememberMarkerState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.zIndex
import com.bluemix.clients_lead.domain.model.Client
import com.bluemix.clients_lead.domain.model.VisitStatus
import com.bluemix.clients_lead.features.expense.presentation.MultiLegTripExpenseSheet
import com.bluemix.clients_lead.features.expense.presentation.TripExpenseSheet
import com.bluemix.clients_lead.features.location.LocationSettingsMonitor
import com.bluemix.clients_lead.features.map.vm.MapViewModel
import com.bluemix.clients_lead.features.map.vm.MapUiState
import com.bluemix.clients_lead.features.meeting.presentation.MeetingBottomSheet
import com.bluemix.clients_lead.features.meeting.utils.ProximityDetector
import com.bluemix.clients_lead.features.meeting.vm.MeetingViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber
import ui.AppTheme
import ui.components.Scaffold
import ui.components.Text
import ui.components.topbar.TopBar
import ui.components.topbar.TopBarDefaults
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.material3.*
import com.bluemix.clients_lead.core.common.utils.MapUtils
import com.bluemix.clients_lead.R



// Default location (Mumbai, India) - Initial camera position before user location loads
private val DefaultLocation = LatLng(19.0760, 72.8777)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel = koinViewModel(),
    meetingViewModel: MeetingViewModel = koinViewModel(),
    onNavigateToClientDetail: (String) -> Unit = {},
    onNavigateToAgentDetail: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val meetingUiState by meetingViewModel.uiState.collectAsState()
    val context = LocalContext.current // ✅ Added context for CSV sharing
    var showMeetingSheet by remember { mutableStateOf(false) }
    var proximityClient by remember { mutableStateOf<Client?>(null) }
    var lastProximityCheck by remember { mutableStateOf(0L) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var hasAutoFocusedOnUser by remember { mutableStateOf(false) }
    
    // ✅ Pulsing Animation for Selected Agent/Live Tracking
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing")
    val pulseSize by infiniteTransition.animateFloat(
        initialValue = 10f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseSize"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )
    
    // ✅ FIX #12: Unified state for expense sheets
    var activeExpenseType by remember { mutableStateOf<ExpenseSheetType?>(null) }
    var showExpenseTypeDialog by remember { mutableStateOf(false) }
    // 🔹 Map legend filter state
    var isLegendExpanded by remember { mutableStateOf(false) }
    var filteredStatuses by remember {
        mutableStateOf(
            setOf(
                VisitStatus.NEVER_VISITED,
                VisitStatus.RECENT,
                VisitStatus.MODERATE,
                VisitStatus.OVERDUE
            )
        )
    }

    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DefaultLocation, 16f)
    }
    BackHandler {
        showExitDialog = true
    }

    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )


    val locationSettingsMonitor = remember {
        LocationSettingsMonitor(context)
    }


    DisposableEffect(Unit) {
        locationSettingsMonitor.startMonitoring()
        onDispose {
            locationSettingsMonitor.stopMonitoring()
        }
    }

    val isLocationEnabled by locationSettingsMonitor.isLocationEnabled.collectAsState()

    // Show dialog when location is disabled
    if (!isLocationEnabled && uiState.isTrackingEnabled) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Location Services Disabled") },
            text = {
                Text("Please enable location services from your device settings to continue using the app.")
            },
            confirmButton = {
                Button(onClick = {
                    // Open location settings
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { /* Handle later */ }) {
                    Text("Later")
                }
            }
        )
    }



    LaunchedEffect(locationPermissions.allPermissionsGranted) {
        if (locationPermissions.allPermissionsGranted) {
            viewModel.refreshTrackingState()
        }
    }
    // Auto-center camera on user's location the first time we get it
    // Works for both admins and agents (no isTrackingEnabled gate)
    LaunchedEffect(uiState.currentLocation) {
        if (uiState.currentLocation != null && !hasAutoFocusedOnUser) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(
                        uiState.currentLocation!!.latitude,
                        uiState.currentLocation!!.longitude
                    ),
                    16.5f
                ),
                durationMs = 1000
            )
            hasAutoFocusedOnUser = true
        }
    }

    // ✅ Move camera to search results - RESPONSIVE AND SENSITIVE
    LaunchedEffect(uiState.filteredClients) {
        if (uiState.searchQuery.isNotEmpty() && uiState.filteredClients.isNotEmpty()) {
            val firstClient = uiState.filteredClients.firstOrNull()
            if (firstClient?.latitude != null && firstClient.longitude != null) {
                try {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(firstClient.latitude, firstClient.longitude),
                            15f
                        )
                    )
                } catch (e: Exception) {
                    Timber.e("Camera animation failed: ${e.message}")
                }
            }
        }
    }

    // ✅ FIX #3: Proximity detection now only triggers UI feedback, not automatic sheet pops.
    LaunchedEffect(uiState.currentLocation, uiState.clients) {
        if (uiState.currentLocation != null && uiState.clients.isNotEmpty()) {
            uiState.clients.forEach { client ->
                val isNewEntry = ProximityDetector.isWithinProximity(
                    currentLocation = uiState.currentLocation!!,
                    client = client,
                    radiusMeters = 50.0
                )
                // We let the MeetingBottomSheet handle the button state internally.
            }
        }
    }

    // ✅ NEW: Show sheet when a client is manually selected on the map
    LaunchedEffect(uiState.selectedClient) {
        if (uiState.selectedClient != null) {
            proximityClient = uiState.selectedClient
            showMeetingSheet = true
            meetingViewModel.checkActiveMeeting(uiState.selectedClient!!.id)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Map",
                            style = AppTheme.typography.h2,
                            color = AppTheme.colors.text
                        )

                        // ✅ FIX #4: Continuous rotation while refreshing (No snapping)
                        val infiniteTransition = rememberInfiniteTransition(label = "refreshLoading")
                        val rotation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable<Float>(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "rotation"
                        )
                        

                        IconButton(
                            onClick = {
                                isRefreshing = true
                                viewModel.refresh()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = AppTheme.colors.text,
                                modifier = Modifier.graphicsLayer { 
                                    rotationZ = if (isRefreshing || uiState.isLoading) rotation else 0f 
                                }
                            )
                        }

                        IconButton(onClick = { viewModel.logout() }) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.ExitToApp,
                                contentDescription = "Logout",
                                tint = AppTheme.colors.error
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(
                        isMyLocationEnabled = locationPermissions.permissions.any { it.status.isGranted },
                        mapType = MapType.NORMAL
                    ),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = false,
                        myLocationButtonEnabled = true,
                        compassEnabled = true,
                        mapToolbarEnabled = false
                    )
                ) {
                    val currentZoom = cameraPositionState.position.zoom
                    val showMarkers = uiState.isAdmin || uiState.isTrackingEnabled
                    val showClientMarkers = showMarkers && (currentZoom >= 13.0f || uiState.searchQuery.isNotEmpty())

                    if (showMarkers) {
                        if (uiState.clients.isEmpty() && uiState.agents.isEmpty() && !uiState.isLoading) {
                            Timber.w("🗺️ MAP EMPTY: No clients found.")
                        } else {
                            Timber.d("🗺️ RENDERING MARKERS - Clients: ${uiState.filteredClients.size}, Agents: ${uiState.agents.size}")
                        }

                        if (showClientMarkers) {
                            uiState.filteredClients.forEachIndexed { index, client ->
                                if (client.latitude != null && client.longitude != null) {
                                    val visitStatus = client.getVisitStatusColor()

                                    if (filteredStatuses.contains(visitStatus)) {
                                        val position = LatLng(client.latitude, client.longitude)

                                        val markerColor = when (visitStatus) {
                                            VisitStatus.NEVER_VISITED -> BitmapDescriptorFactory.HUE_RED
                                            VisitStatus.RECENT -> BitmapDescriptorFactory.HUE_GREEN
                                            VisitStatus.MODERATE -> BitmapDescriptorFactory.HUE_YELLOW
                                            VisitStatus.OVERDUE -> BitmapDescriptorFactory.HUE_ORANGE
                                        }

                                        val visitInfo = client.getFormattedLastVisit()?.let { "Last visit: $it" }
                                            ?: "Never visited"

                                        val snippet = buildString {
                                            append(visitInfo)
                                            client.address?.let {
                                                append(" • ")
                                                append(it)
                                            }
                                        }

                                        // ✅ FIXED: Stable marker rendering with internal key
                                        androidx.compose.runtime.key(client.id) {
                                            val markerState = rememberMarkerState(position = position)
                                            markerState.position = position // ✅ Update position on recomposition
                                            
                                            Marker(
                                                state = markerState,
                                                title = client.name,
                                                snippet = snippet,
                                                icon = BitmapDescriptorFactory.defaultMarker(markerColor),
                                                onClick = {
                                                    viewModel.selectClient(client)
                                                    true
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ✅ NEW: Render Agents (Admins only)
                        if (uiState.isAdmin && uiState.agents.isNotEmpty()) {
                            uiState.agents
                                .filter { agent ->
                                    // Apply Online Filter
                                    val isOnline = agent.latitude != null && agent.longitude != null
                                    if (uiState.showOnlineAgentsOnly && !isOnline) return@filter false
                                    
                                    // Apply High Accuracy Filter (< 50m)
                                    val isHighAcc = (agent.accuracy ?: 100.0) < 50.0
                                    if (uiState.showHighAccuracyOnly && !isHighAcc) return@filter false
                                    
                                    true
                                }
                                .forEach { agent ->
                                if (agent.latitude != null && agent.longitude != null) {
                                    val position = LatLng(agent.latitude, agent.longitude)
                                    
                                    // Blue marker for agents, dimmed if offline
                                    val isOnlineNow = com.bluemix.clients_lead.core.common.utils.DateTimeUtils.isRecent(agent.timestamp)
                                    val isInMeeting = agent.activity?.contains("meeting", ignoreCase = true) == true
                                    
                                    // ✅ FIX #3/6: Stable agent markers - Move state inside KEY
                                    androidx.compose.runtime.key("agent-${agent.id}") {
                                        val markerState = rememberMarkerState(position = position)
                                        markerState.position = position // ✅ Update position on recomposition
                                        
                                        Marker(
                                            state = markerState,
                                            title = agent.fullName ?: agent.email,
                                            snippet = when {
                                                isInMeeting -> "Status: In Meeting"
                                                isOnlineNow -> "Status: Active • ${agent.activity ?: "Moving"}"
                                                else -> "Status: Last seen ${com.bluemix.clients_lead.core.common.utils.DateTimeUtils.formatLastSeen(agent.timestamp)}"
                                            },
                                            icon = if (isOnlineNow) {
                                                MapUtils.vectorToBitmap(context, R.drawable.ic_marker_live)
                                            } else {
                                                MapUtils.vectorToBitmap(context, R.drawable.ic_marker_clock_in)
                                            } ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                                            onClick = {
                                                viewModel.selectAgent(agent)
                                                true
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // ✅ NEW: Pulsing Effect for Selected Agent (only if online)
                        uiState.selectedAgent?.let { agent ->
                            if (agent.latitude != null && agent.longitude != null) {
                                val isOnlineForPulse = com.bluemix.clients_lead.core.common.utils.DateTimeUtils.isRecent(agent.timestamp)
                                if (isOnlineForPulse) {
                                    Circle(
                                        center = LatLng(agent.latitude, agent.longitude),
                                        radius = pulseSize.toDouble(),
                                        fillColor = Color(0xFF3B82F6).copy(alpha = pulseAlpha),
                                        strokeColor = Color(0xFF3B82F6).copy(alpha = pulseAlpha),
                                        strokeWidth = 2f
                                    )
                                }
                            }
                        }

                        // ✅ NEW: Render Whole-Day Journey Overlay for Selected Agent
                        if (uiState.selectedAgent != null && uiState.selectedAgentJourney.isNotEmpty()) {
                            val journeyPoints = uiState.selectedAgentJourney.map { LatLng(it.latitude, it.longitude) }
                            
                            // 1. Draw the Travel Path
                            Polyline(
                                points = journeyPoints,
                                color = Color(0xFF3B82F6).copy(alpha = 0.6f),
                                width = 10f,
                                jointType = com.google.android.gms.maps.model.JointType.ROUND,
                                startCap = com.google.android.gms.maps.model.RoundCap(),
                                endCap = com.google.android.gms.maps.model.RoundCap()
                            )
                            
                            // 2. Activity Markers (Meetings, Clock In/Out)
                            uiState.selectedAgentJourney.forEach { log ->
                                if (!log.markActivity.isNullOrBlank()) {
                                    val icon = when(log.markActivity) {
                                        "MEETING_START" -> MapUtils.vectorToBitmap(context, R.drawable.ic_marker_meeting_start)
                                        "MEETING_END" -> MapUtils.vectorToBitmap(context, R.drawable.ic_marker_meeting_end)
                                        "CLOCK_IN" -> MapUtils.vectorToBitmap(context, R.drawable.ic_marker_clock_in)
                                        "CLOCK_OUT", "LOGOUT", "JOURNEY_STOP" -> MapUtils.vectorToBitmap(context, R.drawable.ic_marker_stop)
                                        "JOURNEY_START", "AT_CLIENT_SITE" -> MapUtils.vectorToBitmap(context, R.drawable.ic_marker_start)
                                        else -> null
                                    } ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                                    
                                    Marker(
                                        state = com.google.maps.android.compose.MarkerState(position = LatLng(log.latitude, log.longitude)),
                                        title = log.markActivity.replace("_", " "),
                                        snippet = log.markNotes ?: "At ${com.bluemix.clients_lead.core.common.utils.DateTimeUtils.formatTimeOnly(log.timestamp)}",
                                        icon = icon,
                                        alpha = 0.9f,
                                        zIndex = 10f
                                    )
                                }
                            }
                        }
                    }
                }

                // ✅ Search Result Indicator / Error Message
                uiState.error?.let { error ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = 100.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        androidx.compose.material3.Text(
                            text = error,
                            color = Color.White,
                            style = AppTheme.typography.body2
                        )
                    }
                }

                // ✅ Tracking Required Warning (Agents only)
                if (!uiState.isAdmin && !uiState.isTrackingEnabled && !uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 32.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppTheme.colors.surface)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.LocationOff,
                                contentDescription = null,
                                tint = AppTheme.colors.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Tracking Required",
                                style = AppTheme.typography.h3,
                                color = AppTheme.colors.text
                            )
                            Text(
                                text = "Please enable location tracking to view clients in your territory.",
                                style = AppTheme.typography.body2,
                                color = AppTheme.colors.textSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { viewModel.startClockIn() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppTheme.colors.primary
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Text("Clock In / Start Work", color = Color.White)
                                }
                            }
                        }
                    }
                }


                // ✅ TOP UI CONTAINER (MANAGED STACK)
                // This prevents Search, Filters, and Status badges from overlapping each other.
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                        .zIndex(3.0f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Floating Search Bar
                    AnimatedVisibility(
                        visible = (uiState.isAdmin || uiState.isTrackingEnabled),
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ui.components.textfield.TextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.onSearchQueryChanged(it) },
                                placeholder = { Text("Search clients by name, address, or pincode...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AppTheme.colors.surface),
                                leadingIcon = {
                                    if (uiState.isSearchingRemote) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = AppTheme.colors.primary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Search",
                                            tint = AppTheme.colors.textSecondary
                                        )
                                    }
                                },
                                trailingIcon = {
                                    if (uiState.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear search",
                                                tint = AppTheme.colors.textSecondary
                                            )
                                        }
                                    }
                                }
                            )
                            
                            // ✅ Client Search Dropdown Results
                            AnimatedVisibility(
                                visible = uiState.searchQuery.isNotEmpty() && uiState.filteredClients.isNotEmpty()
                            ) {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AppTheme.colors.surface)
                                        .border(1.dp, AppTheme.colors.outline, RoundedCornerShape(12.dp))
                                        .heightIn(max = 280.dp)
                                ) {
                                    // ✅ FIX #6 Refined: Use constant for display count to avoid magic numbers
                                    val displayCount = 5
                                    itemsIndexed(uiState.filteredClients.take(displayCount)) { index, client ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.selectClient(client)
                                                    coroutineScope.launch {
                                                        cameraPositionState.animate(
                                                            update = CameraUpdateFactory.newLatLngZoom(
                                                                LatLng(client.latitude ?: 0.0, client.longitude ?: 0.0),
                                                                16f
                                                            ),
                                                            durationMs = 800
                                                        )
                                                    }
                                                    viewModel.onSearchQueryChanged("") 
                                                }
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LocationOn, 
                                                contentDescription = null, 
                                                tint = AppTheme.colors.primary, 
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = client.name, 
                                                    style = AppTheme.typography.body1, 
                                                    fontWeight = FontWeight.Bold, 
                                                    color = AppTheme.colors.text,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = client.address ?: "No address provided", 
                                                    style = AppTheme.typography.body3, 
                                                    color = AppTheme.colors.textSecondary, 
                                                    maxLines = 1, 
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        // ✅ FIX #7: Robust divider logic
                                        if (index < displayCount - 1 && index < uiState.filteredClients.size - 1) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(0.5.dp)
                                                    .background(AppTheme.colors.outline.copy(alpha = 0.5f))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Status Indicator Row (Clocked-In / Online Now)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Side: Status Badges for Agent
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Tracking Active indicator - renamed to "Online" for clarity
                            AnimatedVisibility(
                                visible = uiState.isTrackingEnabled,
                                enter = fadeIn() + scaleIn(),
                                exit = fadeOut() + scaleOut()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(AppTheme.colors.success.copy(alpha = 0.15f))
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.size(8.dp).clip(CircleShape).background(AppTheme.colors.success)
                                        )
                                        Text(
                                            text = "Online / Tracking Active",
                                            style = AppTheme.typography.label2,
                                            color = AppTheme.colors.success
                                        )
                                    }
                                }
                            }
                        }

                        // Right Side: Admin Indicator (Online Count)
                        if (uiState.isAdmin) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(0xFF0F172A).copy(alpha = 0.8f))
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF10B981)))
                                    androidx.compose.material3.Text("${uiState.onlineAgentsCount} Live", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }


                    // ✅ FIXED OVERLAP: Legend inside Managed Stack
                    AnimatedVisibility(
                        visible = uiState.isTrackingEnabled || uiState.isAdmin,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        // ✅ FIX #5: Optimization - Calculate client counts once when clients list changes.
                        val clientCounts = remember(uiState.clients) {
                            mapOf(
                                VisitStatus.NEVER_VISITED to uiState.clients.count { it.getVisitStatusColor() == VisitStatus.NEVER_VISITED },
                                VisitStatus.RECENT to uiState.clients.count { it.getVisitStatusColor() == VisitStatus.RECENT },
                                VisitStatus.MODERATE to uiState.clients.count { it.getVisitStatusColor() == VisitStatus.MODERATE },
                                VisitStatus.OVERDUE to uiState.clients.count { it.getVisitStatusColor() == VisitStatus.OVERDUE }
                            )
                        }

                        EnhancedMapLegend(
                            isExpanded = isLegendExpanded,
                            onToggle = { isLegendExpanded = !isLegendExpanded },
                            clientCounts = clientCounts,
                            filteredStatuses = filteredStatuses,
                            onFilterChange = { status ->
                                filteredStatuses = if (filteredStatuses.contains(status)) {
                                    filteredStatuses - status
                                } else {
                                    filteredStatuses + status
                                }
                            },
                            agentCount = if (uiState.isAdmin) uiState.agents.size else null
                        )
                    }
                }



                // Meeting Bottom Sheet - ONLY FOR AGENTS (Highest priority z-index 2)
                AnimatedVisibility(
                    visible = showMeetingSheet && proximityClient != null && !uiState.isAdmin && uiState.isTrackingEnabled,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(2f),
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    proximityClient?.let { client ->
                        MeetingBottomSheet(
                            client = client,
                            activeMeeting = meetingUiState.activeMeeting,
                            isLoading = meetingUiState.isLoading,
                            pendingAttachments = meetingUiState.pendingAttachments,
                            isUploadingAttachments = meetingUiState.isUploadingAttachments,
                            proximityState = meetingUiState.proximityState,
                            error = meetingUiState.error,
                            activeJourneyClientId = uiState.activeJourneyClientId,
                            currentLocation = uiState.currentLocation,
                            comments = meetingUiState.comments, // ✅ NEW
                            onCommentsChange = { meetingViewModel.updateComments(it) }, // ✅ NEW
                            onStartJourney = { clientId, mode -> viewModel.startJourney(clientId, mode) },
                            onStopJourney = { viewModel.stopJourney() },
                            onStartMeeting = {
                                meetingViewModel.startMeeting(
                                    clientId = client.id,
                                    latitude = uiState.currentLocation?.latitude,
                                    longitude = uiState.currentLocation?.longitude,
                                    accuracy = uiState.currentLocationAccuracy?.toDouble()
                                )
                                // Auto-stop journey when meeting starts
                                if (uiState.activeJourneyClientId == client.id) {
                                    viewModel.stopJourney()
                                }
                            },
                            onEndMeeting = { comments, clientStatus ->
                                meetingViewModel.endMeeting(comments, clientStatus)
                                showMeetingSheet = false
                                proximityClient = null
                                ProximityDetector.resetProximityState(client.id)
                            },
                            onAddAttachment = { uri ->
                                meetingViewModel.addAttachment(uri)
                            },
                            onRemoveAttachment = { attachment ->
                                meetingViewModel.removeAttachment(attachment)
                            },
                            onClearError = { meetingViewModel.clearError() }, // ✅ NEW
                            onDismiss = {
                                showMeetingSheet = false
                                proximityClient = null
                                meetingViewModel.clearError() // Auto-clear on close
                            }
                        )
                    }
                }

                // Agent Bottom Sheet (Admins only)
                AnimatedVisibility(
                    visible = uiState.selectedAgent != null && uiState.isAdmin,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(1.5f),
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    uiState.selectedAgent?.let { agent ->
                        AnimatedAgentBottomSheet(
                            agent = agent,
                            onClose = { viewModel.selectAgent(null) },
                            onViewProfile = { onNavigateToAgentDetail(it) }
                        )
                    }
                }

                // Client Bottom Sheet - Visible when a client is selected
                AnimatedVisibility(
                    visible = uiState.selectedClient != null && (uiState.isAdmin || uiState.isTrackingEnabled) && !showMeetingSheet,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(5f)
                ) {
                    uiState.selectedClient?.let { selectedClient ->
                        AnimatedClientBottomSheet(
                            client = selectedClient,
                            uiState = uiState,
                            viewModel = viewModel,
                            cameraPositionState = cameraPositionState,
                            onClose = { viewModel.selectClient(null) },
                            onViewDetails = { selectedClient.id.let(onNavigateToClientDetail) },
                            onStartMeeting = {
                                viewModel.selectClient(null)
                                proximityClient = selectedClient
                                showMeetingSheet = true
                                meetingViewModel.checkActiveMeeting(selectedClient.id)
                            },
                            meetingUiState = meetingUiState, // ✅ Pass meeting state
                            onQuickVisit = { status ->
                                viewModel.updateQuickVisitStatus(selectedClient.id, status)
                            }
                        )
                    }
                }


                // Territory/Info Banner
                AnimatedVisibility(
                    visible = uiState.territoryMessage != null,
                    modifier = Modifier.align(Alignment.TopCenter),
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppTheme.colors.primary.copy(alpha = 0.9f))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = AppTheme.colors.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = uiState.territoryMessage ?: "",
                                style = AppTheme.typography.body2,
                                color = AppTheme.colors.onPrimary
                            )
                        }
                    }
                }

                // ✅ NEW: Hidden Clients Banner (Coordinate missing)
                AnimatedVisibility(
                    visible = uiState.hiddenClientsCount > 0 && !uiState.isLoading && uiState.selectedClient == null && uiState.selectedAgent == null && !showMeetingSheet,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp)
                        .zIndex(1.0f),
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOff,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${uiState.hiddenClientsCount} clients hidden (missing GPS)",
                                style = AppTheme.typography.body3,
                                color = Color.White
                            )
                        }
                    }
                }

                // Error Banner
                AnimatedVisibility(
                    visible = uiState.error != null,
                    modifier = Modifier.align(Alignment.TopCenter),
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = if (uiState.territoryMessage != null) 72.dp else 16.dp, start = 16.dp, end = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppTheme.colors.error.copy(alpha = 0.9f))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = AppTheme.colors.onError,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = uiState.error ?: "Unknown error",
                                style = AppTheme.typography.body2,
                                color = AppTheme.colors.onError
                            )
                        }
                    }
                }

                // Loading Card
                AnimatedVisibility(
                    visible = uiState.isLoading,
                    modifier = Modifier.align(Alignment.Center),
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppTheme.colors.surface)
                            .padding(24.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = AppTheme.colors.primary
                            )
                            Text(
                                text = "Loading clients...",
                                style = AppTheme.typography.body1,
                                color = AppTheme.colors.text
                            )
                        }
                    }
                }


                // Floating Action Button - Changed to Receipt icon (HIDDEN FOR ADMINS)
                AnimatedVisibility(
                    visible = uiState.isTrackingEnabled && !uiState.isAdmin,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 16.dp, end = 16.dp),
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    ui.components.FloatingActionButton(
                        onClick = { showExpenseTypeDialog = true },
                        icon = Icons.Default.Receipt,
                        contentDescription = "Add Trip Expense"
                    )
                }

                // Permission Prompt
                AnimatedVisibility(
                    visible = !locationPermissions.allPermissionsGranted,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    AnimatedPermissionPrompt(
                        onGrant = { locationPermissions.launchMultiplePermissionRequest() }
                    )
                }

                // Full-screen Tracking Warning (Non-admins only)
                if (!uiState.isTrackingEnabled && !uiState.isAdmin) {
                    TrackingRequiredOverlay(
                        modifier = Modifier.fillMaxSize(),
                        onEnableTracking = {
                            if (!isLocationEnabled) {
                                context.startActivity(
                                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                )
                            } else {
                                viewModel.startClockIn()
                            }
                        },
                        onRefreshStatus = { viewModel.refreshTrackingState() }
                    )
                }

                // Clock Out Button (Visible when tracking is ON)
                AnimatedVisibility(
                    visible = uiState.isTrackingEnabled && !uiState.isAdmin,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 16.dp, start = 16.dp),
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    androidx.compose.material3.ExtendedFloatingActionButton(
                        onClick = { viewModel.stopClockOut() },
                        containerColor = AppTheme.colors.error,
                        contentColor = Color.White,
                        icon = { Icon(Icons.Default.Logout, "Clock Out") },
                        text = { Text("Clock Out") },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }

        // Expense Sheet Modal
        when (activeExpenseType) {
            ExpenseSheetType.SINGLE -> TripExpenseSheet(onDismiss = { activeExpenseType = null })
            ExpenseSheetType.MULTI -> MultiLegTripExpenseSheet(onDismiss = { activeExpenseType = null })
            null -> { /* Nothing shown */ }
        }

        // ✅ FIX #10: Expense Type Selection Dialog (Now at top level)
        if (showExpenseTypeDialog) {
            AlertDialog(
                onDismissRequest = { showExpenseTypeDialog = false },
                containerColor = AppTheme.colors.surface,
                shape = RoundedCornerShape(24.dp),
                title = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = null,
                            tint = AppTheme.colors.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Add Trip Expense",
                            style = AppTheme.typography.h3,
                            color = AppTheme.colors.text,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Choose your trip type",
                            style = AppTheme.typography.body2,
                            color = AppTheme.colors.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ExpenseTypeCard(
                            icon = "🚌",
                            title = "Single Trip",
                            description = "One-way journey with single transport",
                            onClick = {
                                showExpenseTypeDialog = false
                                activeExpenseType = ExpenseSheetType.SINGLE
                            }
                        )
                        ExpenseTypeCard(
                            icon = "✈️",
                            title = "Multi-Leg Journey",
                            description = "Multiple transports in one trip",
                            onClick = {
                                showExpenseTypeDialog = false
                                activeExpenseType = ExpenseSheetType.MULTI
                            }
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(
                        onClick = { showExpenseTypeDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Cancel",
                            style = AppTheme.typography.button,
                            color = AppTheme.colors.textSecondary
                        )
                    }
                }
            )
        }

    }

    // ✅ FIX #4: Unified error management. 
    // Clearing of errors is now primarily handled by the bottom sheet's onDismiss/onClearError.
    // This LaunchedEffect is now for logging or side-effects only.
    LaunchedEffect(meetingUiState.error) {
        meetingUiState.error?.let { error ->
            Timber.e("Meeting validation failed: $error")
        }
    }

    if (showExitDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = {
                Text(
                    text = "Exit GeoTrack?",
                    style = AppTheme.typography.h3,
                    color = AppTheme.colors.text
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to exit the app?",
                    style = AppTheme.typography.body1,
                    color = AppTheme.colors.textSecondary
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        // ✅ FIX #2: Graceful activity finish with logging
                        activity?.finish() ?: Timber.e("Screen Exit Error: Context is not an Activity")
                    }
                ) {
                    Text(
                        text = "Exit",
                        style = AppTheme.typography.button,
                        color = AppTheme.colors.error
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showExitDialog = false }
                ) {
                    Text(
                        text = "Cancel",
                        style = AppTheme.typography.button,
                        color = AppTheme.colors.primary
                    )
                }
            },
            containerColor = AppTheme.colors.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun TrackingRequiredOverlay(
    modifier: Modifier = Modifier,
    onEnableTracking: () -> Unit,
    onRefreshStatus: () -> Unit
) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .padding(top = 120.dp, start = 24.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.LocationOff,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = Color(0xFF5E92F3)
            )

            Text(
                text = "Location tracking required",
                style = AppTheme.typography.h3,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            // Shortened description
            Text(
                text = "Background location access is required to show nearby clients and verify your working area.",
                style = AppTheme.typography.body2,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                TrackingBenefitItem("Verify you are in the correct service area")
                TrackingBenefitItem("Show clients near your location")
                TrackingBenefitItem("Securely log field visits")
                TrackingBenefitItem("Prevent unauthorized access")
            }

            Button(
                onClick = onEnableTracking,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981), // Emerald for Clock In
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Clock In / Start Work",
                    style = AppTheme.typography.button
                )
            }

            OutlinedButton(
                onClick = onRefreshStatus,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                border = BorderStroke(
                    1.dp,
                    Color.White.copy(alpha = 0.35f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Refresh tracking status",
                    style = AppTheme.typography.button,
                    color = Color.White
                )
            }


            // Shortened footer
            Text(
                text = "Your location is used only to verify visits and is never shared.",
                style = AppTheme.typography.body2,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun TrackingBenefitItem(text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "•",
            style = AppTheme.typography.body1,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.padding(end = 8.dp)
        )

        Text(
            text = text,
            style = AppTheme.typography.body2,
            color = Color.White.copy(alpha = 0.75f)
        )
    }
}


    @Composable
    private fun AnimatedClientBottomSheet(
        client: Client,
        uiState: MapUiState,
        viewModel: MapViewModel,
        cameraPositionState: CameraPositionState,
        onClose: () -> Unit,
        onViewDetails: () -> Unit,
        onStartMeeting: () -> Unit,
        meetingUiState: com.bluemix.clients_lead.features.meeting.vm.MeetingUiState, // ✅ Added
        onQuickVisit: (String) -> Unit = {}
    ) {
        var showVisitStatusMenu by remember { mutableStateOf(false) }
        var isEditingAddress by remember { mutableStateOf(false) }
        var editedAddress by remember(client.address) { mutableStateOf(client.address ?: "") }

        // ✅ MOVE: Calculate distance at the TOP of the component so it's available everywhere
        val distanceMeters = remember(uiState.currentLocation, client) {
            val agentLoc = uiState.currentLocation
            val clientLat = client.latitude
            val clientLon = client.longitude

            if (clientLat == null || clientLon == null) 0.0 // Allow bootstrap
            else if (agentLoc == null) Double.MAX_VALUE
            else ProximityDetector.calculateDistance(agentLoc, LatLng(clientLat, clientLon))
        }

        val visitStatus = client.getVisitStatusColor()
        val lastVisit = client.getFormattedLastVisit()


        LaunchedEffect(uiState.updateError) {
            if (uiState.updateError != null) {
                kotlinx.coroutines.delay(3000)
                viewModel.clearUpdateError()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(AppTheme.colors.surface)
                .padding(20.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = client.name,
                            style = AppTheme.typography.h3,
                            color = AppTheme.colors.text,
                            fontWeight = FontWeight.Bold
                        )
                        // ✅ ADDED: Live distance readout in header to reduce confusion
                        Text(
                            text = if (uiState.currentLocation != null) 
                                "Current distance: ${ProximityDetector.formatDistance(distanceMeters)}" 
                                else "Calculating distance...",
                            style = AppTheme.typography.body2,
                            color = AppTheme.colors.primary,
                            fontWeight = FontWeight.Medium
                        )
                        // Visit status badge
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VisitStatusIndicator(visitStatus)
                            lastVisit?.let {
                                Text(
                                    text = it,
                                    style = AppTheme.typography.body2,
                                    color = AppTheme.colors.textSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = AppTheme.colors.textSecondary
                        )
                    }
                }

                // Address Section with Edit
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = AppTheme.colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Address",
                                style = AppTheme.typography.label2,
                                color = AppTheme.colors.textSecondary
                            )
                        }

                        IconButton(
                            onClick = { isEditingAddress = !isEditingAddress },
                            enabled = !uiState.isUpdatingAddress
                        ) {
                            Icon(
                                imageVector = if (isEditingAddress) Icons.Default.Close else Icons.Default.Edit,
                                contentDescription = if (isEditingAddress) "Cancel" else "Edit",
                                tint = AppTheme.colors.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = !isEditingAddress,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(
                            text = client.address ?: "No address",
                            style = AppTheme.typography.body2,
                            color = AppTheme.colors.text
                        )
                    }

                    AnimatedVisibility(
                        visible = isEditingAddress,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ui.components.textfield.TextField(
                                value = editedAddress,
                                onValueChange = { editedAddress = it },
                                placeholder = { Text("Enter new address") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isUpdatingAddress,
                                minLines = 2,
                                maxLines = 4
                            )

                            // ✅ NEW: Phase 4 Hint
                            Text(
                                text = "💡 Updating address will automatically retrieve new GPS coordinates.",
                                style = AppTheme.typography.label2,
                                color = AppTheme.colors.primary.copy(alpha = 0.8f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        isEditingAddress = false
                                        editedAddress = client.address ?: ""
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = !uiState.isUpdatingAddress
                                ) {
                                    Text("Cancel")
                                }

                                Button(
                                    onClick = {
                                        if (editedAddress.isNotBlank() && editedAddress != client.address) {
                                            viewModel.updateAddress(client.id, editedAddress)
                                            isEditingAddress = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = !uiState.isUpdatingAddress &&
                                            editedAddress.isNotBlank() &&
                                            editedAddress != client.address
                                ) {
                                    if (uiState.isUpdatingAddress) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("Update")
                                    }
                                }
                            }
                        }
                    }
                }

                // Error message
                AnimatedVisibility(
                    visible = uiState.updateError != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppTheme.colors.error.copy(alpha = 0.1f))
                            .padding(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = AppTheme.colors.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = uiState.updateError ?: "",
                                style = AppTheme.typography.body3,
                                color = AppTheme.colors.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(AppTheme.colors.onSurface.copy(alpha = 0.1f))
                )

                // Action Buttons
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // UI Rules: 
                    // 1. Show button within 80m (with slight jitter tolerance)
                    // 2. ONLY for non-admins (Agents/Users). Admins shouldn't start meetings.
                     // ✅ FIX #1: Unified 50m Rule. 
                     // Button and logic now strictly follow the 50m Proximity Shield requirement.
                    val isWithinProximity = distanceMeters <= 50.0 
                    val isLocationUnavailable = uiState.currentLocation == null
                    val canStartMeeting = isWithinProximity && !uiState.isAdmin // ✅ ADMINS BLOCKED

                    // Primary: Start Meeting (Visible if 80m of the client and NOT an Admin)
                    if (canStartMeeting && !isLocationUnavailable) {
                        Button(
                            onClick = onStartMeeting,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Handshake,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Start Meeting",
                                style = AppTheme.typography.button
                            )
                        }
                    } else {
                        // Informational warning with distance or status
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppTheme.colors.primary.copy(alpha = 0.08f))
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = AppTheme.colors.primary,
                                    modifier = Modifier.size(18.dp).padding(top = 2.dp)
                                )
                                Column {
                                    Text(
                                        text = when {
                                            isLocationUnavailable -> "Waiting for location..."
                                            uiState.isAdmin -> "Admin View - Read Only"
                                            else -> "Outside 50m Proximity Shield"
                                        },
                                        style = AppTheme.typography.label2,
                                        color = AppTheme.colors.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = when {
                                            isLocationUnavailable -> "Still fetching your current GPS coordinates. Please wait."
                                            uiState.isAdmin -> "You are logged in as an Admin. Meeting operations are restricted to Agents only."
                                            else -> "Move within 50m of the client. You are currently ${ProximityDetector.formatDistance(distanceMeters)} away."
                                        },
                                        style = AppTheme.typography.body3,
                                        color = AppTheme.colors.primary
                                    )
                                }
                            }
                        }
                    }

                    // ✅ NEW: Phase 1 GPS Tagging
                    // Visible to Agents only when client has missing coordinates
                    val isLocationMissing = client.latitude == null || client.longitude == null
                    if (isLocationMissing && !uiState.isAdmin && !isLocationUnavailable) {
                        Button(
                            onClick = { viewModel.tagLocation(client.id) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981) // Emerald Green
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tag Current GPS",
                                style = AppTheme.typography.button
                            )
                        }
                    }

                    // Secondary: Quick Visit Status (HIDDEN FOR ADMINS)
                    // ✅ MODIFIED: Only show if an active meeting is in progress for this client
                    val hasActiveMeeting = meetingUiState.activeMeeting != null && meetingUiState.activeMeeting?.clientId == client.id
                    
                    if (!uiState.isAdmin && hasActiveMeeting) {
                        OutlinedButton(
                            onClick = { showVisitStatusMenu = !showVisitStatusMenu },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = AppTheme.colors.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Mark as Visited",
                                style = AppTheme.typography.button
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                imageVector = if (showVisitStatusMenu) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Quick visit status options
                        AnimatedVisibility(
                            visible = showVisitStatusMenu,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AppTheme.colors.background)
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                QuickVisitOption(
                                    icon = "✅",
                                    label = "Client met successfully",
                                    onClick = {
                                        onQuickVisit("met_success")
                                        showVisitStatusMenu = false
                                    }
                                )
                                QuickVisitOption(
                                    icon = "🚫",
                                    label = "Client not available",
                                    onClick = {
                                        onQuickVisit("not_available")
                                        showVisitStatusMenu = false
                                    }
                                )
                                QuickVisitOption(
                                    icon = "🏢",
                                    label = "Office closed",
                                    onClick = {
                                        onQuickVisit("office_closed")
                                        showVisitStatusMenu = false
                                    }
                                )
                                QuickVisitOption(
                                    icon = "📞",
                                    label = "Spoke on phone",
                                    onClick = {
                                        onQuickVisit("phone_call")
                                        showVisitStatusMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Tertiary actions row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onViewDetails,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Details",
                                style = AppTheme.typography.button,
                                fontSize = 13.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (client.latitude != null && client.longitude != null) {
                                    cameraPositionState.move(
                                        CameraUpdateFactory.newLatLngZoom(
                                            LatLng(client.latitude, client.longitude),
                                            17f
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Focus",
                                style = AppTheme.typography.button,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun VisitStatusIndicator(status: VisitStatus) {
        val (backgroundColor, textColor, label) = when (status) {
            VisitStatus.NEVER_VISITED -> Triple(
                Color(0xFFEA4335).copy(alpha = 0.15f),
                Color(0xFFEA4335),
                "Never Visited"
            )
            VisitStatus.RECENT -> Triple(
                Color(0xFF34A853).copy(alpha = 0.15f),
                Color(0xFF34A853),
                "Recent"
            )
            VisitStatus.MODERATE -> Triple(
                Color(0xFFFBBC04).copy(alpha = 0.15f),
                Color(0xFFFBBC04),
                "Follow-up"
            )
            VisitStatus.OVERDUE -> Triple(
                Color(0xFFFF6D00).copy(alpha = 0.15f),
                Color(0xFFFF6D00),
                "Overdue"
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = label,
                style = AppTheme.typography.label2,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    @Composable
    private fun AnimatedAgentBottomSheet(
        agent: com.bluemix.clients_lead.domain.repository.AgentLocation,
        onClose: () -> Unit,
        onViewProfile: (String) -> Unit
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(AppTheme.colors.surface)
                .padding(20.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = agent.fullName ?: "Staff Member",
                            style = AppTheme.typography.h3,
                            color = AppTheme.colors.text,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = agent.email,
                            style = AppTheme.typography.body2,
                            color = AppTheme.colors.textSecondary
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Info Rows
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    agent.battery?.let { 
                        InfoTile(label = "Battery", value = "$it%", icon = Icons.Default.Info) 
                    }
                    agent.activity?.let {
                        InfoTile(label = "Activity", value = it, icon = Icons.Default.Directions)
                    }
                }

                agent.timestamp?.let {
                    Text(
                        text = "Last seen: ${com.bluemix.clients_lead.core.common.utils.DateTimeUtils.formatLastSeen(it)}",
                        style = AppTheme.typography.label2,
                        color = AppTheme.colors.textSecondary
                    )
                }

                Button(
                    onClick = { onViewProfile(agent.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Full Profile")
                }
            }
        }
    }

    @Composable
    private fun InfoTile(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = label, style = AppTheme.typography.label2, color = AppTheme.colors.textSecondary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = AppTheme.colors.primary)
                Text(text = value, style = AppTheme.typography.body2, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    @Composable
    private fun QuickVisitOption(
        icon: String,
        label: String,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                fontSize = 18.sp
            )
            Text(
                text = label,
                style = AppTheme.typography.body2,
                color = AppTheme.colors.text
            )
        }
    }
@Composable
private fun AnimatedPermissionPrompt(
    onGrant: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colors.surface)
            .padding(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    tint = AppTheme.colors.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Location permission required",
                    style = AppTheme.typography.body1,
                    color = AppTheme.colors.text
                )
            }

            Text(
                text = "Please grant location permission so we can show clients near you and start background tracking.",
                style = AppTheme.typography.body2,
                color = AppTheme.colors.textSecondary
            )

            Button(
                onClick = onGrant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Grant Permission",
                    style = AppTheme.typography.button
                )
            }
        }
    }
}

// ✅ FIX #11: Removed dead code (Old MapLegend and LegendItem)


@Composable
private fun EnhancedMapLegend(
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    clientCounts: Map<VisitStatus, Int>,
    filteredStatuses: Set<VisitStatus>,
    onFilterChange: (VisitStatus) -> Unit,
    agentCount: Int? = null // ✅ ADDED
) {
    Column(
        modifier = modifier
            .width(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colors.surface.copy(alpha = 0.98f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = AppTheme.colors.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Legend",
                    style = AppTheme.typography.label1,
                    color = AppTheme.colors.text,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Total count badge
        if (!isExpanded) {
            val total = clientCounts.values.sum()
            val text = if (agentCount != null) "$total clients • $agentCount agents" else "$total clients"
            Text(
                text = text,
                style = AppTheme.typography.body2,
                color = AppTheme.colors.textSecondary,
                fontSize = 11.sp
            )
        }

        // Expanded content
        AnimatedVisibility(visible = isExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(modifier = Modifier.height(2.dp))

                EnhancedLegendItem(
                    color = Color(0xFFEA4335),
                    label = "Never visited",
                    count = clientCounts[VisitStatus.NEVER_VISITED] ?: 0,
                    isEnabled = filteredStatuses.contains(VisitStatus.NEVER_VISITED),
                    onClick = { onFilterChange(VisitStatus.NEVER_VISITED) }
                )

                EnhancedLegendItem(
                    color = Color(0xFF34A853),
                    label = "Recent visit",
                    count = clientCounts[VisitStatus.RECENT] ?: 0,
                    isEnabled = filteredStatuses.contains(VisitStatus.RECENT),
                    onClick = { onFilterChange(VisitStatus.RECENT) }
                )

                EnhancedLegendItem(
                    color = Color(0xFFFBBC04),
                    label = "Follow-up soon",
                    count = clientCounts[VisitStatus.MODERATE] ?: 0,
                    isEnabled = filteredStatuses.contains(VisitStatus.MODERATE),
                    onClick = { onFilterChange(VisitStatus.MODERATE) }
                )

                EnhancedLegendItem(
                    color = Color(0xFFFF6D00),
                    label = "Overdue",
                    count = clientCounts[VisitStatus.OVERDUE] ?: 0,
                    isEnabled = filteredStatuses.contains(VisitStatus.OVERDUE),
                    onClick = { onFilterChange(VisitStatus.OVERDUE) }
                )

                if (agentCount != null) {
                    EnhancedLegendItem(
                        color = Color(0xFF00BCD4), // Cyan
                        label = "Live Status",
                        count = agentCount,
                        isEnabled = true,
                        onClick = { /* No filter for agents yet */ }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                androidx.compose.material3.HorizontalDivider(color = AppTheme.colors.onSurface.copy(alpha = 0.1f))
                Text(
                    text = "Activity Markers",
                    style = AppTheme.typography.label2,
                    color = AppTheme.colors.textSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                EnhancedLegendItem(
                    color = Color(0xFF4CAF50),
                    label = "Journey Start / Arrived",
                    count = 0,
                    isEnabled = true,
                    onClick = { },
                    showCount = false
                )
                EnhancedLegendItem(
                    color = Color(0xFFE53935),
                    label = "Journey End / Stop",
                    count = 0,
                    isEnabled = true,
                    onClick = { },
                    showCount = false
                )
                EnhancedLegendItem(
                    color = Color(0xFF03A9F4),
                    label = "Meeting Start",
                    count = 0,
                    isEnabled = true,
                    onClick = { },
                    showCount = false
                )
                EnhancedLegendItem(
                    color = Color(0xFF1565C0),
                    label = "Meeting End",
                    count = 0,
                    isEnabled = true,
                    onClick = { },
                    showCount = false
                )
                EnhancedLegendItem(
                    color = Color(0xFF7C4DFF),
                    label = "Clocked In / Person",
                    count = 0,
                    isEnabled = true,
                    onClick = { },
                    showCount = false
                )
            }
        }
    }
}


// ✅ FIX #12: State enum for expense sheets
private enum class ExpenseSheetType { SINGLE, MULTI }

// ✅ FIX #11: Removed dead code (Old MapLegend and LegendItem)


@Composable
private fun EnhancedLegendItem(
    color: Color,
    label: String,
    count: Int,
    isEnabled: Boolean,
    onClick: () -> Unit,
    showCount: Boolean = true // ✅ NEW
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(
                if (isEnabled) Color.Transparent
                else AppTheme.colors.background.copy(alpha = 0.5f)
            )
            .padding(vertical = 4.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (isEnabled) color else color.copy(alpha = 0.3f))
            )
            Text(
                text = label,
                style = AppTheme.typography.body2,
                color = if (isEnabled) AppTheme.colors.text else AppTheme.colors.textSecondary.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }

        // Count badge
        if (showCount) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = if (isEnabled) 0.15f else 0.05f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = count.toString(),
                    style = AppTheme.typography.label2,
                    color = if (isEnabled) color else color.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ExpenseTypeCard(
    icon: String,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = AppTheme.colors.background,
            contentColor = AppTheme.colors.text
        ),
        border = BorderStroke(1.dp, AppTheme.colors.primary.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AppTheme.colors.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = icon,
                    fontSize = 24.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text Content
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = title,
                    style = AppTheme.typography.body1,
                    color = AppTheme.colors.text,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = AppTheme.typography.body2,
                    color = AppTheme.colors.textSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}