package com.bluemix.clients_lead.features.admin.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.repository.AgentLocation
import com.bluemix.clients_lead.domain.repository.VisibilityFilter
import com.bluemix.clients_lead.domain.usecases.GetTeamLocations
import com.bluemix.clients_lead.domain.usecases.GetDashboardStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class AdminDashboardUiState(
    val isLoading: Boolean = false,
    val agents: List<AgentLocation> = emptyList(),
    val filteredAgents: List<AgentLocation> = emptyList(),
    val visibilityFilter: VisibilityFilter = VisibilityFilter.ALL,
    val totalClients: Int = 0,
    val gpsVerifiedCount: Int = 0,
    val coveragePercent: Int = 0,
    val activeAgentsCount: Int = 0,
    val hiddenClientsCount: Int = 0,
    val isClearingLogs: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis(),
    val error: String? = null
)

class AdminDashboardViewModel(
    private val getTeamLocations: GetTeamLocations,
    private val getDashboardStats: GetDashboardStats,
    private val deleteOldLocationLogs: com.bluemix.clients_lead.domain.usecases.DeleteOldLocationLogs,
    private val retryGeocodingUseCase: com.bluemix.clients_lead.domain.usecases.RetryGeocoding
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminDashboardUiState())
    val uiState: StateFlow<AdminDashboardUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        startAutoRefresh()
    }

    fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                refreshDashboard()
                delay(30000) // 30 seconds
            }
        }
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            // Get Stats
            when (val result = getDashboardStats()) {
                is AppResult.Success -> {
                    _uiState.update { 
                        it.copy(
                            totalClients = result.data.totalClients,
                            gpsVerifiedCount = result.data.gpsVerified,
                            coveragePercent = result.data.coverage,
                            activeAgentsCount = result.data.activeAgents,
                            hiddenClientsCount = result.data.hiddenClients
                        )
                    }
                }
                is AppResult.Error -> {
                    Timber.e("❌ Error loading dashboard stats: ${result.error.message}")
                }
            }

            // Get Team Locations
            when (val result = getTeamLocations()) {
                is AppResult.Success -> {
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            agents = result.data,
                            lastUpdated = System.currentTimeMillis()
                        )
                    }
                    applyFilters()
                }
                is AppResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.error.message) }
                }
            }
        }
    }

    fun onVisibilityFilterChanged(filter: VisibilityFilter) {
        _uiState.update { it.copy(visibilityFilter = filter) }
        applyFilters()
    }

    private fun applyFilters() {
        val allAgents = _uiState.value.agents
        val visibility = _uiState.value.visibilityFilter

        val filtered = allAgents.filter { agent ->
            when (visibility) {
                VisibilityFilter.ALL -> true
                VisibilityFilter.SEEN_TODAY -> com.bluemix.clients_lead.core.common.utils.DateTimeUtils.isToday(agent.timestamp)
                VisibilityFilter.UNSEEN_TODAY -> !com.bluemix.clients_lead.core.common.utils.DateTimeUtils.isToday(agent.timestamp)
            }
        }

        _uiState.update { it.copy(filteredAgents = filtered, lastUpdated = System.currentTimeMillis()) }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearingLogs = true, error = null) }
            when (val result = deleteOldLocationLogs.clearAll()) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(isClearingLogs = false) }
                    refreshDashboard()
                }
                is AppResult.Error -> {
                    _uiState.update { it.copy(isClearingLogs = false, error = "Failed to clear logs: ${result.error.message}") }
                }
            }
        }
    }

    fun retryGeocoding() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            retryGeocodingUseCase()
            refreshDashboard()
        }
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }
}
