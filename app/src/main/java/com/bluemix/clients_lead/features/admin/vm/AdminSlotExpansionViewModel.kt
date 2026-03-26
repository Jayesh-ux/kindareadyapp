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

data class AdminSlotExpansionUiState(
    val isLoading: Boolean = false,
    val isPurchasing: Boolean = false,
    val planData: PlanData? = null,
    val userSlotsToPurchase: Int = 0,
    val clientSlotsToPurchase: Int = 0,
    val error: String? = null,
    val successMessage: String? = null
)

class AdminSlotExpansionViewModel(
    private val paymentRepository: PaymentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminSlotExpansionUiState())
    val uiState: StateFlow<AdminSlotExpansionUiState> = _uiState.asStateFlow()

    // Pricing (mirrored from React)
    val PRICE_PER_USER_SLOT = 50
    val PRICE_PER_CLIENT_SLOT = 10

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

    fun updateUserSlots(count: Int) {
        _uiState.update { it.copy(userSlotsToPurchase = count.coerceAtLeast(0)) }
    }

    fun updateClientSlots(count: Int) {
        _uiState.update { it.copy(clientSlotsToPurchase = count.coerceAtLeast(0)) }
    }

    fun purchaseSlots() {
        val state = uiState.value
        if (state.userSlotsToPurchase == 0 && state.clientSlotsToPurchase == 0) return

        val totalAmount = (state.userSlotsToPurchase * PRICE_PER_USER_SLOT) + 
                         (state.clientSlotsToPurchase * PRICE_PER_CLIENT_SLOT)

        viewModelScope.launch {
            _uiState.update { it.copy(isPurchasing = true, error = null, successMessage = null) }
            val result = paymentRepository.purchaseSlots(
                state.userSlotsToPurchase,
                state.clientSlotsToPurchase,
                totalAmount
            )
            when (result) {
                is AppResult.Success -> {
                    _uiState.update { 
                        it.copy(
                            isPurchasing = false, 
                            successMessage = "Successfully purchased slots!",
                            userSlotsToPurchase = 0,
                            clientSlotsToPurchase = 0
                        )
                    }
                    fetchPlanData()
                }
                is AppResult.Error -> {
                    _uiState.update { it.copy(isPurchasing = false, error = result.error.message) }
                }
            }
        }
    }
}
