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
data class MapUiState(
    val isLoading: Boolean = false,
    val clients: List<Client> = emptyList(),
    val agents: List<com.bluemix.clients_lead.domain.repository.AgentLocation> = emptyList(),
    val currentLocation: LatLng? = null,
    val currentLocationLog: LocationLog? = null,
    val selectedClient: Client? = null,
    val selectedAgent: com.bluemix.clients_lead.domain.repository.AgentLocation? = null,
    val isTrackingEnabled: Boolean = false,
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
    val showHighAccuracyOnly: Boolean = false
)
class MapViewModel(
    private val getClientsWithLocation: GetClientsWithLocation,
    private val getCurrentUserId: GetCurrentUserId,
    private val locationTrackingStateManager: LocationTrackingStateManager,
    private val createQuickVisit: CreateQuickVisit,
    private val updateClientAddress: UpdateClientAddress,
    private val getTeamLocations: GetTeamLocations,
    private val observeAuthState: ObserveAuthState,
    private val searchRemoteClients: SearchRemoteClients,
    private val insertLocationLog: InsertLocationLog,
    private val createClient: CreateClient,
    private val signOut: com.bluemix.clients_lead.domain.usecases.SignOut,
    private val context: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null
    private var teamRefreshJob: Job? = null
    init {
        observeTrackingState()
        observeAuth()
        refreshTrackingState()
        fetchLocationImmediately()
        startLocationPolling()
        observeLocationSettings()
    }
    @Volatile private var authResolved = false
    private fun observeAuth() {
        viewModelScope.launch {
            observeAuthState().collect { user ->
                val isAdmin = user?.isAdmin ?: false
                Timber.d("ðŸ”‘ Auth Update: ${user?.email}, isAdmin=$isAdmin, companyId=${user?.companyId}")
                _uiState.update { it.copy(isAdmin = isAdmin) }
                if (!authResolved) {
                    authResolved = true
                    Timber.d("ðŸš€ Initial Load Triggered (isAdmin=$isAdmin)")
                    if (isAdmin) {
                        loadClients()
                        startTeamPolling()
                    } else {
                        if (!locationTrackingStateManager.isCurrentlyTracking()) {
                            Timber.d("ðŸš€ Auto-starting tracking for agent...")
                            locationTrackingStateManager.startTracking()
                        } else {
                            loadClients()
                        }
                    }
                } else if (isAdmin) {
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
                        insertLocationLog(
                            userId = userId,
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            markActivity = "CLOCK_IN",
                            markNotes = "Agent started work session"
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
                        insertLocationLog(
                            userId = userId,
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            markActivity = "CLOCK_OUT",
                            markNotes = "Agent ended work session"
                        )
                    }
                }
                locationTrackingStateManager.stopTracking()
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
                delay(30000)
            }
        }
    }
    private fun stopTeamPolling() {
        teamRefreshJob?.cancel()
        teamRefreshJob = null
    }
    private fun refreshTeamLocations() {
        viewModelScope.launch {
            when (val result = getTeamLocations()) {
                is AppResult.Success -> {
                    val agents = result.data
                    _uiState.update { it.copy(
                        agents = agents,
                        onlineAgentsCount = agents.count { a -> 
                            a.latitude != null && com.bluemix.clients_lead.core.common.utils.DateTimeUtils.isRecent(a.timestamp)
                        }
                    ) }
                }
                is AppResult.Error -> {
                    Timber.e("Failed to refresh team locations: ${result.error.message}")
                }
            }
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
                _uiState.value = _uiState.value.copy(isTrackingEnabled = isTracking)
                if (!authResolved) return@collect
                if (!isTracking) {
                    val isAdmin = _uiState.value.isAdmin
                    if (!isAdmin) {
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
    fun startJourney(clientId: String) {
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
                // Log journey start
                val userId = getCurrentUserId() ?: return@launch
                _uiState.value.currentLocation?.let { loc ->
                    insertLocationLog(
                        userId = userId,
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracy = _uiState.value.currentLocationAccuracy?.toDouble(),
                        clientId = clientId,
                        markActivity = "JOURNEY_START",
                        markNotes = "Agent started journey to client: $clientId"
                    )
                }
                // Update backend tracker service about the target client
                locationTrackingStateManager.startTracking() // Ensure it's active
                locationTrackingStateManager.updateActiveClient(clientId)
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
                // Log journey stop
                val userId = getCurrentUserId() ?: return@launch
                _uiState.value.currentLocation?.let { loc ->
                    insertLocationLog(
                        userId = userId,
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracy = _uiState.value.currentLocationAccuracy?.toDouble(),
                        clientId = clientId,
                        markActivity = "JOURNEY_STOP",
                        markNotes = "Agent stopped journey to client: $clientId"
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
    fun enableTracking() {
        viewModelScope.launch {
            if (!locationTrackingStateManager.isLocationEnabled()) return@launch
            locationTrackingStateManager.startTracking()
        }
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
                    context = org.koin.core.context.GlobalContext.get().get()
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
        _uiState.update { it.copy(selectedAgent = agent, selectedClient = null) }
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
                        _uiState.value = _uiState.value.copy(error = result.error.message ?: "Failed to record visit")
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
                _uiState.value = _uiState.value.copy(isLoading = false, clients = emptyList())
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
                    if (isAdmin) loadTeamMembers()
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
        when (val result = getTeamLocations()) {
            is AppResult.Success -> {
                _uiState.update { it.copy(agents = result.data, isLoading = false) }
            }
            is AppResult.Error -> {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    fun logout() {
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
