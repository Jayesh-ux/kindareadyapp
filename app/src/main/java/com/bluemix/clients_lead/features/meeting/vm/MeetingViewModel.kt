package com.bluemix.clients_lead.features.meeting.vm

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.model.Meeting
import com.bluemix.clients_lead.domain.model.ProximityResult
import com.bluemix.clients_lead.domain.usecases.*
import com.bluemix.clients_lead.features.location.LocationManager
import com.bluemix.clients_lead.features.location.BatteryUtils
import com.bluemix.clients_lead.features.location.LocationTrackingStateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.InputStream

data class AttachmentInfo(
    val id: String = "",
    val fileName: String,
    val fileType: String,
    val sizeMB: Double,
    val uri: Uri
)

/**
 * Reflects the backend PostGIS proximity check result for a meeting start.
 * Drives the badge shown to the user immediately after tapping "Start Meeting".
 */
sealed interface ProximityVerificationState {
    /** ✅ Agent is within 50m of client. Show green "Verified Meeting" badge. */
    data class Verified(val distanceMetres: Double) : ProximityVerificationState
    /** 🟡 Agent started meeting but is too far. Show yellow "Not Verified" badge. */
    data class OutOfRange(val distanceMetres: Double) : ProximityVerificationState
    /** ℹ️ First visit — client GPS was just tagged automatically. Show info badge. */
    object LocationTagged : ProximityVerificationState
    /** No proximity check was performed (no GPS sent or backend skipped). */
    object None : ProximityVerificationState
}

data class MeetingUiState(
    val isLoading: Boolean = false,
    val activeMeeting: Meeting? = null,
    val error: String? = null,
    val uploadProgress: Float = 0f,
    val isUploadingAttachments: Boolean = false,
    val pendingAttachments: List<AttachmentInfo> = emptyList(),
    val proximityState: ProximityVerificationState = ProximityVerificationState.None,
    val comments: String = "" // ✅ NEW: Persistent notes/comments
)

class MeetingViewModel(
    private val startMeeting: StartMeeting,
    private val endMeeting: EndMeeting,
    private val getActiveMeetingForClient: GetActiveMeetingForClient,
    private val uploadMeetingAttachment: UploadMeetingAttachment,
    private val getCurrentUserId: GetCurrentUserId,
    private val insertLocationLogUseCase: InsertLocationLog,
    private val trackingManager: com.bluemix.clients_lead.features.location.LocationTrackingManager,
    private val locationTrackingStateManager: LocationTrackingStateManager, // ✅ NEW: Check duty state
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MeetingUiState())
    val uiState: StateFlow<MeetingUiState> = _uiState.asStateFlow()

    private val locationManager = LocationManager(context)

    // ✅ NEW: Check if user is ON_DUTY before any meeting operation
    fun isOnDuty(): Boolean = locationTrackingStateManager.isOnDuty.value

    fun checkActiveMeeting(clientId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            when (val result = getActiveMeetingForClient(clientId)) {
                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        activeMeeting = result.data,
                        comments = result.data?.comments ?: _uiState.value.comments, // ✅ Populate but keep draft if exists
                        error = null
                    )
                }
                is AppResult.Error -> {
                    Timber.e(result.error.message, "Failed to check active meeting")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.error.message
                    )
                }
            }
        }
    }

    fun startMeeting(
        clientId: String,
        latitude: Double?,
        longitude: Double?,
        accuracy: Double?
    ) {
        // ✅ BLOCK: Cannot start meeting if OFF_DUTY
        if (!isOnDuty()) {
            Timber.w("BLOCKED: Cannot start meeting - user is OFF_DUTY")
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Please Clock In before starting a meeting"
            )
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            when (val result = startMeeting.invoke(clientId, latitude, longitude, accuracy)) {
                is AppResult.Success -> {
                    Timber.d("Meeting started successfully: ${result.data.id}")

                    // ✅ Derive the proximity badge state from the backend's proximity field
                    val proximityState = resolveProximityState(result.data.proximity)
                    Timber.d("Proximity state: $proximityState (reason: ${result.data.proximity?.reason})")

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        activeMeeting = result.data,
                        proximityState = proximityState,
                        error = null
                    )

                    // ✅ Log activity in LocationLog table
                    viewModelScope.launch {
                        val userId = getCurrentUserId() ?: return@launch
                        insertLocationLogUseCase(
                            userId = userId,
                            latitude = latitude ?: 0.0,
                            longitude = longitude ?: 0.0,
                            accuracy = accuracy,
                            battery = BatteryUtils.getBatteryPercentage(context),
                            clientId = clientId,
                            markActivity = "MEETING_START",
                            markNotes = "Meeting started with client: $clientId"
                        )
                        // ✅ Tell background service about the active client
                        trackingManager.updateActiveClient(clientId)
                    }
                }
                is AppResult.Error -> {
                    Timber.e(result.error.message, "Failed to start meeting")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.error.message ?: "Failed to start meeting"
                    )
                }
            }
        }
    }

    /**
     * Convert the backend ProximityResult to the UI's ProximityVerificationState.
     * Mirrors the reason-to-badge table in HANDOFF_Android_Kotlin.md.
     */
    private fun resolveProximityState(proximity: ProximityResult?): ProximityVerificationState {
        return when {
            proximity == null -> ProximityVerificationState.None
            proximity.verified && proximity.reason == "WithinRange" ->
                ProximityVerificationState.Verified(proximity.distanceMetres ?: 0.0)
            proximity.reason == "OutOfRange" ->
                ProximityVerificationState.OutOfRange(proximity.distanceMetres ?: 0.0)
            proximity.reason == "ClientLocationUnknown" ->
                ProximityVerificationState.LocationTagged
            else -> ProximityVerificationState.None
        }
    }

    fun addAttachment(uri: Uri) {
        viewModelScope.launch {
            try {
                val fileName = getFileName(uri) ?: "attachment_${System.currentTimeMillis()}"
                val fileType = getMimeType(uri) ?: "application/octet-stream"
                val fileSizeMB = getFileSize(uri)

                if (fileSizeMB > 10.0) {
                    _uiState.value = _uiState.value.copy(
                        error = "File too large. Maximum size is 10MB"
                    )
                    return@launch
                }

                val attachment = AttachmentInfo(
                    fileName = fileName,
                    fileType = fileType,
                    sizeMB = fileSizeMB,
                    uri = uri
                )

                _uiState.value = _uiState.value.copy(
                    pendingAttachments = _uiState.value.pendingAttachments + attachment
                )

                Timber.d("📎 Attachment added: $fileName (${String.format("%.2f", fileSizeMB)} MB)")

            } catch (e: Exception) {
                Timber.e(e, "Error adding attachment")
                _uiState.value = _uiState.value.copy(
                    error = "Failed to add attachment: ${e.message}"
                )
            }
        }
    }

    fun removeAttachment(attachment: AttachmentInfo) {
        _uiState.value = _uiState.value.copy(
            pendingAttachments = _uiState.value.pendingAttachments - attachment
        )
        Timber.d("📎 Attachment removed: ${attachment.fileName}")
    }

    fun endMeeting(
        comments: String?,
        clientStatus: String
    ) {
        viewModelScope.launch {
            val meeting = _uiState.value.activeMeeting
            if (meeting == null) {
                _uiState.value = _uiState.value.copy(
                    error = "No active meeting to end"
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Get current location for end location
            var endLatitude: Double? = null
            var endLongitude: Double? = null
            var endAccuracy: Double? = null

            try {
                val location = locationManager.getLastKnownLocation()
                if (location != null) {
                    endLatitude = location.latitude
                    endLongitude = location.longitude
                    endAccuracy = location.accuracy.toDouble()
                    Timber.d("📍 End location captured: $endLatitude, $endLongitude")
                } else {
                    Timber.w("⚠️ No location available when ending meeting")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get end location")
            }

            // Upload pending attachments
            val uploadedIds = mutableListOf<String>()
            val pendingAttachments = _uiState.value.pendingAttachments

            if (pendingAttachments.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(isUploadingAttachments = true)

                pendingAttachments.forEachIndexed { index, attachment ->
                    _uiState.value = _uiState.value.copy(
                        uploadProgress = (index.toFloat() / pendingAttachments.size)
                    )

                    Timber.d("📤 Uploading attachment ${index + 1}/${pendingAttachments.size}: ${attachment.fileName}")

                    val uploadResult = uploadSingleAttachment(meeting.id, attachment)
                    when (uploadResult) {
                        is AppResult.Success -> {
                            uploadedIds.add(uploadResult.data)
                            Timber.d("✅ Uploaded: ${attachment.fileName} (ID: ${uploadResult.data})")
                        }
                        is AppResult.Error -> {
                            Timber.e("❌ Upload failed for ${attachment.fileName}: ${uploadResult.error.message}")
                            // Continue with other uploads even if one fails
                        }
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isUploadingAttachments = false,
                    uploadProgress = 0f
                )
            }

            // End meeting with comments, uploaded attachment IDs, location, AND client status
            when (val result = endMeeting.invoke(
                meetingId = meeting.id,
                comments = comments,
                clientStatus = clientStatus,
                latitude = endLatitude,
                longitude = endLongitude,
                accuracy = endAccuracy
            )) {
                is AppResult.Success -> {
                    Timber.d("✅ Meeting ended successfully: ${result.data.id} | Client status: $clientStatus")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        activeMeeting = null,
                        pendingAttachments = emptyList(),
                        error = null
                    )

                    // ✅ Log activity in LocationLog table
                    viewModelScope.launch {
                        val userId = getCurrentUserId() ?: return@launch
                        insertLocationLogUseCase(
                            userId = userId,
                            latitude = endLatitude ?: 0.0,
                            longitude = endLongitude ?: 0.0,
                            accuracy = endAccuracy,
                            battery = BatteryUtils.getBatteryPercentage(context),
                            clientId = meeting.clientId,
                            markActivity = "MEETING_END",
                            markNotes = "Meeting ended with status: $clientStatus. Comments: $comments"
                        )
                        // ✅ Tell background service to stop tagging this client
                        trackingManager.updateActiveClient(null)
                    }
                }
                is AppResult.Error -> {
                    Timber.e(result.error.message, "Failed to end meeting")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.error.message ?: "Failed to end meeting"
                    )
                }
            }
        }
    }

    /**
     * ✅ IMPROVED: Better error logging and handling
     */
    private suspend fun uploadSingleAttachment(
        meetingId: String,
        attachment: AttachmentInfo
    ): AppResult<String> {
        return try {
            Timber.d("📤 Starting upload for: ${attachment.fileName}")
            Timber.d("   - Meeting ID: $meetingId")
            Timber.d("   - File size: ${attachment.sizeMB} MB")
            Timber.d("   - File type: ${attachment.fileType}")

            // Read file
            val inputStream: InputStream? = context.contentResolver.openInputStream(attachment.uri)
            if (inputStream == null) {
                Timber.e("❌ Cannot open input stream for: ${attachment.fileName}")
                return AppResult.Error(
                    com.bluemix.clients_lead.core.common.utils.AppError.Unknown(
                        message = "Failed to read file: ${attachment.fileName}"
                    )
                )
            }

            val fileBytes = inputStream.readBytes()
            inputStream.close()

            Timber.d("   - Read ${fileBytes.size} bytes from file")

            // Convert to base64
            val base64Data = android.util.Base64.encodeToString(
                fileBytes,
                android.util.Base64.NO_WRAP
            )

            Timber.d("   - Encoded to base64: ${base64Data.length} characters")

            // Upload via use case
            val result = uploadMeetingAttachment(
                meetingId,
                base64Data,
                attachment.fileName,
                attachment.fileType,
                attachment.sizeMB
            )

            when (result) {
                is AppResult.Success -> {
                    Timber.d("✅ Upload API call successful")
                    Timber.d("   - Attachment ID: ${result.data}")
                }
                is AppResult.Error -> {
                    Timber.e("❌ Upload API call failed")
                    Timber.e("   - Error message: ${result.error.message}")
                    Timber.e("   - Error type: ${result.error::class.simpleName}")
                }
            }

            result

        } catch (e: Exception) {
            Timber.e(e, "💥 Exception during attachment upload")
            Timber.e("   - File: ${attachment.fileName}")
            Timber.e("   - Exception type: ${e::class.simpleName}")
            Timber.e("   - Message: ${e.message}")

            AppResult.Error(
                com.bluemix.clients_lead.core.common.utils.AppError.Unknown(
                    message = "Upload failed: ${e.message}",
                    cause = e
                )
            )
        }
    }

    private fun getFileName(uri: Uri): String? {
        var fileName: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex)
            }
        }
        return fileName ?: uri.lastPathSegment
    }

    private fun getMimeType(uri: Uri): String? {
        return if (uri.scheme == "content") {
            context.contentResolver.getType(uri)
        } else {
            val fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.lowercase())
        }
    }

    private fun getFileSize(uri: Uri): Double {
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (sizeIndex != -1 && cursor.moveToFirst()) {
                size = cursor.getLong(sizeIndex)
            }
        }
        return size / (1024.0 * 1024.0) // Convert to MB
    }

    fun updateComments(newComments: String) {
        _uiState.value = _uiState.value.copy(comments = newComments)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun resetMeetingState() {
        _uiState.value = MeetingUiState()
    }
}