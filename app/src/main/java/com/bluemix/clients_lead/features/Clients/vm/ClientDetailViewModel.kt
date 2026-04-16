package com.bluemix.clients_lead.features.Clients.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.model.Client
import com.bluemix.clients_lead.domain.repository.IClientRepository
import com.bluemix.clients_lead.domain.usecases.GetClientById
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

data class ClientDetailUiState(
    val isLoading: Boolean = false,
    val client: Client? = null,
    val error: String? = null,
    val locationTagged: Boolean = false,
    val tagLocationError: String? = null
)

/**
 * ViewModel for client detail screen.
 * Receives clientId via loadClient() method, not constructor.
 */
class ClientDetailViewModel(
    private val getClientById: GetClientById,
    private val clientRepository: IClientRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClientDetailUiState())
    val uiState: StateFlow<ClientDetailUiState> = _uiState.asStateFlow()

    private var currentClientId: String? = null

    /**
     * Load client details by ID.
     * Call this from the UI when the screen is created.
     */
    fun loadClient(clientId: String) {
        Timber.d("📋 loadClient called with clientId: $clientId")
        if (currentClientId == clientId && _uiState.value.client != null) {
            Timber.d("📋 Client already loaded, skipping...")
            return
        }

        currentClientId = clientId
        Timber.d("📋 Calling fetchClient for: $clientId")
        fetchClient(clientId)
    }

    private fun fetchClient(clientId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            Timber.d("🔄 Fetching client details for ID: $clientId")

            when (val result = getClientById(clientId)) {
                is AppResult.Success -> {
                    Timber.d("✅ Client loaded successfully: ${result.data.name}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        client = result.data
                    )
                }
                is AppResult.Error -> {
                    Timber.e("❌ Failed to load client: ${result.error.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.error.message ?: "Failed to load client"
                    )
                }
            }
        }
    }

    fun refresh() {
        currentClientId?.let { fetchClient(it) }
    }

    /**
     * Tag client location with GPS coordinates
     */
    fun tagLocation(latitude: Double, longitude: Double, source: String = "AGENT") {
        val clientId = currentClientId ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(locationTagged = false, tagLocationError = null)
            
            when (val result = clientRepository.tagClientLocation(clientId, latitude, longitude, source)) {
                is AppResult.Success -> {
                    Timber.d("✅ Location tagged successfully: ${result.data.message}")
                    _uiState.value = _uiState.value.copy(
                        locationTagged = true,
                        tagLocationError = null
                    )
                    // Refresh client to get updated data
                    fetchClient(clientId)
                }
                is AppResult.Error -> {
                    Timber.e("❌ Failed to tag location: ${result.error.message}")
                    _uiState.value = _uiState.value.copy(
                        locationTagged = false,
                        tagLocationError = result.error.message ?: "Failed to tag location"
                    )
                }
            }
        }
    }
}