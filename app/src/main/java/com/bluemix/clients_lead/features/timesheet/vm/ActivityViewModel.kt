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
    val isAdmin: Boolean = false  // ✅ NEW: Track admin status
)

class ActivityViewModel(
    private val context: android.content.Context,
    private val getLocationLogsByDateRange: GetLocationLogsByDateRange,
    private val getCurrentUserId: GetCurrentUserId,
    private val getClientServices: GetClientServices,
    private val acceptClientService: AcceptClientService,
    private val getTripExpenses: GetTripExpensesUseCase,
    private val getUserMeetings: GetUserMeetings,
    private val observeAuthState: com.bluemix.clients_lead.domain.usecases.ObserveAuthState // ✅ NEW
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
                _uiState.update { it.copy(isAdmin = user?.isAdmin == true) }
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
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }

    fun loadDailySummary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val userId = getCurrentUserId()
            if (userId == null) {
                _uiState.update { it.copy(isLoading = false, error = "User not authenticated") }
                return@launch
            }

            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            
            // ✅ NEW: If admin, fetch for the whole team
            val queryUserId = if (_uiState.value.isAdmin) "all" else userId

            // 1. Fetch Location Logs
            val logsResult = getLocationLogsByDateRange(queryUserId, today, today)
            if (logsResult is AppResult.Success) {
                val logs = logsResult.data.sortedBy { it.timestamp }
                val distance = LocationUtils.calculateTotalDistanceKm(logs)
                val duration = LocationUtils.calculateActiveDurationMinutes(logs)

                _uiState.update { it.copy(
                    logs = logs,
                    totalDistanceKm = distance,
                    activeDurationMinutes = duration
                ) }
                resolveAddresses(logs)
            }

            // 2. Fetch Meetings for today (authoritative source for clientsVisitedCount)
            val meetingsResult = getUserMeetings(queryUserId)
            if (meetingsResult is AppResult.Success) {
                val todayStart = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                val todayEnd = todayStart + 86400000L

                val todayMeetings = meetingsResult.data.filter { meeting ->
                    try {
                        // Parse ISO start time from backend
                        val startMs = java.time.Instant.parse(meeting.startTime).toEpochMilli()
                        startMs in todayStart..todayEnd
                    } catch (e: Exception) {
                        Timber.w("Could not parse meeting startTime: ${meeting.startTime}")
                        false
                    }
                }

                val completedToday = todayMeetings.count {
                    it.status.name == "COMPLETED"
                }

                _uiState.update { it.copy(
                    meetings = todayMeetings,
                    clientsVisitedCount = completedToday
                ) }
            } else {
                Timber.w("Failed to load meetings: ${(meetingsResult as? AppResult.Error)?.error?.message}")
                // Fallback: count MEETING_START marks in location logs
                val fallbackVisits = _uiState.value.logs.count { it.markActivity == "MEETING_START" }
                _uiState.update { it.copy(clientsVisitedCount = fallbackVisits) }
            }

            // 3. Fetch Expenses
            val expensesResult = getTripExpenses(queryUserId)
            if (expensesResult is AppResult.Success) {
                val todayEpoch = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                val todayExpenses = expensesResult.data.filter { it.travelDate >= todayEpoch }

                _uiState.update { it.copy(
                    expenses = todayExpenses,
                    totalExpenseAmount = todayExpenses.sumOf { exp -> exp.amountSpent },
                    isLoading = false
                ) }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
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
        loadDailySummary()
        loadServices()
    }
}