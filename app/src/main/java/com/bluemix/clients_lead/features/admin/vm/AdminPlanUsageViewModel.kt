package com.bluemix.clients_lead.features.admin.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.repository.PaymentRepository
import com.bluemix.clients_lead.domain.repository.PlanData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminPlanUsageUiState(
    val isLoading: Boolean = false,
    val planData: PlanData? = null,
    val error: String? = null
)

class AdminPlanUsageViewModel(
    private val paymentRepository: PaymentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminPlanUsageUiState())
    val uiState: StateFlow<AdminPlanUsageUiState> = _uiState.asStateFlow()

    init {
        fetchPlanData()
    }

    fun fetchPlanData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = paymentRepository.getMyPlan()) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, planData = result.data) }
                }
                is AppResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.error.message) }
                }
            }
        }
    }
}
