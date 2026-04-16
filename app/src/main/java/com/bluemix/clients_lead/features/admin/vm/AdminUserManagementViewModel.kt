package com.bluemix.clients_lead.features.admin.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.repository.AgentLocation
import com.bluemix.clients_lead.domain.repository.VisibilityFilter
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
    val searchQuery: String = "",
    val visibilityFilter: VisibilityFilter = VisibilityFilter.ALL,
    val lastUpdated: Long = System.currentTimeMillis(),
    val loadingUsers: Set<String> = emptySet() // ✅ Track individual toggle loading states
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
                        applyFilters()
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
        applyFilters()
    }

    fun onVisibilityFilterChanged(filter: VisibilityFilter) {
        _uiState.update { it.copy(visibilityFilter = filter) }
        applyFilters()
    }

    private fun applyFilters() {
        val query = _uiState.value.searchQuery
        val visibility = _uiState.value.visibilityFilter
        val allAgents = _uiState.value.agents

        val filtered = allAgents.filter { agent ->
            // Search Match
            val matchesSearch = query.isEmpty() || 
                agent.fullName?.contains(query, ignoreCase = true) == true || 
                agent.email.contains(query, ignoreCase = true)

            // Visibility Match
            val matchesVisibility = when (visibility) {
                VisibilityFilter.ALL -> true
                VisibilityFilter.SEEN_TODAY -> com.bluemix.clients_lead.core.common.utils.DateTimeUtils.isToday(agent.timestamp)
                VisibilityFilter.UNSEEN_TODAY -> !com.bluemix.clients_lead.core.common.utils.DateTimeUtils.isToday(agent.timestamp)
            }

            matchesSearch && matchesVisibility
        }

        _uiState.update { it.copy(searchResults = filtered, lastUpdated = System.currentTimeMillis()) }
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
                loadingUsers = it.loadingUsers + userId, // ✅ Start loading for this user
                searchResults = if (it.searchQuery.isEmpty()) updatedAgents else it.searchResults.map { a ->
                    if (a.id == userId) a.copy(isActive = newStatus) else a
                }
            )}

            when (val result = updateUserStatus(userId, newStatus)) {
                is AppResult.Success -> {
                    Timber.d("✅ Status updated successfully for $userId")
                    _uiState.update { it.copy(loadingUsers = it.loadingUsers - userId) }
                    loadUsers() 
                }
                is AppResult.Error -> {
                    Timber.e("❌ Failed to update status: ${result.error.message}")
                    // Revert optimistic update
                    _uiState.update { it.copy(
                        agents = originalAgents,
                        loadingUsers = it.loadingUsers - userId,
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
