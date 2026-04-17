package com.bluemix.clients_lead.features.map.presentation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.google.maps.android.compose.rememberMarkerState
import com.bluemix.clients_lead.features.map.components.*
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.Logout
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import kotlinx.coroutines.delay
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
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
import com.bluemix.clients_lead.core.common.utils.DateTimeUtils
import com.bluemix.clients_lead.core.common.utils.MapUtils
import com.bluemix.clients_lead.R
import com.bluemix.clients_lead.domain.repository.AgentLocation



// Default location (Mumbai, India) - Initial camera position before user location loads
private val DefaultLocation = LatLng(19.0760, 72.8777)

enum class ExpenseSheetType { SINGLE, MULTI }

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel = koinViewModel(),
    meetingViewModel: MeetingViewModel = koinViewModel(),
    onNavigateToClientDetail: (String) -> Unit = {},
    onNavigateToAgentDetail: (String) -> Unit = {}
) {
    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )
    val uiState by viewModel.uiState.collectAsState()
    val meetingUiState by meetingViewModel.uiState.collectAsState()
    val context = LocalContext.current // ✅ Added context for CSV sharing
    var showMeetingSheet by remember { mutableStateOf(false) }
    var proximityClient by remember { mutableStateOf<Client?>(null) }
    var lastProximityCheck by remember { mutableStateOf(0L) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var hasAutoFocusedOnUser by remember { mutableStateOf(false) }
    
    // ✅ Safe My Location Layer Enabling (Prevents race condition crash)
    var isMyLocationLayerEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(locationPermissions.allPermissionsGranted) {
        if (locationPermissions.allPermissionsGranted) {
            delay(800) // Safe delay for SDK to settle
            isMyLocationLayerEnabled = true
        }
    }
    
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
    var legendOffset by remember { mutableStateOf(Offset(0f, 0f)) } 
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
    val currentZoom = cameraPositionState.position.zoom
    val showMarkers = true // STRICT: Always show map/markers for logged-in agents and admins
    val clientCounts = remember(uiState.filteredClients) {
        calculateVisitStatusCounts(uiState.filteredClients)
    }
    val filteredAgentsForMap = remember(uiState.agents) {
        agentsWithPosition(uiState.agents)
    }
    val canShowClientMarkers = showMarkers && if (uiState.isAdmin) {
        uiState.showClients
    } else {
        // Optimized: Relaxed zoom limit (11.0) allows seeing territory from higher up while maintaining performance
        currentZoom >= 11.0f || uiState.searchQuery.isNotEmpty()
    }
    
    // Viewport-based culling logic: Optimized for maps-compose 4.x+
    // Use derivedStateOf and observe cameraPositionState.position to ensure it updates during pan/zoom
    val currentBounds by remember {
        derivedStateOf { 
            val _pos = cameraPositionState.position // Force dependency for reactivity
            cameraPositionState.projection?.visibleRegion?.latLngBounds 
        }
    }
    val visibleClientMarkers = if (canShowClientMarkers) {
        val bounds = currentBounds // Local copy for smart-casting delegated property
        uiState.filteredClients.filter { client ->
            val lat = client.latitude
            val lng = client.longitude
            lat != null && lng != null &&
            filteredStatuses.contains(client.getVisitStatusColor()) &&
            (bounds == null || bounds.contains(LatLng(lat, lng)))
        }
    } else {
        emptyList()
    }
    val liveAgentCount = filteredAgentsForMap.count { DateTimeUtils.isRecent(it.timestamp) }

    // Show dialog when location is disabled - IMPROVED UI
    if (!isLocationEnabled && !uiState.isAdmin) {
        AlertDialog(
            onDismissRequest = { },
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.LocationOff, null, tint = AppTheme.colors.error)
                    Text("Location Disabled", style = AppTheme.typography.h3)
                }
            },
            text = {
                Text(
                    "Your device's location services are turned off. We need GPS to track your territory and meetings.",
                    style = AppTheme.typography.body2
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppTheme.colors.primary,
                        contentColor = AppTheme.colors.onPrimary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Enable GPS", color = AppTheme.colors.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { /* Handle later */ }) {
                    Text("Not Now", color = AppTheme.colors.textSecondary)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = AppTheme.colors.surface
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
        val loc = uiState.currentLocation
        if (loc != null && !hasAutoFocusedOnUser) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(loc.latitude, loc.longitude),
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
        val currentLoc = uiState.currentLocation
        if (currentLoc != null && uiState.clients.isNotEmpty()) {
            uiState.clients.forEach { client ->
                val isNewEntry = ProximityDetector.isWithinProximity(
                    currentLocation = currentLoc,
                    client = client,
                    radiusMeters = 50.0
                )
                // We let the MeetingBottomSheet handle the button state internally.
            }
        }
    }

    // Show AnimatedClientBottomSheet when a client is selected (NOT MeetingBottomSheet automatically)
    // MeetingBottomSheet only opens when user explicitly clicks "Start Meeting"
    LaunchedEffect(uiState.selectedClient) {
        val selected = uiState.selectedClient
        if (selected != null) {
            proximityClient = selected
            // Don't auto-open MeetingBottomSheet - let user see AnimatedClientBottomSheet first
            // showMeetingSheet will be set to true only when user clicks "Start Meeting" in AnimatedClientBottomSheet
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
                            text = "Territory Map",
                            style = AppTheme.typography.h2.copy(letterSpacing = (-0.5).sp),
                            color = AppTheme.colors.text
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                                modifier = Modifier.size(44.dp),
                                onClick = {
                                    isRefreshing = true
                                    viewModel.refresh()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = AppTheme.colors.primary,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .graphicsLayer { 
                                            rotationZ = if (isRefreshing || uiState.isLoading) rotation else 0f 
                                        }
                                )
                            }

                            IconButton(
                                modifier = Modifier.size(44.dp),
                                onClick = { viewModel.logout() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Logout",
                                    tint = AppTheme.colors.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
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
                        isMyLocationEnabled = locationPermissions.allPermissionsGranted && isMyLocationLayerEnabled,
                        mapType = MapType.NORMAL
                    ),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = false,
                        myLocationButtonEnabled = true,
                        compassEnabled = true,
                        mapToolbarEnabled = false
                    )
                ) {
                    if (showMarkers) {
                        // 🔹 Rendering Markers
                        visibleClientMarkers.forEach { client ->
                            val lat = client.latitude
                            val lng = client.longitude
                            if (lat != null && lng != null) {
                                val visitStatus = client.getVisitStatusColor()
                                val isSelectedStatus = if (filteredStatuses.isEmpty()) true else filteredStatuses.contains(visitStatus)
                                
                                val isTargetedClient = uiState.selectedClient?.id == client.id
                                val isAgentFocusMode = uiState.selectedAgent != null
                                
                                if (isSelectedStatus && (!isAgentFocusMode || isTargetedClient)) {
                                    val position = LatLng(lat, lng)
                                    val markerColor = when (visitStatus) {
                                        VisitStatus.NEVER_VISITED -> BitmapDescriptorFactory.HUE_RED
                                        VisitStatus.RECENT -> BitmapDescriptorFactory.HUE_GREEN
                                        VisitStatus.MODERATE -> BitmapDescriptorFactory.HUE_YELLOW
                                        VisitStatus.OVERDUE -> BitmapDescriptorFactory.HUE_ORANGE
                                    }

                                    val visitInfo = client.getFormattedLastVisit()?.let { "Last visit: $it" } ?: "Never visited"
                                    val snippet = buildString {
                                        append(visitInfo)
                                        client.address?.let { append(" • $it") }
                                    }

                                    androidx.compose.runtime.key(client.id) {
                                        Marker(
                                            state = rememberMarkerState(position = position),
                                            title = client.name,
                                            snippet = snippet,
                                            icon = BitmapDescriptorFactory.defaultMarker(markerColor),
                                            onClick = {
                                                viewModel.selectClient(client)
                                                coroutineScope.launch {
                                                    cameraPositionState.animate(
                                                        update = CameraUpdateFactory.newLatLngZoom(position, 16f),
                                                        durationMs = 500
                                                    )
                                                }
                                                true // Prevent default InfoWindow - show custom bottom sheet instead
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 🔹 Render Agents (Admins only)
                        if (uiState.isAdmin) {
                            filteredAgentsForMap.forEach { agent ->
                                val lat = agent.latitude
                                val lng = agent.longitude
                                if (lat != null && lng != null) {
                                    val position = LatLng(lat, lng)
                                    val isOnlineNow = DateTimeUtils.isRecent(agent.timestamp)
                                    val isInMeeting = agent.currentActivity?.contains("meeting", ignoreCase = true) == true
                                    
                                    androidx.compose.runtime.key("agent-${agent.id}") {
                                        Marker(
                                            state = rememberMarkerState(position = position),
                                            title = agent.fullName ?: agent.email,
                                            snippet = when {
                                                isInMeeting -> agent.currentActivity ?: "In client visit"
                                                isOnlineNow -> agent.smartStatus ?: "Active now"
                                                else -> "Last seen ${DateTimeUtils.formatLastSeen(agent.timestamp)}"
                                            },
                                            icon = if (isOnlineNow) {
                                                MapUtils.vectorToBitmap(context, R.drawable.ic_marker_live)
                                            } else {
                                                MapUtils.vectorToBitmap(context, R.drawable.ic_marker_clock_in)
                                            } ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                                            onClick = {
                                                viewModel.selectAgent(agent)
                                                coroutineScope.launch {
                                                    cameraPositionState.animate(
                                                        update = CameraUpdateFactory.newLatLngZoom(position, 15f),
                                                        durationMs = 500
                                                    )
                                                }
                                                true // Prevent default InfoWindow - show custom bottom sheet instead
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 🔹 Pulsing Effect for Selected Agent
                        uiState.selectedAgent?.let { agent ->
                            val lat = agent.latitude
                            val lng = agent.longitude
                            if (lat != null && lng != null && DateTimeUtils.isRecent(agent.timestamp)) {
                                Circle(
                                    center = LatLng(lat, lng),
                                    radius = pulseSize.toDouble(),
                                    fillColor = Color(0xFF3B82F6).copy(alpha = pulseAlpha),
                                    strokeColor = Color(0xFF3B82F6).copy(alpha = pulseAlpha),
                                    strokeWidth = 2f
                                )
                            }
                        }

                        // 🔹 Render Journey Overlay
                        if (uiState.selectedAgent != null && uiState.selectedAgentJourney.isNotEmpty()) {
                            val journeyPoints = uiState.selectedAgentJourney.map { LatLng(it.latitude, it.longitude) }
                            
                            Polyline(
                                points = journeyPoints,
                                color = AppTheme.colors.primary.copy(alpha = 0.5f),
                                width = 8f,
                                jointType = com.google.android.gms.maps.model.JointType.ROUND
                            )
                            
                            uiState.selectedAgentJourney.forEach { log ->
                                if (!log.markActivity.isNullOrBlank()) {
                                    val logTime = com.bluemix.clients_lead.core.common.utils.DateTimeUtils.formatTimeOnly(log.timestamp)
                                    val clientName = log.clientName ?: "Office"
                                    
                                    val markerColor = when(log.markActivity) {
                                        "CLOCK_IN" -> BitmapDescriptorFactory.HUE_AZURE
                                        "MEETING_START" -> BitmapDescriptorFactory.HUE_GREEN
                                        "MEETING_END" -> BitmapDescriptorFactory.HUE_YELLOW
                                        "CLOCK_OUT", "LOGOUT" -> BitmapDescriptorFactory.HUE_RED
                                        else -> BitmapDescriptorFactory.HUE_CYAN
                                    }
                                    
                                    val activityLabel = log.markActivity.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                                    
                                    androidx.compose.runtime.key("waypoint-${log.id}-${log.timestamp}") {
                                        Marker(
                                            state = rememberMarkerState(position = LatLng(log.latitude, log.longitude)),
                                            title = "$activityLabel: $clientName",
                                            snippet = "Time: $logTime",
                                            icon = when(log.markActivity) {
                                                "CLOCK_IN" -> MapUtils.vectorToBitmap(context, R.drawable.ic_marker_clock_in)
                                                "MEETING_START" -> MapUtils.vectorToBitmap(context, R.drawable.ic_marker_meeting_start)
                                                "MEETING_END" -> MapUtils.vectorToBitmap(context, R.drawable.ic_marker_meeting_end)
                                                "CLOCK_OUT", "LOGOUT" -> MapUtils.vectorToBitmap(context, R.drawable.ic_marker_stop)
                                                else -> BitmapDescriptorFactory.defaultMarker(markerColor)
                                            } ?: BitmapDescriptorFactory.defaultMarker(markerColor),
                                            zIndex = 10f
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ✅ Search Result Indicator / Error Message
                if (false) uiState.error?.let { error ->
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

                // ✅ Mandatory Tracking Overlay: Only show if permissions are missing or GPS is off
                if (!uiState.isAdmin && (!locationPermissions.allPermissionsGranted || !isLocationEnabled) && !uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .padding(32.dp)
                                .fillMaxWidth()
                                .heightIn(max = 400.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = AppTheme.colors.surface,
                                contentColor = AppTheme.colors.text
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(32.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    modifier = Modifier.size(80.dp),
                                    shape = CircleShape,
                                    color = AppTheme.colors.primary.copy(alpha = 0.1f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = AppTheme.colors.primary,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                Text(
                                    text = "Start Your Day",
                                    style = AppTheme.typography.h3,
                                    color = AppTheme.colors.text,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Text(
                                    text = "Turn on tracking to see clients in your territory and start logging your visits.",
                                    style = AppTheme.typography.body2,
                                    color = AppTheme.colors.textSecondary,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 20.sp
                                )
                                
                                Spacer(modifier = Modifier.height(32.dp))
                                
                                Button(
                                    onClick = { 
                                        if (locationPermissions.allPermissionsGranted) {
                                            if (isLocationEnabled) {
                                                viewModel.startClockIn()
                                            } else {
                                                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                            }
                                        } else {
                                            locationPermissions.launchMultiplePermissionRequest()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AppTheme.colors.primary,
                                        contentColor = AppTheme.colors.onPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(18.dp), tint = AppTheme.colors.onPrimary)
                                        Text("Clock In & Enable Map", fontWeight = FontWeight.Bold, color = AppTheme.colors.onPrimary)
                                    }
                                }
                                
                                if (!locationPermissions.allPermissionsGranted || !isLocationEnabled) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = if (!locationPermissions.allPermissionsGranted) 
                                            "⚠️ Location permissions required" 
                                            else "⚠️ GPS is currently turned off",
                                        style = AppTheme.typography.label3,
                                        color = AppTheme.colors.error
                                    )
                                }
                            }
                        }
                    }
                }


                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Stats Panel
                    AnimatedVisibility(
                        visible = true, // Mandatory tracking
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .zIndex(3f),
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically()
                    ) {
                        Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp)),
                        color = Color(0xFF0B1220).copy(alpha = 0.92f),
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Live dot
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981)) // Always "Online" dot for agents
                            )
                            // Clients in view
                            OverlayStatChip(
                                icon = Icons.Default.Place,
                                label = "${visibleClientMarkers.size} in view",
                                tint = AppTheme.colors.primary
                            )
                            // Total mapped
                            OverlayStatChip(
                                icon = Icons.Default.PinDrop,
                                label = "${uiState.filteredClients.size} total",
                                tint = Color(0xFF34D399)
                            )
                            if (uiState.isAdmin) {
                                // Live agents
                                OverlayStatChip(
                                    icon = Icons.Default.People,
                                    label = "$liveAgentCount agents",
                                    tint = Color(0xFFF59E0B)
                                )
                            }
                            if (uiState.hiddenClientsCount > 0) {
                                OverlayStatChip(
                                    icon = Icons.Default.LocationOff,
                                    label = "${uiState.hiddenClientsCount} no GPS",
                                    tint = Color(0xFFF87171)
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            // Search button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AppTheme.colors.primary.copy(alpha = 0.15f))
                                    .clickable { /* search already on panel */ }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = AppTheme.colors.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    }

                    // Search bar overlay — always visible below stats
                    AnimatedVisibility(
                        visible = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .zIndex(10f),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ui.components.textfield.TextField(
                                    value = uiState.searchQuery,
                                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                                    placeholder = { Text("Search clients by name, address, or pincode...") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(AppTheme.colors.surface),
                                    leadingIcon = {
                                        if (uiState.isSearchingRemote) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
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

                                AnimatedVisibility(
                                    visible = uiState.searchQuery.isNotEmpty() && uiState.filteredClients.isNotEmpty()
                                ) {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(AppTheme.colors.surface)
                                            .border(1.dp, AppTheme.colors.outline.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                            .heightIn(max = 280.dp)
                                    ) {
                                        val displayCount = 5
                                        items(uiState.filteredClients.take(displayCount)) { client ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        viewModel.selectClient(client)
                                                        coroutineScope.launch {
                                                            cameraPositionState.animate(
                                                                update = CameraUpdateFactory.newLatLngZoom(
                                                                    LatLng(client.latitude ?: 0.0, client.longitude ?: 0.0),
                                                                    16.5f
                                                                ),
                                                                durationMs = 800
                                                            )
                                                            // ✅ DELAY clearing to ensure Modal shows first
                                                            delay(500)
                                                            viewModel.onSearchQueryChanged("")
                                                        }
                                                    }
                                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(AppTheme.colors.primary.copy(alpha = 0.12f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.LocationOn,
                                                        contentDescription = "Client location",
                                                        tint = AppTheme.colors.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = client.name,
                                                        style = AppTheme.typography.body1,
                                                        fontWeight = FontWeight.Bold,
                                                        color = AppTheme.colors.text
                                                    )
                                                    Text(
                                                        text = client.address ?: "No address",
                                                        style = AppTheme.typography.body3,
                                                        color = AppTheme.colors.textSecondary,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                    }
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = !showMeetingSheet,
                    modifier = Modifier
                        .offset { IntOffset(legendOffset.x.roundToInt(), legendOffset.y.roundToInt()) }
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp, top = 120.dp)
                        .zIndex(5f)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                legendOffset += dragAmount
                            }
                        },
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    EnhancedMapLegend(
                        isExpanded = isLegendExpanded,
                        onToggle = { isLegendExpanded = !isLegendExpanded },
                        clientCounts = clientCounts,
                        filteredStatuses = filteredStatuses,
                        onFilterChange = { status ->
                            filteredStatuses = if (filteredStatuses.contains(status) && filteredStatuses.size > 1) {
                                filteredStatuses - status
                            } else {
                                filteredStatuses + status
                            }
                        },
                        agentCount = if (uiState.isAdmin) liveAgentCount else null,
                        visibleClientCount = visibleClientMarkers.size,
                        clientsVisible = if (uiState.isAdmin) uiState.showClients else canShowClientMarkers
                    )
                }

                // 2. Status Indicator Row (Clocked-In / Online Now)
                if (false) Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 120.dp, start = 16.dp)
                        .zIndex(2f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Side: Status Badges for Agent
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Tracking Active indicator - renamed to "Online" for clarity
                            AnimatedVisibility(
                                visible = !uiState.isAdmin,
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
                        visible = true,
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
                    visible = showMeetingSheet && proximityClient != null && !uiState.isAdmin,
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
                // Agent Detail Modal (Centered overlay)
                AnimatedVisibility(
                    visible = uiState.selectedAgent != null && uiState.isAdmin,
                    modifier = Modifier
                        .align(Alignment.Center) // ✅ MODAL: Centered
                        .padding(horizontal = 24.dp) // Modal padding
                        .zIndex(20f), // Highest priority
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    uiState.selectedAgent?.let { agent ->
                        AnimatedAgentBottomSheet( // Functionally a modal now
                            agent = agent,
                            uiState = uiState,
                            onClose = { viewModel.selectAgent(null) },
                            onViewProfile = { onNavigateToAgentDetail(it) }
                        )
                    }
                }

                // Client Detail Modal - Shows for BOTH Admin and Agent users
                AnimatedVisibility(
                    visible = uiState.selectedClient != null && !showMeetingSheet,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 0.dp)
                        .zIndex(20f), 
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    uiState.selectedClient?.let { selectedClient ->
                        AnimatedClientBottomSheet(
                            client = selectedClient,
                            uiState = uiState,
                            viewModel = viewModel,
                            cameraPositionState = cameraPositionState,
                            onClose = { 
                                viewModel.selectClient(null)
                                proximityClient = null
                            },
                            onViewDetails = { selectedClient.id.let(onNavigateToClientDetail) },
                            onStartMeeting = {
                                viewModel.selectClient(null)
                                proximityClient = selectedClient
                                showMeetingSheet = true
                                meetingViewModel.checkActiveMeeting(selectedClient.id)
                            },
                            meetingUiState = meetingUiState,
                            onQuickVisit = { status ->
                                viewModel.updateQuickVisitStatus(selectedClient.id, status)
                            },
                            onStartJourney = { clientId, mode ->
                                viewModel.startJourney(clientId, mode)
                                viewModel.selectClient(null)
                                proximityClient = null
                            }
                        )
                    }
                }


                // Territory/Info Banner
                AnimatedVisibility(
                    visible = false && uiState.territoryMessage != null,
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
                    visible = false && uiState.hiddenClientsCount > 0 && !uiState.isLoading && uiState.selectedClient == null && uiState.selectedAgent == null && !showMeetingSheet,
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
                    visible = false && uiState.error != null,
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
                    visible = !uiState.isAdmin,
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

                // Clock Out Button (Visible when tracking is ON)
                AnimatedVisibility(
                    visible = !uiState.isAdmin,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 16.dp, start = 16.dp),
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    androidx.compose.material3.ExtendedFloatingActionButton(
                        onClick = { viewModel.stopClockOut() },
                        containerColor = AppTheme.colors.error,
                        contentColor = AppTheme.colors.onError,
                        icon = { Icon(Icons.AutoMirrored.Filled.Logout, "Clock Out") },
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
        meetingUiState: com.bluemix.clients_lead.features.meeting.vm.MeetingUiState,
        onQuickVisit: (String) -> Unit = {},
        onStartJourney: ((String, String) -> Unit)? = null // ✅ NEW: Start Journey callback for agents
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
                    // 1. Show button within 50m (with slight jitter tolerance)
                    // 2. ONLY for non-admins (Agents/Users). Admins shouldn't start meetings.
                     // ✅ FIX #1: Unified 50m Rule. 
                     // Button and logic now strictly follow the 50m Proximity Shield requirement.
                    val isWithinProximity = distanceMeters <= 50.0 
                    val isLocationUnavailable = uiState.currentLocation == null
                    val canStartMeeting = isWithinProximity && !uiState.isAdmin // ✅ ADMINS BLOCKED
                    val isAgent = !uiState.isAdmin

                    // Primary: Start Meeting (Visible if 50m of the client and NOT an Admin)
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
                    } else if (isAgent && !isLocationUnavailable && onStartJourney != null) {
                        // ✅ NEW: Start Journey button for agents (when not within proximity)
                        Button(
                            onClick = { onStartJourney?.invoke(client.id, "Car") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6366F1) // Indigo
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Start Journey",
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
    fun VisitStatusIndicator(status: VisitStatus) {
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
    fun AnimatedAgentBottomSheet(
        agent: com.bluemix.clients_lead.domain.repository.AgentLocation,
        uiState: MapUiState,
        onClose: () -> Unit,
        onViewProfile: (String) -> Unit
    ) {
        val isOnlineNow = com.bluemix.clients_lead.core.common.utils.DateTimeUtils.isRecent(agent.timestamp)
        
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
                // Header: Agent Identity
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = agent.fullName ?: "Staff Member",
                            style = AppTheme.typography.h3,
                            color = AppTheme.colors.text,
                            fontWeight = FontWeight.Bold
                        )
                        // ✅ SMART STATUS: Human-Readable (At site, Traveling, or Idle)
                        Text(
                            text = agent.smartStatus ?: if (isOnlineNow) "Active / Online" else "Offline",
                            style = AppTheme.typography.body2,
                            color = if (agent.smartStatus == "Idle") AppTheme.colors.error else AppTheme.colors.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    // ✅ FIXED: Close icon with white color and circular background
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // ✅ STAT CARDS GRID (3 columns - Removed Overdue)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AgentStatCard(
                        modifier = Modifier.weight(1f),
                        label = "Visits",
                        value = "${agent.visitCount}",
                        icon = Icons.AutoMirrored.Filled.DirectionsRun,
                        color = AppTheme.colors.primary
                    )
                    AgentStatCard(
                        modifier = Modifier.weight(1f),
                        label = "Verified",
                        value = "${uiState.selectedAgentVerifiedVisits}",
                        icon = Icons.Default.Verified,
                        color = Color(0xFF10B981)
                    )
                    AgentStatCard(
                        modifier = Modifier.weight(1f),
                        label = "Battery",
                        value = "${agent.battery ?: 0}%",
                        icon = Icons.Default.Bolt,
                        color = if ((agent.battery ?: 100) < 20) Color.Red else Color(0xFF3B82F6)
                    )
                }

                // Last Seen Footer
                agent.timestamp?.let {
                    Text(
                        text = "Last seen: ${com.bluemix.clients_lead.core.common.utils.DateTimeUtils.formatLastSeen(it)}",
                        style = AppTheme.typography.label2,
                        color = AppTheme.colors.textSecondary,
                        modifier = Modifier.align(Alignment.End)
                    )
                }

                // ✅ FIXED: View Details CTA Button
                Button(
                    onClick = { onViewProfile(agent.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppTheme.colors.primary,
                        contentColor = AppTheme.colors.onPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 8.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = AppTheme.colors.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "View Details",
                        style = AppTheme.typography.button,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.colors.onPrimary
                    )
                }
            }
        }
    }

    @Composable
    fun AgentStatCard(
        modifier: Modifier = Modifier,
        label: String,
        value: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        color: Color
    ) {
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = 0.08f))
                .border(1.dp, color.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = color)
            Text(text = value, style = AppTheme.typography.body1, fontWeight = FontWeight.Bold, color = AppTheme.colors.text)
            Text(text = label, style = AppTheme.typography.label2, color = AppTheme.colors.textSecondary, fontSize = 10.sp)
        }
    }

    @Composable
    fun InfoTile(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = label, style = AppTheme.typography.label2, color = AppTheme.colors.textSecondary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = AppTheme.colors.primary)
                Text(text = value, style = AppTheme.typography.body2, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    @Composable
    fun QuickVisitOption(
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
fun AnimatedPermissionPrompt(
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


// Small reusable chip for the persistent map overlay bar
@Composable
private fun OverlayStatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(text = label, color = Color.White, style = AppTheme.typography.label2, fontSize = 11.sp)
    }
}
// ✅ Simplified Agent filtering: just check for coordinates
private fun agentsWithPosition(agents: List<AgentLocation>): List<AgentLocation> {
    return agents.filter { it.latitude != null && it.longitude != null }
}

@Composable
fun EnhancedMapLegend(
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    clientCounts: Map<VisitStatus, Int>,
    filteredStatuses: Set<VisitStatus>,
    onFilterChange: (VisitStatus) -> Unit,
    agentCount: Int? = null,
    visibleClientCount: Int = clientCounts.values.sum(),
    clientsVisible: Boolean = true
) {
    // ANIMATED: Adjust width based on state for a smooth resize effect
    val width by animateDpAsState(targetValue = if (isExpanded) 260.dp else 48.dp, label = "legendWidth")
    val cornerRadius by animateDpAsState(targetValue = if (isExpanded) 24.dp else 24.dp, label = "legendCorners")
    
    Column(
        modifier = modifier
            .width(width)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0xFF08111D).copy(alpha = 0.94f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(cornerRadius))
            .then(if (isExpanded) Modifier.padding(14.dp) else Modifier.padding(10.dp))
            .clickable(onClick = onToggle),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = if (isExpanded) Alignment.Start else Alignment.CenterHorizontally
    ) {
        // TOP HEADER / ICON
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isExpanded) Arrangement.SpaceBetween else Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isExpanded) 30.dp else 28.dp)
                        .clip(RoundedCornerShape(if (isExpanded) 10.dp else 14.dp))
                        .background(Color.White.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.FilterList else Icons.Default.Map,
                        contentDescription = "Toggle Legend",
                        tint = AppTheme.colors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                if (isExpanded) {
                    Column {
                        Text(
                            text = "Legend & Layers",
                            style = AppTheme.typography.label1,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (clientsVisible) "$visibleClientCount pins" else "Hidden",
                            style = AppTheme.typography.body3,
                            color = Color.White.copy(alpha = 0.58f),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            if (isExpanded) {
                Icon(
                    imageVector = Icons.Default.ExpandLess,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // CONTENT (Visible only when expanded)
        if (!isExpanded && clientsVisible && visibleClientCount > 0) {
           // Small count badge below icon when collapsed
           Text(
               text = visibleClientCount.toString(),
               style = AppTheme.typography.label2,
               color = AppTheme.colors.primary,
               fontSize = 10.sp,
               fontWeight = FontWeight.Bold
           )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

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
                        color = Color(0xFF38BDF8),
                        label = "Live agents",
                        count = agentCount,
                        isEnabled = true,
                        onClick = { }
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Text(
                    text = "Tip: tap any color to narrow the map. Journey markers appear only after you select an agent.",
                    style = AppTheme.typography.body3,
                    color = Color.White.copy(alpha = 0.56f),
                    fontSize = 11.sp
                )
            }
        }
    }
}


// ✅ FIX #12: State enum for expense sheets removed, using global one

// ✅ FIX #11: Removed dead code (Old MapLegend and LegendItem)


@Composable
fun EnhancedLegendItem(
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

private fun resolveBannerTone(message: String): Pair<Color, androidx.compose.ui.graphics.vector.ImageVector> {
    val lowerMessage = message.lowercase()
    return when {
        "success" in lowerMessage || "tagged" in lowerMessage || "updated" in lowerMessage ->
            Color(0xFF34D399) to Icons.Default.CheckCircle
        "detecting" in lowerMessage || "wait" in lowerMessage || "no clients found" in lowerMessage ->
            Color(0xFF60A5FA) to Icons.Default.Info
        else -> Color(0xFFF87171) to Icons.Default.Warning
    }
}

private fun calculateVisitStatusCounts(clients: List<Client>): Map<VisitStatus, Int> {
    val counts = VisitStatus.values().associateWith { 0 }.toMutableMap()
    clients.forEach { client ->
        val status = client.getVisitStatusColor()
        counts[status] = (counts[status] ?: 0) + 1
    }
    return counts
}



@Composable
fun ExpenseTypeCard(
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

@Composable
fun AdminFilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    androidx.compose.material3.FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(text, color = if (isSelected) Color.White else AppTheme.colors.text, style = AppTheme.typography.body2, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AppTheme.colors.primary,
            containerColor = AppTheme.colors.surface.copy(alpha = 0.9f)
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = AppTheme.colors.outline.copy(alpha = 0.2f),
            selectedBorderColor = Color.Transparent,
            borderWidth = 1.dp,
            selectedBorderWidth = 0.dp
        ),
        shape = RoundedCornerShape(20.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentRosterSheet(
    agents: List<com.bluemix.clients_lead.domain.repository.AgentLocation>,
    selectedAgent: com.bluemix.clients_lead.domain.repository.AgentLocation?,
    onAgentClick: (com.bluemix.clients_lead.domain.repository.AgentLocation) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = AppTheme.colors.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Live Agent Roster",
                style = AppTheme.typography.h3,
                modifier = Modifier.padding(16.dp),
                color = AppTheme.colors.text
            )
            
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(agents) { agent ->
                    AgentRosterItem(
                        agent = agent,
                        isSelected = agent.id == selectedAgent?.id,
                        onClick = { onAgentClick(agent) }
                    )
                }
            }
        }
    }
}

@Composable
fun AgentRosterItem(
    agent: com.bluemix.clients_lead.domain.repository.AgentLocation,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isOnline = com.bluemix.clients_lead.core.common.utils.DateTimeUtils.isRecent(agent.timestamp)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(if (isSelected) AppTheme.colors.primary.copy(alpha = 0.1f) else Color.Transparent)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(if (isOnline) AppTheme.colors.success.copy(alpha = 0.15f) else AppTheme.colors.textSecondary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = if (isOnline) AppTheme.colors.success else AppTheme.colors.textSecondary,
                modifier = Modifier.size(24.dp)
            )
            // Battery dot
            val battery = agent.battery
            if (battery != null) {
                Box(
                    modifier = Modifier.size(10.dp).align(Alignment.BottomEnd).clip(CircleShape).background(Color.White).padding(1.dp).clip(CircleShape).background(if (battery < 20) Color.Red else Color.Green)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = agent.fullName ?: agent.email,
                style = AppTheme.typography.body1,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.text
            )
            Text(
                text = agent.smartStatus ?: if (isOnline) "Active" else "Offline",
                style = AppTheme.typography.body3,
                color = if (agent.smartStatus == "Idle") AppTheme.colors.error else AppTheme.colors.textSecondary,
                fontSize = 12.sp
            )
        }
        
        if (agent.visitCount > 0) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(AppTheme.colors.primary.copy(alpha = 0.1f)).padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(text = "${agent.visitCount} visits", style = AppTheme.typography.label2, color = AppTheme.colors.primary, fontSize = 11.sp)
            }
        }
    }
}
