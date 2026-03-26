package com.bluemix.clients_lead.features.admin.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.repository.AgentLocation
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
    val totalClients: Int = 0,
    val gpsVerifiedCount: Int = 0, // Now Percentage
    val coveragePercent: Int = 0,
    val activeAgentsCount: Int = 0,
    val hiddenClientsCount: Int = 0,
    val isClearingLogs: Boolean = false,
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

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                refreshDashboard()
                delay(10000) // ✅ Faster 10s Real-time sync
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            // 📡 Fetch stats and team locations in parallel
            val statsDef = viewModelScope.launch {
                when (val result = getDashboardStats()) {
                    is AppResult.Success -> {
                        val stats = result.data
                        _uiState.update { 
                            it.copy(
                                totalClients = stats.totalClients,
                                activeAgentsCount = stats.activeAgents,
                                gpsVerifiedCount = stats.gpsVerified,
                                coveragePercent = stats.coverage,
                                hiddenClientsCount = stats.hiddenClients
                            )
                        }
                    }
                    is AppResult.Error -> {
                        Timber.e("Failed to load dashboard stats: ${result.error.message}")
                    }
                }
            }

            when (val result = getTeamLocations()) {
                is AppResult.Success -> {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            agents = result.data
                        )
                    }
                }
                is AppResult.Error -> {
                    Timber.e("Failed to load team locations: ${result.error.message}")
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            error = result.error.message ?: "Failed to load dashboard data"
                        )
                    }
                }
            }
        }
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
                    _uiState.update { it.copy(isClearingLogs = false, error = result.error.message) }
                }
            }
        }
    }

    fun retryGeocoding() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = retryGeocodingUseCase()) {
                is AppResult.Success -> {
                    // Give backend some time to process
                    delay(2000)
                    refreshDashboard()
                }
                is AppResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.error.message) }
                }
            }
        }
    }
}

