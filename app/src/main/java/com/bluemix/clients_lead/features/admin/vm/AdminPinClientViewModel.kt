package com.bluemix.clients_lead.features.admin.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluemix.clients_lead.domain.model.Client
import com.bluemix.clients_lead.domain.usecases.AdminPinClientLocation
import com.bluemix.clients_lead.domain.usecases.GetAllClients
import com.bluemix.clients_lead.core.common.utils.AppResult
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class AdminPinClientUiState(
    val isLoading: Boolean = false,
    val clientsWithoutLocation: List<Client> = emptyList(),
    val selectedClient: Client? = null,
    val showMapForPinning: Boolean = false,
    val currentMapCenter: LatLng = LatLng(19.0760, 72.8777),
    val isPinning: Boolean = false,
    val pinError: String? = null,
    val pinnedCount: Int = 0
)

class AdminPinClientViewModel(
    private val getAllClients: GetAllClients,
    private val adminPinClientLocation: AdminPinClientLocation
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminPinClientUiState())
    val uiState: StateFlow<AdminPinClientUiState> = _uiState.asStateFlow()

    fun loadClientsWithoutLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            when (val result = getAllClients(userId = "admin", page = 1, limit = 5000)) {
                is AppResult.Success -> {
                    val withoutLocation = result.data.filter { 
                        it.latitude == null || it.longitude == null || it.locationAccuracy == "approximate"
                    }
                    _uiState.update { it.copy(
                        isLoading = false,
                        clientsWithoutLocation = withoutLocation
                    )}
                }
                is AppResult.Error -> {
                    _uiState.update { it.copy(
                        isLoading = false,
                        pinError = result.error.message
                    )}
                }
            }
        }
    }

    fun clearPinnedCount() {
        _uiState.update { it.copy(pinnedCount = 0) }
    }

    fun selectClientForPinning(client: Client) {
        _uiState.update { it.copy(selectedClient = client, showMapForPinning = true) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedClient = null, showMapForPinning = false) }
    }

    fun updateMapCenter(latLng: LatLng) {
        _uiState.update { it.copy(currentMapCenter = latLng) }
    }

    fun pinClientLocation(clientId: String, clientName: String, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPinning = true) }

            when (val result = adminPinClientLocation(clientId, latitude, longitude)) {
                is AppResult.Success -> {
                    val currentClients = _uiState.value.clientsWithoutLocation
                    val updatedClients = currentClients.filter { it.id != clientId }
                    _uiState.update { it.copy(
                        isPinning = false,
                        selectedClient = null,
                        clientsWithoutLocation = updatedClients,
                        pinnedCount = it.pinnedCount + 1
                    ) }
                }
                is AppResult.Error -> {
                    _uiState.update { it.copy(isPinning = false, pinError = result.error.message) }
                }
            }
        }
    }
}