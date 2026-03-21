package com.bluemix.clients_lead.features.admin.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.model.LocationLog
import com.bluemix.clients_lead.domain.repository.AgentLocation
import com.bluemix.clients_lead.domain.usecases.GetLocationLogsByDateRange
import com.bluemix.clients_lead.domain.usecases.GetTeamLocations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    
    // Stats
    val totalDistanceKm: Double = 0.0,
    val activeDurationMinutes: Long = 0,
    val clientsVisited: Int = 0,
    val plannedClients: Int = 0,
    val gpsPointCount: Int = 0,
    val totalExpenses: Double = 0.0,
    val expenses: List<com.bluemix.clients_lead.domain.model.TripExpense> = emptyList(),
    val resolvedAddresses: Map<String, String> = emptyMap(), // ✅ NEW
    
    val error: String? = null
)

class AdminJourneyViewModel(
    private val context: android.content.Context, // ✅ NEW
    private val getTeamLocations: GetTeamLocations,
    private val getLocationLogsByDateRange: GetLocationLogsByDateRange,
    private val getTripExpenses: com.bluemix.clients_lead.domain.usecases.GetTripExpensesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminJourneyUiState())
    val uiState: StateFlow<AdminJourneyUiState> = _uiState.asStateFlow()

    init {
        loadAgents()
    }

    private fun loadAgents() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = getTeamLocations()) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, agents = result.data) }
                }
                is AppResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.error.message) }
                }
            }
        }
    }

    fun selectAgent(agent: AgentLocation?) {
        _uiState.update { it.copy(selectedAgent = agent) }
        fetchLogs()
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        fetchLogs()
    }

    fun setViewMode(mode: JourneyViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    private fun fetchLogs() {
        val state = _uiState.value
        val agent = state.selectedAgent ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val dateStr = state.selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE) // "yyyy-MM-dd"
            
            when (val result = getLocationLogsByDateRange(agent.id, dateStr, dateStr)) {
                is AppResult.Success -> {
                    val logs = result.data.sortedBy { it.timestamp }
                    val stats = calculateStats(logs)
                    
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
                            totalDistanceKm = stats.distanceKm,
                            activeDurationMinutes = stats.durationMinutes,
                            gpsPointCount = logs.size,
                            clientsVisited = logs.count { it.markActivity == "MEETING_START" },
                            plannedClients = logs.filter { it.markActivity == "MEETING_START" }.mapNotNull { it.clientId }.distinct().size,
                            expenses = todayExpenses,
                            totalExpenses = todayExpenses.sumOf { exp -> exp.amountSpent }
                        ) 
                    }
                    
                    resolveAddresses(logs) // ✅ NEW: Start resolving addresses
                }
                is AppResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.error.message) }
                }
            }
        }
    }

    private data class JourneyStats(val distanceKm: Double, val durationMinutes: Long)

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
