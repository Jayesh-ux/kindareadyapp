package com.bluemix.clients_lead.features.admin.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.repository.AgentLocation
import com.bluemix.clients_lead.domain.usecases.GetTeamLocations
import com.bluemix.clients_lead.domain.usecases.UpdateUserStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class AdminUserManagementUiState(
    val isLoading: Boolean = false,
    val agents: List<AgentLocation> = emptyList(),
    val error: String? = null,
    val searchResults: List<AgentLocation> = emptyList(),
    val searchQuery: String = ""
)

class AdminUserManagementViewModel(
    private val getTeamLocations: GetTeamLocations,
    private val updateUserStatus: UpdateUserStatus
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUserManagementUiState())
    val uiState: StateFlow<AdminUserManagementUiState> = _uiState.asStateFlow()

    init {
        loadUsers()
    }

    fun loadUsers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = getTeamLocations()) {
                is AppResult.Success -> {
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            agents = result.data,
                            searchResults = if (it.searchQuery.isEmpty()) result.data else it.searchResults
                        )
                    }
                    if (_uiState.value.searchQuery.isNotEmpty()) {
                        performSearch(_uiState.value.searchQuery)
                    }
                }
                is AppResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.error.message) }
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        performSearch(query)
    }

    private fun performSearch(query: String) {
        if (query.isEmpty()) {
            _uiState.update { it.copy(searchResults = it.agents) }
            return
        }
        
        val filtered = _uiState.value.agents.filter {
            it.fullName?.contains(query, ignoreCase = true) == true || 
            it.email.contains(query, ignoreCase = true)
        }
        _uiState.update { it.copy(searchResults = filtered) }
    }

    fun toggleUserStatus(userId: String, newStatus: Boolean) {
        viewModelScope.launch {
            // Optimistic Update
            val originalAgents = _uiState.value.agents
            val updatedAgents = originalAgents.map { agent ->
                if (agent.id == userId) agent.copy(isActive = newStatus) else agent
            }
            
            _uiState.update { it.copy(
                agents = updatedAgents,
                searchResults = if (it.searchQuery.isEmpty()) updatedAgents else it.searchResults.map { a ->
                    if (a.id == userId) a.copy(isActive = newStatus) else a
                }
            )}

            when (val result = updateUserStatus(userId, newStatus)) {
                is AppResult.Success -> {
                    Timber.d("✅ Status updated successfully for $userId")
                    // No need to reload everything if optimistic update succeeded, 
                    // but we call it to ensure sync with server state
                    loadUsers() 
                }
                is AppResult.Error -> {
                    Timber.e("❌ Failed to update status: ${result.error.message}")
                    // Revert optimistic update
                    _uiState.update { it.copy(
                        agents = originalAgents,
                        searchResults = if (it.searchQuery.isEmpty()) originalAgents else it.searchResults.map { a ->
                            if (a.id == userId) a.copy(isActive = !newStatus) else a
                        },
                        error = result.error.message,
                        isLoading = false
                    )}
                    loadUsers() // Force sync
                }
            }
        }
    }
}
