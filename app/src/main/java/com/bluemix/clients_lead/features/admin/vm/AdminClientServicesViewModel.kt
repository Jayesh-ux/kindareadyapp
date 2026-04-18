package com.bluemix.clients_lead.features.admin.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluemix.clients_lead.domain.model.ClientService
import com.bluemix.clients_lead.domain.usecases.GetClientServices
import com.bluemix.clients_lead.core.common.utils.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*

data class AdminClientServicesUiState(
    val isLoading: Boolean = false,
    val services: List<ClientService> = emptyList(),
    val filteredServices: List<ClientService> = emptyList(),
    val searchQuery: String = "",
    val totalRevenue: String = "₹0",
    val activeCount: Int = 0,
    val expiringCount: Int = 0,
    val expiredCount: Int = 0,
    val totalCount: Int = 0
)

class AdminClientServicesViewModel(
    private val getClientServices: GetClientServices
) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminClientServicesUiState())
    val uiState: StateFlow<AdminClientServicesUiState> = _uiState.asStateFlow()

    init {
        loadServices()
    }

    fun loadServices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            when (val result = getClientServices(null)) {
                is AppResult.Success -> {
                    val data = result.data
                    val revenue = data.sumOf { s -> s.price?.toDoubleOrNull() ?: 0.0 }
                    val revenueStr = when {
                        revenue >= 1_00_000 -> "₹${String.format("%.1f", revenue / 1_00_000)}M"
                        revenue >= 1_000 -> "₹${String.format("%.1f", revenue / 1_000)}K"
                        else -> "₹${revenue.toInt()}"
                    }
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            services = data,
                            filteredServices = if (it.searchQuery.isEmpty()) data else it.filteredServices,
                            totalCount = data.size,
                            activeCount = data.count { s -> s.status == "active" },
                            expiringCount = data.count { s -> s.daysLeft in 0..30 },
                            expiredCount = data.count { s -> s.daysLeft < 0 },
                            totalRevenue = revenueStr
                        )
                    }
                    if (_uiState.value.searchQuery.isNotEmpty()) {
                        onSearchQueryChanged(_uiState.value.searchQuery)
                    }
                }
                is AppResult.Error -> {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { currentState ->
            val filtered = currentState.services.filter {
                it.clientName.contains(query, ignoreCase = true) || 
                it.name.contains(query, ignoreCase = true)
            }
            currentState.copy(searchQuery = query, filteredServices = filtered)
        }
    }
}
