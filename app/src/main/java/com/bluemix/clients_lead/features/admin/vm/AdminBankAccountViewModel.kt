package com.bluemix.clients_lead.features.admin.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.repository.*
import com.bluemix.clients_lead.domain.usecases.GetTeamLocations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class AdminBankAccountUiState(
    val isLoadingUsers: Boolean = false,
    val isLoadingDetails: Boolean = false,
    val isSaving: Boolean = false,
    val users: List<AgentLocation> = emptyList(),
    val filteredUsers: List<AgentLocation> = emptyList(),
    val searchQuery: String = "",
    val selectedUser: AgentLocation? = null,
    val bankDetails: BankAccount = BankAccount(),
    val error: String? = null,
    val successMessage: String? = null
)

class AdminBankAccountViewModel(
    private val getTeamLocations: GetTeamLocations,
    private val paymentRepository: PaymentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminBankAccountUiState())
    val uiState: StateFlow<AdminBankAccountUiState> = _uiState.asStateFlow()

    init {
        fetchUsers()
    }

    fun fetchUsers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingUsers = true, error = null) }
            when (val result = getTeamLocations()) {
                is AppResult.Success -> {
                    _uiState.update { 
                        it.copy(
                            isLoadingUsers = false,
                            users = result.data,
                            filteredUsers = result.data.filter { user -> 
                                (user.fullName ?: "").contains(it.searchQuery, ignoreCase = true) ||
                                user.email.contains(it.searchQuery, ignoreCase = true)
                            }
                        )
                    }
                }
                is AppResult.Error -> {
                    _uiState.update { it.copy(isLoadingUsers = false, error = result.error.message) }
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredUsers = state.users.filter { user ->
                    (user.fullName ?: "").contains(query, ignoreCase = true) ||
                    user.email.contains(query, ignoreCase = true)
                }
            )
        }
    }

    fun selectUser(user: AgentLocation?) {
        _uiState.update { it.copy(selectedUser = user, successMessage = null, error = null) }
        if (user != null) {
            fetchUserBankDetails(user.id)
        }
    }

    private fun fetchUserBankDetails(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDetails = true, error = null) }
            when (val result = paymentRepository.getUserBankAccount(userId)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(isLoadingDetails = false, bankDetails = result.data ?: BankAccount()) }
                }
                is AppResult.Error -> {
                    _uiState.update { it.copy(isLoadingDetails = false, bankDetails = BankAccount()) }
                    Timber.e("Failed to fetch bank details: ${result.error.message}")
                }
            }
        }
    }

    fun onBankDetailsChanged(bankDetails: BankAccount) {
        _uiState.update { it.copy(bankDetails = bankDetails, error = null, successMessage = null) }
    }

    fun saveBankDetails() {
        val userId = uiState.value.selectedUser?.id ?: return
        val details = uiState.value.bankDetails

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, successMessage = null) }
            when (val result = paymentRepository.updateUserBankAccount(userId, details)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(isSaving = false, successMessage = "Bank details saved successfully!") }
                }
                is AppResult.Error -> {
                    _uiState.update { it.copy(isSaving = false, error = result.error.message) }
                }
            }
        }
    }
}
