package com.bluemix.clients_lead.features.admin.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.model.Client
import com.bluemix.clients_lead.domain.repository.AgentLocation
import com.bluemix.clients_lead.domain.usecases.AddClientService
import com.bluemix.clients_lead.domain.usecases.GetClientsWithLocation
import com.bluemix.clients_lead.domain.usecases.GetTeamLocations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class AdminAddServiceUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    
    // Selection data
    val clients: List<Client> = emptyList(),
    val agents: List<AgentLocation> = emptyList(),
    
    // Form fields
    val name: String = "",
    val selectedClient: Client? = null,
    val selectedAgent: AgentLocation? = null,
    val center: String = "",
    val price: String = "",
    val startDate: String = "",
    val expiryDate: String = "",
    val status: String = "active"
)

class AdminAddServiceViewModel(
    private val getClientsWithLocation: GetClientsWithLocation,
    private val getTeamLocations: GetTeamLocations,
    private val addClientService: AddClientService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminAddServiceUiState())
    val uiState: StateFlow<AdminAddServiceUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // Fetch clients and agents for dropdowns
            val clientsResult = getClientsWithLocation("", isAdmin = true)
            val agentsResult = getTeamLocations()
            
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    clients = if (clientsResult is AppResult.Success) clientsResult.data.clients else emptyList(),
                    agents = if (agentsResult is AppResult.Success) agentsResult.data else emptyList()
                )
            }
        }
    }

    fun updateName(value: String) = _uiState.update { it.copy(name = value) }
    fun selectClient(value: Client?) = _uiState.update { it.copy(selectedClient = value) }
    fun selectAgent(value: AgentLocation?) = _uiState.update { it.copy(selectedAgent = value) }
    fun updateCenter(value: String) = _uiState.update { it.copy(center = value) }
    fun updatePrice(value: String) = _uiState.update { it.copy(price = value) }
    fun updateStartDate(value: String) = _uiState.update { it.copy(startDate = value) }
    fun updateExpiryDate(value: String) = _uiState.update { it.copy(expiryDate = value) }

    fun submitService() {
        if (_uiState.value.name.isBlank()) {
            _uiState.update { it.copy(error = "Service name is required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val state = _uiState.value
            
            val result = addClientService(
                name = state.name,
                clientId = state.selectedClient?.id,
                center = if (state.center.isNotBlank()) state.center else null,
                agentId = state.selectedAgent?.id,
                status = state.status,
                startDate = if (state.startDate.isNotBlank()) state.startDate else null,
                expiryDate = if (state.expiryDate.isNotBlank()) state.expiryDate else null,
                price = if (state.price.isNotBlank()) state.price else null
            )

            when (result) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                }
                is AppResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.error.message) }
                }
            }
        }
    }
}
