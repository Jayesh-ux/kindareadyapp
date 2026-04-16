package com.bluemix.clients_lead.features.admin.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.model.Client
import com.bluemix.clients_lead.domain.model.LocationLog
import com.bluemix.clients_lead.domain.repository.AgentLocation
import com.bluemix.clients_lead.domain.usecases.GetClientsWithLocation
import com.bluemix.clients_lead.domain.usecases.GetLocationLogsByDateRange
import com.bluemix.clients_lead.domain.usecases.GetTeamLocations
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class JourneyViewMode {
    MAP, SUMMARY
}

data class AdminJourneyUiState(
    val isLoading: Boolean = false,
    val agents: List<AgentLocation> = emptyList(),
    val selectedAgent: AgentLocation? = null,
    val selectedDate: LocalDate = LocalDate.now(),
    val logs: List<LocationLog> = emptyList(),
    val viewMode: JourneyViewMode = JourneyViewMode.MAP,
    val clients: List<Client> = emptyList(),
    val selectedClient: Client? = null,
    
    // Agent search
    val agentSearchQuery: String = "",
    val filteredAgents: List<AgentLocation> = emptyList(),
    val showAgentSearch: Boolean = false,
    
    // Map optimization
    val routePolyline: List<LatLng> = emptyList(),
    val importantMarkers: List<JourneyMarker> = emptyList(),
    
    // Stats
    val totalDistanceKm: Double = 0.0,
    val activeDurationMinutes: Long = 0,
    val clientsVisited: Int = 0,
    val plannedClients: Int = 0,
    val gpsPointCount: Int = 0,
    val totalExpenses: Double = 0.0,
    val expenses: List<com.bluemix.clients_lead.domain.model.TripExpense> = emptyList(),
    val resolvedAddresses: Map<String, String> = emptyMap(),
    
    val error: String? = null
)

data class JourneyMarker(
    val position: LatLng,
    val title: String,
    val type: MarkerType,
    val timestamp: String? = null
)

enum class MarkerType {
    START, END, MEETING_START, MEETING_END, OTHER
}

class AdminJourneyViewModel(
    private val context: android.content.Context, // ✅ NEW
    private val getTeamLocations: GetTeamLocations,
    private val getLocationLogsByDateRange: GetLocationLogsByDateRange,
    private val getTripExpenses: com.bluemix.clients_lead.domain.usecases.GetTripExpensesUseCase,
    private val getClientsWithLocation: GetClientsWithLocation
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminJourneyUiState())
    val uiState: StateFlow<AdminJourneyUiState> = _uiState.asStateFlow()

    private var refreshJob: kotlinx.coroutines.Job? = null

    init {
        loadAgents()
        loadClients(null) // Load all clients for admin view initially
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                delay(10000) // ✅ Faster 10s Real-time sync
                if (_uiState.value.selectedDate == LocalDate.now() && _uiState.value.selectedAgent != null) {
                    fetchLogs(isAutoRefresh = true)
                }
                if (_uiState.value.selectedDate == LocalDate.now()) {
                    loadAgents() // Keep agent list (and their latest positions) fresh
                }
            }
        }
    }

    private fun loadAgents() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = getTeamLocations()) {
                is AppResult.Success -> {
                    val updatedAgents = result.data
                    _uiState.update { state -> 
                        state.copy(
                            isLoading = false, 
                            agents = updatedAgents,
                            // ✅ Sync selectedAgent with fresh activity/location data
                            selectedAgent = state.selectedAgent?.let { current ->
                                updatedAgents.find { it.id == current.id } ?: current
                            }
                        ) 
                    }
                }
                is AppResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.error.message) }
                }
            }
        }
    }

    private fun loadClients(agentId: String? = null) {
        viewModelScope.launch {
            val assignedTo = agentId ?: ""
            val isAdminView = agentId == null
            
            when (val result = getClientsWithLocation(assignedTo, isAdmin = isAdminView)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(clients = result.data.clients) }
                }
                is AppResult.Error -> {
                    // Silently fail - clients are optional
                }
            }
        }
    }

    fun selectClient(client: Client?) {
        _uiState.update { it.copy(selectedClient = client) }
    }

    fun selectAgent(agent: AgentLocation?) {
        Timber.d("FILTER_DEBUG", "========================================")
        Timber.d("FILTER_DEBUG", "ADMIN JOURNEY - SELECT AGENT: ${agent?.id ?: "null"}")
        Timber.d("FILTER_DEBUG", "Logs before: ${_uiState.value.logs.size}")
        
        _uiState.update { it.copy(selectedAgent = agent) }
        
        Timber.d("FILTER_DEBUG", "Calling fetchLogs()...")
        fetchLogs()
        
        Timber.d("FILTER_DEBUG", "Calling loadClients(${agent?.id})...")
        loadClients(agent?.id)
        
        Timber.d("FILTER_DEBUG", "========================================")
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        fetchLogs()
    }

    fun setViewMode(mode: JourneyViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    private fun fetchLogs(isAutoRefresh: Boolean = false) {
        val state = _uiState.value
        val agent = state.selectedAgent ?: return
        
        viewModelScope.launch {
            if (!isAutoRefresh) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }
            
            val dateStr = state.selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE) // "yyyy-MM-dd"
            
            when (val result = getLocationLogsByDateRange(agent.id, dateStr, dateStr)) {
                is AppResult.Success -> {
                    val logs = result.data.sortedBy { it.timestamp }
                    val stats = calculateStats(logs)
                    
                    // ✅ IMPROVED: Process route with filtering, deduplication, smoothing
                    val processedPolyline = processRoute(logs)
                    val importantMarkers = extractImportantMarkers(logs)
                    
                    // Fetch expenses
                    val expenseResult = getTripExpenses(agent.id)
                    var todayExpenses: List<com.bluemix.clients_lead.domain.model.TripExpense> = emptyList()
                    if (expenseResult is AppResult.Success) {
                        val startOfDay = state.selectedDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val endOfDay = startOfDay + 86400000L
                        todayExpenses = expenseResult.data.filter { it.travelDate in startOfDay..endOfDay }
                    }
                    
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            logs = logs,
                            routePolyline = processedPolyline,
                            importantMarkers = importantMarkers,
                            totalDistanceKm = stats.distanceKm,
                            activeDurationMinutes = stats.durationMinutes,
                            gpsPointCount = logs.size,
                            clientsVisited = logs.count { it.markActivity == "MEETING_START" },
                            plannedClients = logs.filter { it.markActivity == "MEETING_START" }.mapNotNull { it.clientId }.distinct().size,
                            expenses = todayExpenses,
                            totalExpenses = todayExpenses.sumOf { exp -> exp.amountSpent }
                        ) 
                    }
                    
                    if (!isAutoRefresh) {
                        resolveAddresses(logs) // ✅ NEW: Start resolving addresses
                    } else {
                        // Only resolve new addresses during auto-refresh
                        val lastResolvedId = _uiState.value.resolvedAddresses.keys.lastOrNull()
                        val newLogs = if (lastResolvedId != null) logs.dropWhile { it.id != lastResolvedId }.drop(1) else logs
                        if (newLogs.isNotEmpty()) resolveAddresses(newLogs)
                    }
                }
                is AppResult.Error -> {
                    if (!isAutoRefresh) {
                        _uiState.update { it.copy(isLoading = false, error = result.error.message) }
                    }
                }
            }
        }
    }

    private data class JourneyStats(val distanceKm: Double, val durationMinutes: Long)

    /**
     * ✅ IMPROVED: Process route with STRICT filtering to eliminate zig-zag and loops
     * Fixes: double lines, zig-zag, triangular loops, inaccurate GPS points
     * Includes: accuracy filter, distance filter, time filter, sudden jump filter
     */
    private fun processRoute(logs: List<LocationLog>): List<LatLng> {
        if (logs.isEmpty()) return emptyList()
        
        // Sort by timestamp first
        val sortedLogs = logs.sortedBy { it.timestamp }
        
        // Step 1: STRICT accuracy filter - only keep points where accuracy <= 30m
        val accurateLogs = sortedLogs.filter { (it.accuracy ?: 100.0) <= 30.0 }
        
        if (accurateLogs.isEmpty()) {
            Timber.w("⚠️ No accurate GPS points found (<=30m accuracy)")
            return emptyList()
        }
        
        // Step 2: STRICT distance + time filter
        val filtered = mutableListOf<LocationLog>()
        var lastKeptPoint: LatLng? = null
        var lastTimestamp: Long? = null
        
        for (log in accurateLogs) {
            val current = LatLng(log.latitude, log.longitude)
            
            // Parse timestamp - handle ISO format
            val currentTimestamp = try {
                java.time.Instant.parse(log.timestamp).toEpochMilli()
            } catch (e: Exception) {
                try {
                    log.timestamp.toLongOrNull() ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }
            }
            
            // Time-based filter: skip points less than 5 seconds apart
            if (lastTimestamp != null) {
                val timeDiff = currentTimestamp - lastTimestamp
                if (timeDiff < 5000) {
                    continue // Skip point - too soon after previous
                }
            }
            
            if (lastKeptPoint == null) {
                filtered.add(log)
                lastKeptPoint = current
                lastTimestamp = currentTimestamp
                continue
            }
            
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                lastKeptPoint.latitude, lastKeptPoint.longitude,
                current.latitude, current.longitude,
                results
            )
            
            val distance = results[0]
            
            // Skip if: too close (< 10m) OR sudden jump (> 200m indicates GPS error)
            if (distance < 10.0) {
                continue // Skip point - too close to previous
            }
            
            if (distance > 200.0) {
                Timber.w("⚠️ GPS jump detected: ${distance.toInt()}m - skipping point")
                continue // Skip sudden jump - likely GPS error
            }
            
            filtered.add(log)
            lastKeptPoint = current
            lastTimestamp = currentTimestamp
        }
        
        // Step 3: Apply gentle simplification (50m threshold)
        val simplified = simplifyRoute(filtered, 50.0f)
        
        Timber.d("📍 Route processed: ${logs.size} → ${accurateLogs.size} accurate → ${filtered.size} filtered → ${simplified.size} final points")
        return simplified
    }

    /**
     * ✅ UPDATED: Simplify route by keeping points every minDistanceMeters
     * Reduced threshold from 200m to 50m for smoother path
     */
    private fun simplifyRoute(logs: List<LocationLog>, minDistanceMeters: Float): List<LatLng> {
        if (logs.size < 2) return logs.map { LatLng(it.latitude, it.longitude) }
        
        val simplified = mutableListOf<LatLng>()
        var lastPoint: LatLng? = null
        
        for (log in logs) {
            val current = LatLng(log.latitude, log.longitude)
            
            if (lastPoint == null) {
                simplified.add(current)
                lastPoint = current
                continue
            }
            
            // Calculate distance from last kept point
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                lastPoint.latitude, lastPoint.longitude,
                current.latitude, current.longitude,
                results
            )
            
            if (results[0] >= minDistanceMeters) {
                simplified.add(current)
                lastPoint = current
            }
        }
        
        Timber.d("📍 Route simplified: ${logs.size} → ${simplified.size} points")
        return simplified
    }
    
    /**
     * ✅ NEW: Extract only important markers (START, END, MEETING events)
     * Reduces marker spam on map
     */
    private fun extractImportantMarkers(logs: List<LocationLog>): List<JourneyMarker> {
        if (logs.isEmpty()) return emptyList()
        
        val markers = mutableListOf<JourneyMarker>()
        
        // Always add start marker
        markers.add(JourneyMarker(
            position = LatLng(logs.first().latitude, logs.first().longitude),
            title = "Journey Start",
            type = MarkerType.START,
            timestamp = logs.first().timestamp
        ))
        
        // Add meeting markers
        logs.forEachIndexed { index, log ->
            when (log.markActivity) {
                "MEETING_START" -> {
                    markers.add(JourneyMarker(
                        position = LatLng(log.latitude, log.longitude),
                        title = "Meeting Start",
                        type = MarkerType.MEETING_START,
                        timestamp = log.timestamp
                    ))
                }
                "MEETING_END" -> {
                    markers.add(JourneyMarker(
                        position = LatLng(log.latitude, log.longitude),
                        title = "Meeting End",
                        type = MarkerType.MEETING_END,
                        timestamp = log.timestamp
                    ))
                }
            }
        }
        
        // Always add end marker if different from start
        val lastLog = logs.last()
        if (markers.size == 1 || (markers.last().position.latitude != lastLog.latitude || 
            markers.last().position.longitude != lastLog.longitude)) {
            markers.add(JourneyMarker(
                position = LatLng(lastLog.latitude, lastLog.longitude),
                title = "Journey End",
                type = MarkerType.END,
                timestamp = lastLog.timestamp
            ))
        }
        
        Timber.d("📍 Important markers: ${markers.size}")
        return markers
    }
    
    // Agent search functions
    fun updateAgentSearch(query: String) {
        val filtered = if (query.isBlank()) {
            _uiState.value.agents
        } else {
            _uiState.value.agents.filter { 
                (it.fullName ?: "").contains(query, ignoreCase = true) ||
                it.email.contains(query, ignoreCase = true)
            }
        }
        _uiState.update { it.copy(agentSearchQuery = query, filteredAgents = filtered) }
    }
    
    fun toggleAgentSearch(show: Boolean) {
        _uiState.update { 
            it.copy(
                showAgentSearch = show,
                filteredAgents = if (show) it.agents else emptyList()
            )
        }
    }

    private fun calculateStats(logs: List<LocationLog>): JourneyStats {
        if (logs.size < 2) return JourneyStats(0.0, 0)
        
        var totalDist = 0.0
        for (i in 0 until logs.size - 1) {
            val start = logs[i]
            val end = logs[i + 1]
            totalDist += calculateDistanceMeters(
                start.latitude, start.longitude,
                end.latitude, end.longitude
            )
        }

        val firstLogTime = com.bluemix.clients_lead.core.common.utils.DateTimeUtils.parseDate(logs.first().timestamp)?.time ?: 0L
        val lastLogTime = com.bluemix.clients_lead.core.common.utils.DateTimeUtils.parseDate(logs.last().timestamp)?.time ?: 0L
        val durationMillis = lastLogTime - firstLogTime
        val durationMinutes = durationMillis / (1000 * 60)

        return JourneyStats(totalDist / 1000.0, durationMinutes)
    }

    private fun resolveAddresses(logs: List<LocationLog>) {
        viewModelScope.launch {
            val currentAddresses = _uiState.value.resolvedAddresses.toMutableMap()
            val resolvedPoints = mutableListOf<Pair<LocationLog, String>>()
            
            // Build cache of already known points
            currentAddresses.forEach { (id, addr) ->
                logs.find { it.id == id }?.let { resolvedPoints.add(it to addr) }
            }

            for (log in logs) {
                if (!currentAddresses.containsKey(log.id)) {
                    // Check for nearby (<= 50m) already resolved point to skip API
                    var cachedAddress: String? = null
                    for ((resolvedLog, addr) in resolvedPoints) {
                        val dist = calculateDistanceMeters(
                            log.latitude, log.longitude,
                            resolvedLog.latitude, resolvedLog.longitude
                        )
                        if (dist <= 50.0) {
                            cachedAddress = addr
                            break
                        }
                    }

                    if (cachedAddress != null) {
                        currentAddresses[log.id] = cachedAddress
                        resolvedPoints.add(log to cachedAddress)
                        _uiState.update { it.copy(resolvedAddresses = currentAddresses.toMap()) }
                    } else {
                        // Unique location -> Call API
                        val address = com.bluemix.clients_lead.core.common.utils.LocationUtils.getAddress(
                            context, log.latitude, log.longitude
                        )
                        if (address != null) {
                            currentAddresses[log.id] = address
                            resolvedPoints.add(log to address)
                            _uiState.update { it.copy(resolvedAddresses = currentAddresses.toMap()) }
                        }
                    }
                }
            }
        }
    }

    fun exportJourneyReport(): String {
        val state = _uiState.value
        val sb = StringBuilder()
        sb.append("Date,Time,Activity,Notes,Latitude,Longitude,Accuracy,Battery,Address\n")
        
        state.logs.forEach { log ->
            val address = state.resolvedAddresses[log.id] ?: "Unknown"
            val time = com.bluemix.clients_lead.core.common.utils.DateTimeUtils.formatTimeOnly(log.timestamp)
            sb.append("${state.selectedDate},$time,${log.markActivity ?: "Movement"},\"${(log.markNotes ?: "").replace("\"", "'")}\",${log.latitude},${log.longitude},${log.accuracy},${log.battery ?: ""},\"$address\"\n")
        }
        
        return sb.toString()
    }

    private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371e3 // Earth radius
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lon2 - lon1)

        val a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2) +
                Math.cos(phi1) * Math.cos(phi2) *
                Math.sin(dLambda / 2) * Math.sin(dLambda / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return r * c
    }
}
