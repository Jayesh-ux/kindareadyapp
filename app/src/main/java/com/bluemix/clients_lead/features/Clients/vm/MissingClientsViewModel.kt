package com.bluemix.clients_lead.features.Clients.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.repository.IClientRepository
import com.bluemix.clients_lead.domain.repository.MissingBreakdown
import com.bluemix.clients_lead.domain.repository.MissingClient
import com.bluemix.clients_lead.domain.repository.MissingLocationsResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

data class MissingClientsUiState(
    val isLoading: Boolean = false,
    val clients: List<MissingClient> = emptyList(),
    val totalMissing: Int = 0,
    val breakdown: MissingBreakdown? = null,
    val searchQuery: String = "",
    val error: String? = null
)

class MissingClientsViewModel(
    private val clientRepository: IClientRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MissingClientsUiState())
    val uiState: StateFlow<MissingClientsUiState> = _uiState.asStateFlow()

    private var allClients: List<MissingClient> = emptyList()

    init {
        loadMissingClients()
    }

    fun loadMissingClients() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            when (val result = clientRepository.getMissingLocations()) {
                is AppResult.Success -> {
                    val data = result.data
                    allClients = data.clients
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        clients = data.clients,
                        totalMissing = data.totalMissing,
                        breakdown = data.breakdown
                    )
                    Timber.d("✅ Loaded ${data.totalMissing} missing clients")
                }
                is AppResult.Error -> {
                    Timber.e("❌ Failed to load missing clients: ${result.error.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.error.message
                    )
                }
            }
        }
    }

    fun search(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        
        if (query.isEmpty()) {
            _uiState.value = _uiState.value.copy(clients = allClients)
        } else {
            val filtered = allClients.filter { client ->
                client.name.contains(query, ignoreCase = true) ||
                client.address?.contains(query, ignoreCase = true) == true ||
                client.phone?.contains(query) == true
            }
            _uiState.value = _uiState.value.copy(clients = filtered)
        }
    }

    fun refresh() {
        loadMissingClients()
    }
}