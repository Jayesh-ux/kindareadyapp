package com.bluemix.clients_lead.features.admin.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.model.LocationLog
import com.bluemix.clients_lead.domain.repository.AgentLocation
import com.bluemix.clients_lead.domain.usecases.GetLocationLogsByDateRange
import com.bluemix.clients_lead.domain.usecases.GetTeamLocations
import com.bluemix.clients_lead.domain.usecases.UpdateUserStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import com.bluemix.clients_lead.core.common.utils.LocationUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class AdminAgentDetailUiState(
    val isLoading: Boolean = false,
    val agent: AgentLocation? = null,
    val recentLogs: List<LocationLog> = emptyList(),
    val todayDistanceKm: Double = 0.0,
    val error: String? = null,
    val isUpdatingStatus: Boolean = false
)

class AdminAgentDetailViewModel(
    private val agentId: String,
    private val getTeamLocations: GetTeamLocations,
    private val getLocationLogsByDateRange: GetLocationLogsByDateRange,
    private val updateUserStatus: UpdateUserStatus
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminAgentDetailUiState())
    val uiState: StateFlow<AdminAgentDetailUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            // 1. Load Agent Basic Info from Team List
            val teamResult = getTeamLocations()
            if (teamResult is AppResult.Success) {
                val foundAgent = teamResult.data.find { it.id == agentId }
                _uiState.update { it.copy(agent = foundAgent) }
            }

            // 2. Load Recent Activity (Last 24h roughly or just today)
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val logsResult = getLocationLogsByDateRange(agentId, today, today)
            
            if (logsResult is AppResult.Success) {
                val sortedLogs = logsResult.data.sortedByDescending { it.timestamp }
                val distance = LocationUtils.calculateTotalDistanceKm(logsResult.data)
                
                _uiState.update { it.copy(
                    recentLogs = sortedLogs.take(20),
                    todayDistanceKm = distance,
                    isLoading = false
                ) }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun toggleStatus(newStatus: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingStatus = true) }
            when (val result = updateUserStatus(agentId, newStatus)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(
                        agent = it.agent?.copy(isActive = newStatus),
                        isUpdatingStatus = false
                    ) }
                }
                is AppResult.Error -> {
                    _uiState.update { it.copy(
                        isUpdatingStatus = false,
                        error = result.error.message
                    ) }
                }
            }
        }
    }
}
