package com.bluemix.clients_lead.features.admin.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.model.Meeting
import com.bluemix.clients_lead.domain.usecases.GetUserMeetings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MeetingLogsUiState(
    val isLoading: Boolean = false,
    val meetings: List<Meeting> = emptyList(),
    val error: String? = null
)

class MeetingLogsViewModel(
    private val getUserMeetings: GetUserMeetings
) : ViewModel() {

    private val _uiState = MutableStateFlow(MeetingLogsUiState())
    val uiState: StateFlow<MeetingLogsUiState> = _uiState.asStateFlow()

    init {
        loadMeetings()
    }

    fun loadMeetings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            // Using "all" to fetch meetings across all agents for the admin's company
            when (val result = getUserMeetings("all")) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, meetings = result.data) }
                }
                is AppResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.error.message) }
                }
            }
        }
    }
}
