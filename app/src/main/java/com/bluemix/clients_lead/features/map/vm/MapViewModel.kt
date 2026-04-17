package com.bluemix.clients_lead.features.map.vm
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import kotlinx.coroutines.flow.update
import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.usecases.UpdateClientAddress
import com.bluemix.clients_lead.domain.model.Client
import com.bluemix.clients_lead.domain.model.LocationLog
import com.bluemix.clients_lead.domain.usecases.GetClientsWithLocation
import com.bluemix.clients_lead.domain.usecases.GetCurrentUserId
import com.bluemix.clients_lead.domain.usecases.InsertLocationLog
import com.bluemix.clients_lead.features.location.LocationTrackingStateManager
import com.bluemix.clients_lead.features.location.BatteryUtils
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import timber.log.Timber
import com.bluemix.clients_lead.domain.usecases.CreateQuickVisit
import com.bluemix.clients_lead.domain.usecases.GetTeamLocations
import com.bluemix.clients_lead.domain.usecases.ObserveAuthState
import com.bluemix.clients_lead.domain.usecases.SearchRemoteClients
import com.bluemix.clients_lead.domain.usecases.CreateClient
import com.bluemix.clients_lead.domain.model.VisitStatus

data class MapUiState(
    val isLoading: Boolean = false,
    val clients: List<Client> = emptyList(),
    val agents: List<com.bluemix.clients_lead.domain.repository.AgentLocation> = emptyList(),
    val currentLocation: LatLng? = null,
    val currentLocationLog: LocationLog? = null,
    val selectedClient: Client? = null,
    val selectedAgent: com.bluemix.clients_lead.domain.repository.AgentLocation? = null,
    val isAdmin: Boolean = false,
    val territoryMessage: String? = null,
    val error: String? = null,
    val searchQuery: String = "",
    val filteredClients: List<Client> = emptyList(),
    val isSearchingRemote: Boolean = false,
    val isUpdatingAddress: Boolean = false,
    val updateError: String? = null,
    val hiddenClientsCount: Int = 0,
    val onlineAgentsCount: Int = 0,
    val currentLocationAccuracy: Float? = null,
    val activeJourneyClientId: String? = null,
    val showOnlineAgentsOnly: Boolean = false,
    val showHighAccuracyOnly: Boolean = false,
    val showClients: Boolean = true, // ✅ UPDATED: Show by default for immediate data visibility
    val agentFilter: String = "All", // ✅ NEW: All, Idle, Overdue
    val showAgentRoster: Boolean = false, // ✅ NEW
    val dailySummary: com.bluemix.clients_lead.domain.model.DailySummary? = null, // ✅ NEW
    val userEmail: String? = null,
    val selectedAgentJourney: List<LocationLog> = emptyList(),
    val selectedAgentVerifiedVisits: Int = 0,
    val selectedAgentOverdueNearby: Int = 0
)
class MapViewModel(
    private val getClientsWithLocation: GetClientsWithLocation,
    private val getCurrentUserId: GetCurrentUserId,
    private val locationTrackingStateManager: LocationTrackingStateManager,
    private val createQuickVisit: CreateQuickVisit,
    private val updateClientAddress: UpdateClientAddress,
    private val updateClientLocation: com.bluemix.clients_lead.domain.usecases.UpdateClientLocation,
    private val getTeamLocations: GetTeamLocations,
    private val observeAuthState: ObserveAuthState,
    private val searchRemoteClients: SearchRemoteClients,
    private val insertLocationLogUseCase: InsertLocationLog,
    private val createClient: CreateClient,
    private val signOut: com.bluemix.clients_lead.domain.usecases.SignOut,
    private val context: android.content.Context,
    private val getLocationLogsByDateRange: com.bluemix.clients_lead.domain.usecases.GetLocationLogsByDateRange,
    private val getLiveAgents: com.bluemix.clients_lead.domain.usecases.GetLiveAgents, // ✅ NEW Phase 3
    private val getDailySummary: com.bluemix.clients_lead.domain.usecases.GetDailySummary // ✅ NEW Phase 3
) : ViewModel() {
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null
    private var teamRefreshJob: Job? = null
    init {
        try {
            observeTrackingState()
        } catch (e: Exception) {
            Timber.e(e, "Error in observeTrackingState")
        }
        
        try {
            observeAuth()
        } catch (e: Exception) {
            Timber.e(e, "Error in observeAuth")
        }
        
        try {
            refreshTrackingState()
        } catch (e: Exception) {
            Timber.e(e, "Error in refreshTrackingState")
        }
        
        try {
            fetchLocationImmediately()
        } catch (e: Exception) {
            Timber.e(e, "Error in fetchLocationImmediately")
        }
        
        try {
            startLocationPolling()
        } catch (e: Exception) {
            Timber.e(e, "Error in startLocationPolling")
        }
        
        try {
            observeLocationSettings()
        } catch (e: Exception) {
            Timber.e(e, "Error in observeLocationSettings")
        }
    }
    @Volatile private var authResolved = false
    private fun observeAuth() {
        viewModelScope.launch {
            observeAuthState().collect { user ->
                val isAdmin = user?.isAdmin ?: false
                val isSuperAdmin = user?.isSuperAdmin ?: false
                val isAdminOrSuperAdmin = isAdmin || isSuperAdmin  // ✅ Combined
                val email = user?.email
                Timber.d("ðŸ”‘ Auth Update: $email, isAdmin=$isAdmin, isSuperAdmin=$isSuperAdmin, companyId=${user?.companyId}")
                _uiState.update { it.copy(isAdmin = isAdminOrSuperAdmin, userEmail = email) }  // ✅ Updated
                if (!authResolved) {
                    authResolved = true
                    Timber.d("🚀 Initial Load Triggered (isAdmin=$isAdmin, isSuperAdmin=$isSuperAdmin)")
                    if (isAdminOrSuperAdmin) {  // ✅ Updated
                        loadClients()
                        startTeamPolling()
                    } else {
                        // STRICT: Always start tracking for agents on login/launch
                        Timber.d("🚀 Mandatory tracking: Auto-starting for agent...")
                        locationTrackingStateManager.startTracking()
                        loadClients()
                    }
                } else if (isAdminOrSuperAdmin) {  // ✅ Updated
                    loadClients()
                    startTeamPolling()
                } else {
                    stopTeamPolling()
                }
            }
        }
    }
    fun startClockIn() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                locationTrackingStateManager.startTracking()
                // Initial log to mark the start
                _uiState.value.currentLocation?.let { loc ->
                    getCurrentUserId()?.let { userId ->
                        val email = _uiState.value.userEmail ?: "Unknown"
                        insertLocationLogUseCase(
                            userId = userId,
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            battery = com.bluemix.clients_lead.features.location.BatteryUtils.getBatteryPercentage(context),
                            markActivity = "CLOCK_IN",
                            markNotes = "Agent ($email) started work session"
                        )
                    }
                }
                _uiState.update { it.copy(isLoading = false, error = null) }
                loadClients()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to Clock In: ${e.message}") }
            }
        }
    }
    fun stopClockOut() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                _uiState.value.currentLocation?.let { loc ->
                    getCurrentUserId()?.let { userId ->
                        val email = _uiState.value.userEmail ?: "Unknown"
                        insertLocationLogUseCase(
                            userId = userId,
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            battery = com.bluemix.clients_lead.features.location.BatteryUtils.getBatteryPercentage(context),
                            markActivity = "CLOCK_OUT",
                            markNotes = "Agent ($email) ended work session (Background tracking remains active)"
                        )
                    }
                }
                // STRICT: Background location tracking is NOT stopped on clock out.
                // It only stops on explicit Logout for security/compliance.
                Timber.d("STRICT: Background tracking continues after clock out")
                
                _uiState.update { it.copy(isLoading = false, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to Clock Out: ${e.message}") }
            }
        }
    }
    private fun startTeamPolling() {
        teamRefreshJob?.cancel()
        teamRefreshJob = viewModelScope.launch {
            while (true) {
                refreshTeamLocations()
                // S13: Adaptive polling — faster when agents are active, slower when idle
                val onlineCount = _uiState.value.onlineAgentsCount
                val intervalMs = if (onlineCount > 0) 15_000L else 60_000L
                Timber.d("📡 Team poll interval: ${intervalMs / 1000}s (${onlineCount} online agents)")
                delay(intervalMs)
            }
        }
    }
    private fun stopTeamPolling() {
        teamRefreshJob?.cancel()
        teamRefreshJob = null
    }
    private fun refreshTeamLocations() {
        viewModelScope.launch {
            // Trigger fresh data fetch for team and summary
            loadTeamMembers()
            fetchDailySummary() // Keep summary fresh too
        }
    }
    private fun observeLocationSettings() {
        viewModelScope.launch { }
    }
    override fun onCleared() {
        super.onCleared()
        locationTrackingStateManager.cleanup()
    }
    private fun fetchLocationImmediately() {
        viewModelScope.launch {
            try {
                val locationManager = com.bluemix.clients_lead.features.location.LocationManager(
                    context = context
                )
                val location = locationManager.getLastKnownLocation()
                location?.let {
                    val latLng = LatLng(it.latitude, it.longitude)
                    val locationLog = LocationLog(
                        id = "",
                        userId = getCurrentUserId() ?: "",
                        latitude = it.latitude,
                        longitude = it.longitude,
                        accuracy = it.accuracy.toDouble(),
                        timestamp = System.currentTimeMillis().toString(),
                        createdAt = System.currentTimeMillis().toString(),
                        battery = com.bluemix.clients_lead.features.location.BatteryUtils.getBatteryPercentage(context)
                    )
                    _uiState.update { it.copy(
                        currentLocation = latLng,
                        currentLocationLog = locationLog
                    ) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Immediate location fetch failed")
            }
        }
    }
    private fun startLocationPolling() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5000)
                try {
                    val locationManager = com.bluemix.clients_lead.features.location.LocationManager(
                        context = context
                    )
                    val location = locationManager.getLastKnownLocation()
                    location?.let {
                        val latLng = LatLng(it.latitude, it.longitude)
                        val locationLog = LocationLog(
                            id = "",
                            userId = getCurrentUserId() ?: "",
                            latitude = it.latitude,
                            longitude = it.longitude,
                            accuracy = it.accuracy.toDouble(),
                            timestamp = System.currentTimeMillis().toString(),
                            createdAt = System.currentTimeMillis().toString(),
                            battery = com.bluemix.clients_lead.features.location.BatteryUtils.getBatteryPercentage(context)
                        )
                        _uiState.update { it.copy(
                            currentLocation = latLng,
                            currentLocationLog = locationLog
                        ) }
                    }
                } catch (e: Exception) { }
            }
        }
    }
    private fun observeTrackingState() {
        viewModelScope.launch {
            locationTrackingStateManager.trackingState.collect { isTracking ->
                // Flag removed as per STRICT policy
                if (!authResolved) return@collect
                if (!isTracking) {
                    val isAdmin = _uiState.value.isAdmin
                    if (!isAdmin) {
                        // OS-Level Reliability: Check permissions/GPS before auto-restarting
                        val lManager = com.bluemix.clients_lead.features.location.LocationManager(context)
                        if (lManager.hasLocationPermission() && lManager.isLocationEnabled()) {
                            Timber.w("STRICT: Tracking is OFF for agent with valid permissions. Restarting...")
                            locationTrackingStateManager.startTracking()
                        } else {
                            Timber.d("ℹ️ Tracking is disabled due to missing permissions or GPS. Not auto-restarting to avoid loops.")
                        }
                        
                        _uiState.update { it.copy(
                            clients = emptyList(),
                            selectedClient = null,
                            isLoading = false,
                            error = null,
                            currentLocation = null,
                            currentLocationLog = null
                        ) }
                    }
                } else {
                    loadClients()
                }
            }
        }
    }
    fun startJourney(clientId: String, transportMode: String = "Car") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Ensure tracking is enabled
                if (!locationTrackingStateManager.isCurrentlyTracking()) {
                    locationTrackingStateManager.startTracking()
                }
                // Set active journey client
                _uiState.update { it.copy(
                    activeJourneyClientId = clientId,
                    isLoading = false,
                    error = null
                ) }
                // Log journey start with client name and mode
                val userId = getCurrentUserId() ?: return@launch
                val client = _uiState.value.clients.find { it.id == clientId }
                val clientName = client?.name ?: clientId
                
                _uiState.value.currentLocation?.let { loc ->
                    insertLocationLogUseCase(
                        userId = userId,
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracy = _uiState.value.currentLocationAccuracy?.toDouble(),
                        battery = com.bluemix.clients_lead.features.location.BatteryUtils.getBatteryPercentage(context),
                        clientId = clientId,
                        markActivity = "JOURNEY_START",
                        markNotes = "Agent started journey to $clientName via $transportMode"
                    )
                }
                // Tell background service about the active client with coordinates and mode
                locationTrackingStateManager.updateActiveClient(
                    clientId = clientId,
                    clientName = clientName,
                    transportMode = transportMode,
                    latitude = client?.latitude,
                    longitude = client?.longitude
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to start journey: ${e.message}") }
            }
        }
    }
    fun stopJourney() {
        viewModelScope.launch {
            val clientId = _uiState.value.activeJourneyClientId ?: return@launch
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Log journey stop with client name
                val userId = getCurrentUserId() ?: return@launch
                val client = _uiState.value.clients.find { it.id == clientId }
                val clientName = client?.name ?: clientId
                _uiState.value.currentLocation?.let { loc ->
                    insertLocationLogUseCase(
                        userId = userId,
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracy = _uiState.value.currentLocationAccuracy?.toDouble(),
                        battery = com.bluemix.clients_lead.features.location.BatteryUtils.getBatteryPercentage(context),
                        clientId = clientId,
                        markActivity = "JOURNEY_STOP",
                        markNotes = "Agent ended journey to $clientName"
                    )
                }
                _uiState.update { it.copy(
                    activeJourneyClientId = null,
                    isLoading = false,
                    error = null
                ) }
                locationTrackingStateManager.updateActiveClient(null)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to stop journey: ${e.message}") }
            }
        }
    }
    fun refreshTrackingState() {
        locationTrackingStateManager.updateTrackingState()
        requestCurrentLocation()
        loadClients()
    }
    fun updateCurrentLocation(location: LatLng, accuracy: Float? = null) {
        _uiState.update { it.copy(
            currentLocation = location,
            currentLocationAccuracy = accuracy
        ) }
    }
    fun requestCurrentLocation() {
        viewModelScope.launch {
            try {
                val locationManager = com.bluemix.clients_lead.features.location.LocationManager(
                    context = context
                )
                val location = locationManager.getLastKnownLocation()
                location?.let {
                    val latLng = LatLng(it.latitude, it.longitude)
                    updateCurrentLocation(latLng, it.accuracy)
                }
            } catch (e: Exception) { }
        }
    }
    fun selectClient(client: Client?) {
        _uiState.update { it.copy(selectedClient = client, selectedAgent = null) }
    }
    fun selectAgent(agent: com.bluemix.clients_lead.domain.repository.AgentLocation?) {
        _uiState.update { it.copy(
            selectedAgent = agent, 
            selectedClient = null,
            selectedAgentJourney = emptyList() // Reset journey on new selection
        ) }
        
        // This is handled in MapScreen by observers but we clear internal logic here if needed
        agent?.let { fetchSelectedAgentJourney(it.id) }
    }

    private fun fetchSelectedAgentJourney(agentId: String) {
        viewModelScope.launch {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val dateStr = sdf.format(java.util.Calendar.getInstance().time)
            
            _uiState.update { it.copy(isLoading = true) }
            when (val result = getLocationLogsByDateRange(agentId, dateStr, dateStr, limit = 1500, page = 1)) {
                is AppResult.Success -> {
                    // ✅ Optimization: Preserve ALL activities, but filter movement by 30m distance to handle long routes
                    val rawLogs = result.data.sortedBy { it.timestamp }
                    val filteredLogs = mutableListOf<LocationLog>()
                    
                    rawLogs.forEachIndexed { index, log ->
                        val isActivity = !log.markActivity.isNullOrBlank()
                        if (index == 0 || index == rawLogs.size - 1 || isActivity) {
                            filteredLogs.add(log)
                        } else {
                            val prev = filteredLogs.last()
                            val dist = calculateDistance(
                                prev.latitude, prev.longitude, log.latitude, log.longitude
                            )
                            // Skip if too close to previous saved point (unless it's an activity)
                            if (dist > 30.0) {
                                filteredLogs.add(log)
                            }
                        }
                    }
                    
                    // ✅ Calculate Stats
                    val verifiedCount = filteredLogs.count { it.markActivity == "MEETING_END" && !it.markNotes.isNullOrBlank() && it.markNotes!!.contains("proof", ignoreCase = true) }
                    
                    // ✅ Calculate Overdue Nearby (Admins only)
                    var overdueCount = 0
                    val agent = _uiState.value.agents.find { it.id == agentId }
                    if (agent?.latitude != null && agent.longitude != null) {
                        overdueCount = _uiState.value.clients.count { client ->
                            client.getVisitStatusColor() == VisitStatus.OVERDUE &&
                            client.latitude != null && client.longitude != null &&
                            calculateDistance(
                                agent.latitude, agent.longitude, client.latitude, client.longitude
                            ) <= 500.0
                        }
                    }

                    _uiState.update { it.copy(
                        selectedAgentJourney = filteredLogs, 
                        selectedAgentVerifiedVisits = verifiedCount,
                        selectedAgentOverdueNearby = overdueCount,
                        isLoading = false
                    ) }
                }
                is AppResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.error.message) }
                    Timber.e("Failed to fetch journey for agent $agentId: ${result.error.message}")
                }
            }
        }
    }
    fun updateAddress(clientId: String, newAddress: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingAddress = true, updateError = null) }
            when (val result = updateClientAddress(clientId, newAddress)) {
                is AppResult.Success -> {
                    val updatedClient = result.data
                    val updatedClients = _uiState.value.clients.map { if (it.id == clientId) updatedClient else it }
                    _uiState.update { it.copy(
                        clients = updatedClients,
                        selectedClient = updatedClient,
                        isUpdatingAddress = false
                    ) }
                }
                is AppResult.Error -> {
                    _uiState.update { it.copy(
                        isUpdatingAddress = false,
                        updateError = result.error.message ?: "Failed to update address"
                    ) }
                }
            }
        }
    }
    fun clearUpdateError() {
        _uiState.update { it.copy(updateError = null) }
    }

    fun tagLocation(clientId: String) {
        viewModelScope.launch {
            val currentLocation = _uiState.value.currentLocation
            if (currentLocation == null) {
                _uiState.update { it.copy(error = "Location not available. Please enable location services.") }
                return@launch
            }
            
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            when (val result = updateClientLocation(
                clientId = clientId,
                latitude = currentLocation.latitude,
                longitude = currentLocation.longitude,
                accuracy = _uiState.value.currentLocationAccuracy?.toDouble()
            )) {
                is AppResult.Success -> {
                    val updatedClient = result.data
                    val updatedClients = _uiState.value.clients.map { if (it.id == clientId) updatedClient.copy() else it }
                    _uiState.update { currentState ->
                        currentState.copy(
                            clients = updatedClients,
                            filteredClients = filterClients(updatedClients, currentState.searchQuery),
                            selectedClient = updatedClient,
                            isLoading = false,
                            error = "Location successfully tagged!"
                        )
                    }
                }
                is AppResult.Error -> {
                    _uiState.update { it.copy(
                        isLoading = false,
                        error = "Failed to tag location: ${result.error.message}"
                    ) }
                }
            }
        }
    }
    fun updateQuickVisitStatus(clientId: String, visitType: String, notes: String? = null) {
        viewModelScope.launch {
            try {
                val currentLocation = _uiState.value.currentLocation
                if (currentLocation == null) {
                    _uiState.update { it.copy(error = "Location not available. Please enable location services.") }
                    return@launch
                }
                when (val result = createQuickVisit(
                    clientId = clientId,
                    visitType = visitType,
                    latitude = currentLocation.latitude,
                    longitude = currentLocation.longitude,
                    accuracy = _uiState.value.currentLocationAccuracy?.toDouble(),
                    notes = notes
                )) {
                    is AppResult.Success -> {
                        val updatedClient = result.data
                        val updatedClients = _uiState.value.clients.map { if (it.id == updatedClient.id) updatedClient.copy() else it }
                        _uiState.update { it.copy(
                            clients = updatedClients,
                            filteredClients = filterClients(updatedClients, it.searchQuery),
                            selectedClient = null,
                            error = null
                        ) }
                    }
                    is AppResult.Error -> {
                        _uiState.update { it.copy(error = result.error.message ?: "Failed to record visit") }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Unexpected error: ${e.message}") }
            }
        }
    }
    fun loadClients() {
        viewModelScope.launch {
            val isAdmin = _uiState.value.isAdmin
            val isTracking = locationTrackingStateManager.isCurrentlyTracking()
            if (!isAdmin && !isTracking) {
                _uiState.update { it.copy(isLoading = false, clients = emptyList()) }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, error = null) }
            val userId = getCurrentUserId() ?: return@launch
            when (val result = getClientsWithLocation(userId, isAdmin)) {
                is AppResult.Success -> {
                    val clientsResult = result.data
                    val allClients = clientsResult.clients
                    val mapReadyClients = allClients.filter { it.latitude != null && it.longitude != null }
                    val jitteredClients = applyJitterToOverlapping(mapReadyClients)
                    _uiState.update { currentState ->
                        currentState.copy(
                            clients = allClients,
                            hiddenClientsCount = allClients.size - mapReadyClients.size,
                            territoryMessage = clientsResult.message,
                            filteredClients = filterClients(jitteredClients, currentState.searchQuery, mustHaveLocation = true),
                            isLoading = false 
                        )
                    }
                    if (_uiState.value.isAdmin) loadTeamMembers()
                }
                is AppResult.Error -> {
                    val msg = result.error.message ?: "Unknown error"
                    val filteredMsg = if (msg.contains("NoPincodeFound", ignoreCase = true) || msg.contains("enable location tracking", ignoreCase = true)) {
                        "📍 Detecting your location... please wait a moment."
                    } else {
                        msg
                    }
                    _uiState.update { it.copy(isLoading = false, error = filteredMsg) }
                }
            }
        }
    }
    private suspend fun loadTeamMembers() {
        val result = if (_uiState.value.isAdmin) {
            getLiveAgents() 
        } else {
            getTeamLocations()
        }

        when (val resultData = result) {
            is AppResult.Success -> {
                val agents = resultData.data
                _uiState.update { it.copy(
                    agents = agents, 
                    onlineAgentsCount = agents.count { a -> a.latitude != null && com.bluemix.clients_lead.core.common.utils.DateTimeUtils.isRecent(a.timestamp) },
                    isLoading = false
                ) }
                
                // Refresh selected agent if any
                _uiState.value.selectedAgent?.let { current ->
                    val updated = agents.find { it.id == current.id }
                    if (updated != null) {
                        _uiState.update { it.copy(selectedAgent = updated) }
                    }
                }
            }
            is AppResult.Error -> {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun fetchDailySummary() {
        if (!_uiState.value.isAdmin) return
        viewModelScope.launch {
            when (val result = getDailySummary()) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(dailySummary = result.data) }
                }
                is AppResult.Error -> {
                    Timber.e("Failed to fetch daily summary: ${result.error.message}")
                }
            }
        }
    }

    fun toggleClientVisibility() {
        _uiState.update { it.copy(showClients = !it.showClients) }
    }

    fun setAgentFilter(filter: String) {
        _uiState.update { it.copy(agentFilter = filter) }
    }

    fun toggleAgentRoster() {
        _uiState.update { it.copy(showAgentRoster = !it.showAgentRoster) }
    }
    fun logout() {
        // ✅ NEW: Non-blocking logout. Trigger sign-out immediately and fire-and-forget the log.
        _uiState.update { it.copy(isLoading = true) }
        
        val currentLocation = _uiState.value.currentLocation
        val email = _uiState.value.userEmail ?: "Unknown"

        viewModelScope.launch(Dispatchers.IO) {
            val userId = getCurrentUserId()
            if (currentLocation != null && userId != null) {
                insertLocationLogUseCase(
                    userId = userId,
                    latitude = currentLocation.latitude,
                    longitude = currentLocation.longitude,
                    battery = com.bluemix.clients_lead.features.location.BatteryUtils.getBatteryPercentage(context),
                    markActivity = "LOGOUT",
                    markNotes = "Agent ($email) logged out"
                )
            }
        }
        
        viewModelScope.launch {
            signOut()
        }
    }
    fun refresh() {
        loadClients()
    }
    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            val snapshot = _uiState.value.clients
            val localResults = filterClients(snapshot, query, mustHaveLocation = true)
            val jitteredResults = applyJitterToOverlapping(localResults)
            _uiState.update { it.copy(
                filteredClients = jitteredResults,
                error = if (query.isNotEmpty() && jitteredResults.isEmpty()) "No clients found" else null
            ) }
            if (query.length >= 3 && jitteredResults.isEmpty()) performRemoteSearch(query)
        }
    }
    private fun performRemoteSearch(query: String) {
        viewModelScope.launch {
            val userId = getCurrentUserId() ?: return@launch
            _uiState.update { it.copy(isSearchingRemote = true) }
            when (val result = searchRemoteClients(userId, query)) {
                is AppResult.Success -> {
                    val mapReady = result.data.filter { it.latitude != null && it.longitude != null }
                    _uiState.update { it.copy(
                        filteredClients = applyJitterToOverlapping(mapReady),
                        isSearchingRemote = false
                    )}
                }
                is AppResult.Error -> _uiState.update { it.copy(isSearchingRemote = false) }
            }
        }
    }
    private fun applyJitterToOverlapping(clients: List<Client>): List<Client> {
        val locationMap = mutableMapOf<String, Int>()
        return clients.map { client ->
            val lat = client.latitude ?: 0.0
            val lng = client.longitude ?: 0.0
            val key = String.format("%.6f,%.6f", lat, lng)
            val count = locationMap.getOrDefault(key, 0)
            locationMap[key] = count + 1
            if (count > 0) {
                client.copy(latitude = lat + (Math.sin(count.toDouble()) * 0.00008), longitude = lng + (Math.cos(count.toDouble()) * 0.00008))
            } else client
        }
    }
    private fun filterClients(clients: List<Client>, searchQuery: String, mustHaveLocation: Boolean = true): List<Client> {
        var result = if (mustHaveLocation) clients.filter { it.latitude != null && it.longitude != null } else clients
        if (searchQuery.isBlank()) return result
        val q = searchQuery.lowercase().trim()
        return result.filter { it.name.lowercase().contains(q) || it.address?.lowercase()?.contains(q) == true || it.pincode?.contains(q) == true }
    }

    fun toggleOnlineAgentsFilter() {
        _uiState.update { it.copy(showOnlineAgentsOnly = !it.showOnlineAgentsOnly) }
    }

    fun toggleHighAccuracyFilter() {
        _uiState.update { it.copy(showHighAccuracyOnly = !it.showHighAccuracyOnly) }
    }
}

private fun calculateDistance(
    lat1: Double,
    lng1: Double,
    lat2: Double,
    lng2: Double
): Double {
    val results = FloatArray(1)
    android.location.Location.distanceBetween(
        lat1, lng1, lat2, lng2, results
    )
    return results[0].toDouble()
}
