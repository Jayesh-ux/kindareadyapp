package com.bluemix.clients_lead.features.meeting.presentation

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemix.clients_lead.domain.model.Client
import com.bluemix.clients_lead.domain.model.Meeting
import com.bluemix.clients_lead.features.meeting.vm.AttachmentInfo
import com.bluemix.clients_lead.features.meeting.vm.ProximityVerificationState
import ui.AppTheme
import ui.components.Icon
import ui.components.IconButton
import ui.components.Text
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.bluemix.clients_lead.core.design.ui.components.textfield.OutlinedTextField
import com.bluemix.clients_lead.features.meeting.utils.ProximityDetector
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.android.gms.maps.model.LatLng
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MeetingBottomSheet(
    client: Client,
    activeMeeting: Meeting?,
    isLoading: Boolean,
    pendingAttachments: List<AttachmentInfo>,
    isUploadingAttachments: Boolean,
    proximityState: ProximityVerificationState = ProximityVerificationState.None,  // ✅ NEW
    error: String? = null, // ✅ NEW
    activeJourneyClientId: String? = null,
    onStartJourney: (String, String) -> Unit = { _, _ -> }, // ✅ REFINED: added mode
    onStopJourney: () -> Unit = {},
    currentLocation: LatLng? = null, // ✅ REFINED
    comments: String = "", // ✅ NEW: ViewModel backed
    onCommentsChange: (String) -> Unit = {}, // ✅ NEW
    onStartMeeting: () -> Unit,
    onEndMeeting: (comments: String, clientStatus: String) -> Unit,
    onAddAttachment: (Uri) -> Unit,
    onRemoveAttachment: (AttachmentInfo) -> Unit,
    onClearError: () -> Unit = {}, // ✅ NEW
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedClientStatus by remember { mutableStateOf(client.status) }
    var showEndConfirmation by remember { mutableStateOf(false) }
    var showDismissWarning by remember { mutableStateOf(false) }

    // ✅ STRICT: Camera-only capture for mandatory storefront photos
    val context = androidx.compose.ui.platform.LocalContext.current
    val fileProviderAuthority = "${context.packageName}.fileprovider"
    var currentImageUri by remember { mutableStateOf<Uri?>(null) }

    val cameraPermission = com.google.accompanist.permissions.rememberPermissionState(
        android.Manifest.permission.CAMERA
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentImageUri != null) {
            onAddAttachment(currentImageUri!!)
        }
    }

    val canDismiss = activeMeeting == null

    // ✅ REFINED: Proximity Check logic for UI feedback
    val distanceToClient = remember(currentLocation, client) {
        if (currentLocation != null && client.latitude != null && client.longitude != null) {
            ProximityDetector.calculateDistance(
                currentLocation,
                LatLng(client.latitude, client.longitude)
            )
        } else {
            null
        }
    }

    val isWithinProximity = distanceToClient != null && distanceToClient <= 50.0
    val isTrackingThisClient = activeJourneyClientId == client.id
    val isTrackingOtherClient = activeJourneyClientId != null && activeJourneyClientId != client.id

    Box(modifier = Modifier.fillMaxSize()) {
        // Dim backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    onClick = {
                        if (canDismiss) {
                            onDismiss()
                        } else {
                            showDismissWarning = true
                        }
                    },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                )
        )

        // Bottom sheet
        Box(
            modifier = modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(top = 80.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(AppTheme.colors.surface)
                .clickable(
                    onClick = {},
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // HEADER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (activeMeeting != null) "Meeting in Progress" else "Start Meeting",
                            style = AppTheme.typography.h3,
                            color = Color.White
                        )
                        Text(
                            text = client.name,
                            style = AppTheme.typography.body2,
                            color = Color(0xFFB0B0B0)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (canDismiss) {
                                onDismiss()
                            } else {
                                showDismissWarning = true
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = if (canDismiss) Color(0xFFB0B0B0) else Color(0xFF606060)
                        )
                    }
                }

                // ✅ ERROR BANNER (Strict Rejection Messages)
                AnimatedVisibility(
                    visible = error != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFF5252).copy(alpha = 0.15f))
                            .clickable { onClearError() }
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = error ?: "An error occurred",
                                style = AppTheme.typography.body2,
                                color = Color(0xFFFF5252),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // ✅ PROXIMITY WARNING (Compact)
                AnimatedVisibility(
                    visible = activeMeeting == null && !isWithinProximity && currentLocation != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppTheme.colors.error.copy(alpha = 0.1f))
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GpsFixed,
                                contentDescription = null,
                                tint = AppTheme.colors.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Out of Range: Must be within 50m (${String.format("%.0f", distanceToClient ?: 0.0)}m)",
                                style = AppTheme.typography.label2,
                                color = AppTheme.colors.error
                            )
                        }
                    }
                }

                // Warning message when meeting is in-progress
                AnimatedVisibility(
                    visible = activeMeeting != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF2962FF).copy(alpha = 0.15f))
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFF5E92F3),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Please end this meeting before closing",
                                style = AppTheme.typography.body2,
                                color = Color(0xFF5E92F3)
                            )
                        }
                    }
                }

                // ✅ PROXIMITY BADGE — shown after meeting starts
                AnimatedVisibility(
                    visible = activeMeeting != null && proximityState !is ProximityVerificationState.None,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    ProximityBadge(state = proximityState)
                }

                // ✅ REFINED: Journey Controls
                AnimatedVisibility(
                    visible = activeMeeting == null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    var selectedMode by remember { mutableStateOf("Car") }
                    
                    JourneyControlCard(
                        isTracking = isTrackingThisClient,
                        isTrackingOther = isTrackingOtherClient,
                        distance = distanceToClient,
                        selectedMode = selectedMode,
                        onModeChange = { selectedMode = it },
                        onStartJourney = { onStartJourney(client.id, selectedMode) },
                        onStopJourney = onStopJourney
                    )
                }

                // CLIENT INFO CARD
                ClientInfoCard(client)

                // MEETING DURATION
                if (activeMeeting != null) {
                    MeetingDurationCard(activeMeeting)
                }

                // CLIENT STATUS SELECTOR (only when ending meeting)
                AnimatedVisibility(
                    visible = activeMeeting != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Update Client Status",
                            style = AppTheme.typography.body1,
                            color = Color.White
                        )

                        Text(
                            text = "Current: ${client.status.capitalize()}",
                            style = AppTheme.typography.body2,
                            color = Color(0xFFB0B0B0)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ClientStatusChip(
                                label = "Active",
                                icon = Icons.Default.Person,
                                color = Color(0xFF5E92F3),
                                isSelected = selectedClientStatus == "active",
                                onClick = { selectedClientStatus = "active" },
                                modifier = Modifier.weight(1f)
                            )

                            ClientStatusChip(
                                label = "Completed",
                                icon = Icons.Default.CheckCircle,
                                color = Color(0xFF66BB6A),
                                isSelected = selectedClientStatus == "completed",
                                onClick = { selectedClientStatus = "completed" },
                                modifier = Modifier.weight(1f)
                            )

                            ClientStatusChip(
                                label = "Inactive",
                                icon = Icons.Default.PersonOff,
                                color = Color(0xFFEF5350),
                                isSelected = selectedClientStatus == "inactive",
                                onClick = { selectedClientStatus = "inactive" },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Status description
                        Text(
                            text = when (selectedClientStatus) {
                                "active" -> "Client is still in discussion"
                                "completed" -> "Client is interested / Deal done"
                                "inactive" -> "Client not interested"
                                else -> ""
                            },
                            style = AppTheme.typography.body3,
                            color = when (selectedClientStatus) {
                                "active" -> Color(0xFF5E92F3)
                                "completed" -> Color(0xFF66BB6A)
                                "inactive" -> Color(0xFFEF5350)
                                else -> Color(0xFF808080)
                            }
                        )
                    }
                }

                // COMMENTS (Only visible AFTER start, mandatory for end)
                AnimatedVisibility(
                    visible = activeMeeting != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Meeting Notes (Mandatory)",
                            style = AppTheme.typography.body1,
                            color = Color.White
                        )

                        OutlinedTextField(
                            value = comments,
                            onValueChange = onCommentsChange,
                            placeholder = { Text("Enter meeting notes or client requirements...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp),
                            maxLines = 6
                        )
                    }
                }

                // ✅ ATTACHMENTS SECTION (Only visible AFTER start, mandatory for end)
                AnimatedVisibility(
                    visible = activeMeeting != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Attachments (Mandatory - Client Photo)",
                                style = AppTheme.typography.body1,
                                color = Color.White
                            )

                            TextButton(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                        // ✅ Mandatory Camera Capture
                                        val photoFile = java.io.File(
                                            context.cacheDir,
                                            "storefront_${System.currentTimeMillis()}.jpg"
                                        )
                                        val uri = androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            fileProviderAuthority,
                                            photoFile
                                        )
                                        currentImageUri = uri
                                        cameraLauncher.launch(uri)
                                    } else {
                                        cameraPermission.launchPermissionRequest()
                                    }
                                },
                                enabled = !isUploadingAttachments
                            ) {
                                if (isUploadingAttachments) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF5E92F3)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = null,
                                    tint = Color(0xFF5E92F3)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Capture Photo", color = Color(0xFF5E92F3))
                            }
                        }

                        val hasExistingAttachments = activeMeeting?.attachments?.isNotEmpty() ?: false
                        if (pendingAttachments.isEmpty() && !hasExistingAttachments) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFFFCA28).copy(alpha = 0.1f))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "Please capture a photo of the client's storefront or sign to end the meeting.",
                                    style = AppTheme.typography.body3,
                                    color = Color(0xFFFFCA28)
                                )
                            }
                        } else if (pendingAttachments.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                pendingAttachments.forEach { attachment ->
                                    EnhancedAttachmentItem(
                                        attachment = attachment,
                                        onRemove = { onRemoveAttachment(attachment) }
                                    )
                                }
                            }
                        }
                    }
                }

                // ACTION BUTTON
                MeetingActionButton(
                    activeMeeting = activeMeeting,
                    isLoading = isLoading,
                    isEnabled = if (activeMeeting == null) {
                        isWithinProximity // ✅ Strictly enforced 50m rule
                    } else {
                        comments.isNotBlank() && (pendingAttachments.isNotEmpty() || activeMeeting.attachments.isNotEmpty()) 
                    },
                    onStartMeeting = onStartMeeting,
                    onEndRequest = { showEndConfirmation = true }
                )

                // INFO TEXT
                Text(
                    text = if (activeMeeting == null)
                        "Starting a meeting will log your visit time and location for this client."
                    else
                        "Update client status, add notes and attachments before ending the meeting.",
                    style = AppTheme.typography.body2,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF808080),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // End Meeting Confirmation Dialog
        if (showEndConfirmation) {
            EndMeetingDialog(
                comments = comments,
                clientStatus = selectedClientStatus,
                currentStatus = client.status,
                attachmentCount = pendingAttachments.size + (activeMeeting?.attachments?.size ?: 0),
                onCancel = { showEndConfirmation = false },
                onConfirm = {
                    showEndConfirmation = false
                    onEndMeeting(comments, selectedClientStatus)
                }
            )
        }

        // Dismiss Warning Dialog
        if (showDismissWarning) {
            DismissWarningDialog(
                clientName = client.name,
                onDismiss = { showDismissWarning = false }
            )
        }
    }
}

// ============ HELPER COMPOSABLES ============

@Composable
private fun ClientStatusChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) color.copy(alpha = 0.2f) else Color(0xFF0D0D0D))
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) color else Color(0xFF808080),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = AppTheme.typography.label2,
                color = if (isSelected) color else Color(0xFF808080)
            )
        }
    }
}

@Composable
private fun ClientInfoCard(client: Client) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0D0D0D))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            client.address?.let {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF5E92F3),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = it,
                        style = AppTheme.typography.body2,
                        color = Color(0xFFB0B0B0)
                    )
                }
            }

            client.phone?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = Color(0xFF5E92F3),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = it,
                        style = AppTheme.typography.body2,
                        color = Color(0xFFB0B0B0)
                    )
                }
            }
        }
    }
}

@Composable
private fun MeetingActionButton(
    activeMeeting: Meeting?,
    isLoading: Boolean,
    isEnabled: Boolean = true,
    onStartMeeting: () -> Unit,
    onEndRequest: () -> Unit
) {
    Button(
        onClick = if (activeMeeting == null) onStartMeeting else onEndRequest,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF5E92F3),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF404040),
            disabledContentColor = Color(0xFF808080)
        ),
        enabled = !isLoading && isEnabled
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (activeMeeting == null) "Starting..." else "Ending...",
                    style = AppTheme.typography.button,
                    color = Color.White
                )
            } else {
                androidx.compose.material3.Icon(
                    imageVector = if (activeMeeting == null) Icons.Default.Handshake else Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.White
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (activeMeeting == null) "Start Meeting" else "End Meeting",
                    style = AppTheme.typography.button,
                    color = Color.White
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun MeetingDurationCard(meeting: Meeting) {
    val initial = remember(meeting.startTime) {
        try {
            Duration.between(Instant.parse(meeting.startTime), Instant.now())
        } catch (e: Exception) {
            Duration.ZERO
        }
    }

    var duration by remember { mutableStateOf(initial) }

    LaunchedEffect(Unit) {
        while (true) {
            duration = try {
                Duration.between(Instant.parse(meeting.startTime), Instant.now())
            } catch (e: Exception) {
                Duration.ZERO
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2962FF).copy(alpha = 0.15f))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Meeting Duration",
                    style = AppTheme.typography.label2,
                    color = Color(0xFFB0B0B0)
                )
                Text(
                    text = formatDuration(duration),
                    style = AppTheme.typography.h2,
                    color = Color(0xFF5E92F3)
                )
            }

            androidx.compose.material3.Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = Color(0xFF5E92F3)
            )
        }
    }
}

/**
 * ✅ Enhanced attachment item showing file type icon and size
 */
@Composable
private fun EnhancedAttachmentItem(
    attachment: AttachmentInfo,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0D0D0D))
            .padding(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // File type icon
                androidx.compose.material3.Icon(
                    imageVector = when {
                        attachment.fileType.contains("image") -> Icons.Default.Image
                        attachment.fileType.contains("pdf") -> Icons.Default.PictureAsPdf
                        attachment.fileType.contains("text") -> Icons.Default.Description
                        attachment.fileType.contains("video") -> Icons.Default.VideoFile
                        attachment.fileType.contains("audio") -> Icons.Default.AudioFile
                        else -> Icons.Default.AttachFile
                    },
                    contentDescription = null,
                    tint = Color(0xFF5E92F3),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = attachment.fileName,
                        style = AppTheme.typography.body2,
                        color = Color.White,
                        maxLines = 1
                    )
                    Text(
                        text = "${String.format("%.2f", attachment.sizeMB)} MB",
                        style = AppTheme.typography.body3,
                        color = Color(0xFF808080)
                    )
                }
            }

            IconButton(onClick = onRemove) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun EndMeetingDialog(
    comments: String,
    clientStatus: String,
    currentStatus: String,
    attachmentCount: Int,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1A1A1A))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "End Meeting?",
                    style = AppTheme.typography.h3,
                    color = Color.White
                )

                if (clientStatus != currentStatus) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2A2A2A))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Client status will be updated:",
                                style = AppTheme.typography.body3,
                                color = Color(0xFFB0B0B0)
                            )
                            Text(
                                text = "${currentStatus.capitalize()} → ${clientStatus.capitalize()}",
                                style = AppTheme.typography.body1,
                                color = when (clientStatus) {
                                    "active" -> Color(0xFF5E92F3)
                                    "completed" -> Color(0xFF66BB6A)
                                    "inactive" -> Color(0xFFEF5350)
                                    else -> Color.White
                                }
                            )
                        }
                    }
                }

                // ✅ NEW: Summary Statistics
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Meeting Summary", style = AppTheme.typography.label2, color = Color.Gray)
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SummaryStatItem("Notes", if (comments.isBlank()) "None" else "${comments.length} chars", Icons.Default.Notes, Modifier.weight(1f))
                        SummaryStatItem("Files", "$attachmentCount", Icons.Default.AttachFile, Modifier.weight(1f))
                    }
                }

                if (comments.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Recorded Notes", style = AppTheme.typography.label2, color = Color.Gray)
                        Text(comments, style = AppTheme.typography.body2, color = Color.White, maxLines = 3)
                    }
                }

                Text(
                    text = buildString {
                        append("Your notes")
                        if (attachmentCount > 0) {
                            append(" and $attachmentCount attachment${if (attachmentCount > 1) "s" else ""}")
                        }
                        append(" will be saved.")
                    },
                    style = AppTheme.typography.body2,
                    color = Color(0xFFB0B0B0)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text("Cancel", color = Color.White)
                    }

                    androidx.compose.material3.Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF5E92F3),
                            contentColor = Color.White
                        )
                    ) {
                        Text("End", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun DismissWarningDialog(
    clientName: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1A1A1A))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "Meeting in Progress",
                        style = AppTheme.typography.h3,
                        color = Color.White
                    )
                }

                Text(
                    text = "You have an active meeting with $clientName. Please end the meeting before closing this screen.",
                    style = AppTheme.typography.body1,
                    color = Color(0xFFB0B0B0)
                )

                androidx.compose.material3.Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF5E92F3),
                        contentColor = Color.White
                    )
                ) {
                    Text("Got it", color = Color.White)
                }
            }
        }
    }
}

private fun formatDuration(d: Duration): String {
    val hours = d.toHours()
    val minutes = d.toMinutes() % 60
    val seconds = d.seconds % 60

    return if (hours > 0)
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    else
        String.format("%02d:%02d", minutes, seconds)
}

// ============ PROXIMITY BADGE ============

/**
 * Displays the PostGIS proximity verification result as a styled banner.
 *
 * | State          | Colour | Icon       | Label                          |
 * |----------------|--------|------------|--------------------------------|
 * | Verified       | Green  | CheckCircle| ✅ Verified Meeting · Xm away  |
 * | OutOfRange     | Yellow | Warning    | ⚠️ Not Verified · Xm away      |
 * | LocationTagged | Blue   | MyLocation | ℹ️ Client location saved        |
 */
@Composable
private fun ProximityBadge(state: ProximityVerificationState) {
    val (bgColor, iconVector, iconTint, label) = when (state) {
        is ProximityVerificationState.Verified -> Quad(
            Color(0xFF1B5E20).copy(alpha = 0.35f),
            Icons.Default.CheckCircle,
            Color(0xFF66BB6A),
            "✅  Verified Meeting · ${state.distanceMetres.toInt()} m away"
        )
        is ProximityVerificationState.OutOfRange -> Quad(
            Color(0xFFF57F17).copy(alpha = 0.25f),
            Icons.Default.Warning,
            Color(0xFFFFCA28),
            "⚠️  Not Verified · ${state.distanceMetres.toInt()} m away"
        )
        is ProximityVerificationState.LocationTagged -> Quad(
            Color(0xFF0D47A1).copy(alpha = 0.35f),
            Icons.Default.MyLocation,
            Color(0xFF5E92F3),
            "ℹ️  Client location saved for future visits"
        )
        else -> return  // ProximityVerificationState.None — render nothing
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            androidx.compose.material3.Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = AppTheme.typography.body2,
                color = Color.White
            )
        }
    }
}

/** Tiny utility to destructure 4-tuples from when-expressions. */
private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
@Composable
private fun JourneyControlCard(
    isTracking: Boolean,
    isTrackingOther: Boolean,
    distance: Double?,
    selectedMode: String,
    onModeChange: (String) -> Unit,
    onStartJourney: () -> Unit,
    onStopJourney: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isTracking) Color(0xFF1B5E20).copy(alpha = 0.15f) else Color(0xFF0D0D0D))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isTracking) "Journey in Progress" else "Travel to Client",
                        style = AppTheme.typography.label2,
                        color = Color(0xFFB0B0B0)
                    )
                    Text(
                        text = distance?.let { ProximityDetector.formatDistance(it) } ?: "Locating...",
                        style = AppTheme.typography.h3,
                        color = if (distance != null && distance <= 50.0) Color(0xFF66BB6A) else Color.White
                    )
                }
            }

            if (!isTracking && !isTrackingOther) {
                TransportModeSelector(selectedMode = selectedMode, onModeChange = onModeChange)
            }

            if (isTrackingOther) {
                Text(
                    text = "You are tracking another client. Stop that journey first.",
                    style = AppTheme.typography.body3,
                    color = Color(0xFFFFCA28)
                )
            } else {
                Button(
                    onClick = if (isTracking) onStopJourney else onStartJourney,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTracking) Color(0xFFEF5350).copy(alpha = 0.8f) else Color(0xFF263238),
                        contentColor = if (isTracking) Color.White else Color(0xFF5E92F3)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isTracking) "Stop Journey" else "Start Journey Tracking",
                        style = AppTheme.typography.button
                    )
                }
            }

            if (distance != null && distance <= 50.0) {
                Text(
                    text = "You are within 50m. Start Meeting is now enabled.",
                    style = AppTheme.typography.body3,
                    color = Color(0xFF66BB6A)
                )
            } else if (distance != null) {
                Text(
                    text = "Move within 50m to enable Start Meeting.",
                    style = AppTheme.typography.body3,
                    color = Color(0xFF808080)
                )
            }
        }
    }
}

@Composable
private fun SummaryStatItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0D0D0D))
            .padding(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = Color(0xFF5E92F3), modifier = Modifier.size(16.dp))
            Text(value, style = AppTheme.typography.h4, color = Color.White)
            Text(label, style = AppTheme.typography.label3, color = Color.Gray)
        }
    }
}

/** Utility to hold 4 related values for the transport selector. */


@Composable
private fun TransportModeSelector(
    selectedMode: String,
    onModeChange: (String) -> Unit
) {
    val transportOptions = listOf(
        Triple("Car",  "🚗", "Car"),
        Triple("Bike", "🏍️", "Bike"),
        Triple("Bus",  "🚌", "Bus"),
        Triple("Auto", "🛺", "Auto"),
        Triple("Taxi", "🚕", "Taxi")
    )
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Select Transport",
            style = AppTheme.typography.body2,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.padding(start = 4.dp)
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            transportOptions.forEach { (mode, emoji, label) ->
                val isSelected = selectedMode == mode
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) Color(0xFF1A3A6B)
                            else Color(0xFF1E293B)
                        )
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color(0xFF3B82F6)
                                    else Color(0xFF334155),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onModeChange(mode) }
                        .padding(vertical = 10.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = emoji, fontSize = 22.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        color = if (isSelected) Color(0xFF3B82F6)
                                else Color(0xFF9CA3AF),
                        fontWeight = if (isSelected) FontWeight.SemiBold
                                     else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
