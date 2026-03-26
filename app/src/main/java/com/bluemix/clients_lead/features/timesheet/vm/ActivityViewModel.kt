package com.bluemix.clients_lead.features.timesheet.vm
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluemix.clients_lead.domain.model.LocationLog
import com.bluemix.clients_lead.domain.model.ClientService
import com.bluemix.clients_lead.domain.model.Meeting
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.usecases.GetCurrentUserId
import com.bluemix.clients_lead.domain.usecases.GetLocationLogsByDateRange
import com.bluemix.clients_lead.domain.usecases.GetClientServices
import com.bluemix.clients_lead.domain.usecases.AcceptClientService
import com.bluemix.clients_lead.domain.usecases.GetTripExpensesUseCase
import com.bluemix.clients_lead.domain.usecases.GetUserMeetings
import com.bluemix.clients_lead.domain.model.TripExpense
import com.bluemix.clients_lead.core.common.utils.LocationUtils
import timber.log.Timber
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ActivityUiState(
    val isLoading: Boolean = false,
    val logs: List<LocationLog> = emptyList(),
    val services: List<ClientService> = emptyList(),
    val expenses: List<TripExpense> = emptyList(),
    val meetings: List<Meeting> = emptyList(),
    val error: String? = null,
    val selectedTab: Int = 0, // 0=Logs, 1=Summary, 2=Meetings, 3=Services
    val resolvedAddresses: Map<String, String> = emptyMap(), // log.id -> address

    // Day Summary Stats
    val totalDistanceKm: Double = 0.0,
    val clientsVisitedCount: Int = 0,      // count of COMPLETED meetings today
    val totalExpenseAmount: Double = 0.0,
    val activeDurationMinutes: Long = 0,
    val isAdmin: Boolean = false,
    val showAllAgents: Boolean = false,
    val currentLocation: com.bluemix.clients_lead.domain.repository.AgentLocation? = null,
    
    // Pagination
    val currentPage: Int = 1,
    val isEndReached: Boolean = false
)

class ActivityViewModel(
    private val context: android.content.Context,
    private val getLocationLogsByDateRange: GetLocationLogsByDateRange,
    private val getCurrentUserId: GetCurrentUserId,
    private val getClientServices: GetClientServices,
    private val acceptClientService: AcceptClientService,
    private val getTripExpenses: GetTripExpensesUseCase,
    private val getUserMeetings: GetUserMeetings,
    private val observeAuthState: com.bluemix.clients_lead.domain.usecases.ObserveAuthState, // ✅ NEW
    private val getTeamLocations: com.bluemix.clients_lead.domain.usecases.GetTeamLocations // ✅ NEW for Admin Logs
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivityUiState())
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        observeUserRole()
        startAutoRefresh()
    }

    private fun observeUserRole() {
        viewModelScope.launch {
            observeAuthState().collect { user ->
                val isAdmin = user?.isAdmin == true
                _uiState.update { it.copy(
                    isAdmin = isAdmin,
                    showAllAgents = if (isAdmin) true else it.showAllAgents
                ) }
                if (isAdmin) {
                    loadDailySummary()
                }
            }
        }
    }

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                loadDailySummary()
                loadServices()
                delay(30000)
            }
        }
        
        // Use an additional job for high-frequency location updates (Real-time distance)
        viewModelScope.launch {
            val locationManager = com.bluemix.clients_lead.features.location.LocationManager(context)
            while (true) {
                if (locationManager.hasLocationPermission() && locationManager.isLocationEnabled()) {
                    val location = locationManager.getLastKnownLocation()
                    location?.let { loc ->
                        _uiState.update { state ->
                            val lastLog = state.logs.firstOrNull()
                            val pendingDist = if (lastLog != null) {
                                com.bluemix.clients_lead.core.common.utils.LocationUtils.calculateDistanceMeters(
                                    lastLog.latitude, lastLog.longitude,
                                    loc.latitude, loc.longitude
                                ) / 1000.0
                            } else 0.0
                            
                            // Re-calculate base distance from logs to ensure accuracy
                            val baseDist = com.bluemix.clients_lead.core.common.utils.LocationUtils.calculateTotalDistanceKm(state.logs)
                            
                            state.copy(
                                totalDistanceKm = baseDist + pendingDist,
                                currentLocation = com.bluemix.clients_lead.domain.repository.AgentLocation(
                                    id = "",
                                    email = "",
                                    fullName = null,
                                    latitude = loc.latitude,
                                    longitude = loc.longitude,
                                    accuracy = loc.accuracy.toDouble(),
                                    timestamp = System.currentTimeMillis().toString(),
                                    activity = null,
                                    battery = null
                                )
                            )
                        }
                    }
                }
                delay(5000) // Update every 5 seconds for real-time feel
            }
        }
    }

    fun toggleAllAgents() {
        _uiState.update { it.copy(showAllAgents = !it.showAllAgents, logs = emptyList(), currentPage = 1, isEndReached = false) }
        viewModelScope.launch {
            loadDailySummary(isRefresh = true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }

    fun loadDailySummary(isRefresh: Boolean = false) {
        if (_uiState.value.isLoading || (_uiState.value.isEndReached && !isRefresh)) return

        viewModelScope.launch {
            if (isRefresh) {
                _uiState.update { it.copy(isLoading = true, error = null, logs = emptyList(), currentPage = 1, isEndReached = false) }
            } else {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }

            val userId = getCurrentUserId()
            if (userId == null) {
                _uiState.update { it.copy(isLoading = false, error = "User not authenticated") }
                return@launch
            }

            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val pageToLoad = if (isRefresh) 1 else _uiState.value.currentPage
            val pageSize = 50

            // 1. Fetch Location Logs
            val aggregatedLogs = mutableListOf<LocationLog>()
            var baseDistance = if (isRefresh) 0.0 else _uiState.value.totalDistanceKm
            var baseDuration = if (isRefresh) 0L else _uiState.value.activeDurationMinutes

            val lRes = if (_uiState.value.showAllAgents && _uiState.value.isAdmin) {
                getLocationLogsByDateRange("all", today, today, limit = pageSize, page = pageToLoad)
            } else {
                getLocationLogsByDateRange(userId, today, today, limit = pageSize, page = pageToLoad)
            }

            if (lRes is AppResult.Success) {
                aggregatedLogs.addAll(lRes.data)
                if (isRefresh) {
                    baseDistance = LocationUtils.calculateTotalDistanceKm(lRes.data)
                    baseDuration = LocationUtils.calculateActiveDurationMinutes(lRes.data)
                }
                
                val isEnd = lRes.data.size < pageSize
                _uiState.update { it.copy(
                    logs = if (isRefresh) lRes.data else it.logs + lRes.data,
                    totalDistanceKm = baseDistance,
                    activeDurationMinutes = baseDuration,
                    currentPage = pageToLoad + 1,
                    isEndReached = isEnd,
                    isLoading = false
                ) }
                
                // Only resolve addresses for the new batch to save cycles
                resolveAddresses(lRes.data)
            } else if (lRes is AppResult.Error) {
                _uiState.update { it.copy(isLoading = false, error = (lRes as AppResult.Error).error.message) }
            }

            // Fetch Summary Data (Meetings/Expenses) only on first page or refresh
            if (pageToLoad == 1) {
                loadSummaryData(userId, today)
            }
        }
    }

    private suspend fun loadSummaryData(userId: String, today: String) {
        val aggregatedMeetings = mutableListOf<Meeting>()
        val aggregatedExpenses = mutableListOf<TripExpense>()

        if (_uiState.value.showAllAgents && _uiState.value.isAdmin) {
            val mRes = getUserMeetings("all")
            if (mRes is AppResult.Success) aggregatedMeetings.addAll(mRes.data)
            
            val eRes = getTripExpenses("all")
            if (eRes is AppResult.Success) aggregatedExpenses.addAll(eRes.data)
        } else {
            val mRes = getUserMeetings(userId)
            if (mRes is AppResult.Success) aggregatedMeetings.addAll(mRes.data)

            val eRes = getTripExpenses(userId)
            if (eRes is AppResult.Success) aggregatedExpenses.addAll(eRes.data)
        }

        // Process Meetings
        val todayStart = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayEnd = todayStart + 86400000L

        val todayMeetings = aggregatedMeetings.filter { meeting ->
            try {
                val startMs = java.time.Instant.parse(meeting.startTime).toEpochMilli()
                startMs in todayStart..todayEnd
            } catch (e: Exception) { false }
        }
        val completedToday = todayMeetings.count { it.status.name == "COMPLETED" }

        // Process Expenses
        val todayExpenses = aggregatedExpenses.filter { it.travelDate >= todayStart }

        _uiState.update { it.copy(
            meetings = todayMeetings,
            clientsVisitedCount = completedToday.takeIf { it > 0 } ?: it.logs.count { log -> log.markActivity == "MEETING_START" },
            expenses = todayExpenses,
            totalExpenseAmount = todayExpenses.sumOf { exp -> exp.amountSpent }
        ) }
    }

    private fun resolveAddresses(logs: List<LocationLog>) {
        viewModelScope.launch {
            val currentAddresses = _uiState.value.resolvedAddresses.toMutableMap()

            logs.forEach { log ->
                if (!currentAddresses.containsKey(log.id)) {
                    val address = com.bluemix.clients_lead.core.common.utils.LocationUtils.getAddress(
                        context, log.latitude, log.longitude
                    )
                    if (address != null) {
                        currentAddresses[log.id] = address
                        _uiState.update { it.copy(resolvedAddresses = currentAddresses.toMap()) }
                    }
                }
            }
        }
    }

    fun loadServices() {
        viewModelScope.launch {
            when (val result = getClientServices()) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(services = result.data) }
                }
                is AppResult.Error -> {
                    // Silently fail for background sync
                }
            }
        }
    }

    fun acceptService(serviceId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = acceptClientService(serviceId, "active")) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    loadServices()
                }
                is AppResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.error.message) }
                }
            }
        }
    }

    fun onTabSelected(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun refresh() {
        loadDailySummary(isRefresh = true)
        loadServices()
    }
}