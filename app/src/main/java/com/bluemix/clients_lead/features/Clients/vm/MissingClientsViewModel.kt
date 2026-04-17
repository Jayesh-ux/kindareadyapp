package com.bluemix.clients_lead.features.Clients.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.model.Client
import com.bluemix.clients_lead.domain.repository.IClientRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class MissingClientsUiState(
    val isLoading: Boolean = false,
    val clients: List<Client> = emptyList(),
    val totalMissing: Int = 0,
    val searchQuery: String = "",
    val error: String? = null
)

class MissingClientsViewModel(
    private val clientRepository: IClientRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MissingClientsUiState())
    val uiState: StateFlow<MissingClientsUiState> = _uiState.asStateFlow()

    private var allClients: List<Client> = emptyList()

    init {
        loadMissingClients()
    }

    fun loadMissingClients() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = clientRepository.getAllClients(userId = "admin", page = 1, limit = 5000)) {
                is AppResult.Success -> {
                    val clients = result.data
                    val missingClients = clients.filter { 
                        it.latitude == null || it.longitude == null || it.locationAccuracy == "needs_verification"
                    }
                    allClients = missingClients
                    Timber.d("📋 Total clients: ${clients.size}, Missing: ${missingClients.size}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            clients = missingClients,
                            totalMissing = missingClients.size
                        )
                    }
                    Timber.d("✅ Loaded ${missingClients.size} missing clients")
                }
                is AppResult.Error -> {
                    Timber.e("❌ Failed to load missing clients: ${result.error.message}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.error.message
                        )
                    }
                }
            }
        }
    }

    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        
        if (query.isEmpty()) {
            _uiState.update { it.copy(clients = allClients) }
        } else {
            val filtered = allClients.filter { client ->
                client.name.contains(query, ignoreCase = true) ||
                client.address?.contains(query, ignoreCase = true) == true ||
                client.phone?.contains(query) == true
            }
            _uiState.update { it.copy(clients = filtered) }
        }
    }

    fun refresh() {
        loadMissingClients()
    }
}