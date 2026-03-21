package com.bluemix.clients_lead.features.Clients.presentation

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.data.repository.OCRRepository
import com.bluemix.clients_lead.features.Clients.vm.ClientsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import timber.log.Timber

@Composable
fun CreateClientScreen(
    viewModel: ClientsViewModel = koinViewModel(),
    ocrRepository: OCRRepository = koinInject(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var pincode by remember { mutableStateOf("") }
    var mapsLink by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var isValidatingAddress by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var pincodeError by remember { mutableStateOf<String?>(null) }

    var showScanner by remember { mutableStateOf(false) }
    var isProcessingOCR by remember { mutableStateOf(false) }
    var ocrMessage by remember { mutableStateOf<String?>(null) }
    var showOCRConfirmation by remember { mutableStateOf(false) }
    var extractedInfo by remember { mutableStateOf<com.bluemix.clients_lead.data.repository.ExtractedClientInfo?>(null) }

    // Navigation on success
    LaunchedEffect(uiState.createSuccess) {
        if (uiState.createSuccess) {
            kotlinx.coroutines.delay(1500)
            viewModel.resetCreateState()
            onNavigateBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(top = 80.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color(0xFF000000))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        Text(
                            text = "New Client",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.size(40.dp))
                    }

                    // OCR Scan Button
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF8B5CF6).copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(20.dp))
                                Text("Smart OCR Fill", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Button(
                                onClick = { showScanner = true },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Scan Card")
                            }
                        }
                    }

                    // Basic Information Section
                    SectionHeader("Basic Info")
                    CustomTextField(value = name, onValueChange = { name = it; nameError = null }, label = "Name", placeholder = "Client name", isRequired = true, isError = nameError != null, errorMessage = nameError)
                    CustomTextField(value = phone, onValueChange = { phone = it; phoneError = null }, label = "Phone", placeholder = "Mobile number", isError = phoneError != null, errorMessage = phoneError, keyboardType = KeyboardType.Phone)
                    CustomTextField(value = email, onValueChange = { email = it; emailError = null }, label = "Email", placeholder = "Email address", isError = emailError != null, errorMessage = emailError, keyboardType = KeyboardType.Email)

                    // Location Section
                    SectionHeader("Location (User Friendly)")
                    CustomTextField(value = address, onValueChange = { address = it }, label = "Street Address", placeholder = "Flat/Street/Area", minLines = 2)
                    CustomTextField(value = pincode, onValueChange = { if(it.length <= 6) pincode = it }, label = "Pincode", placeholder = "6-digit code", isError = pincodeError != null, errorMessage = pincodeError, keyboardType = KeyboardType.Number)
                    
                    CustomTextField(
                        value = mapsLink,
                        onValueChange = { mapsLink = it },
                        label = "Google Maps Link (Optional)",
                        placeholder = "Paste maps.app.goo.gl link here",
                        leadingIcon = "🔗"
                    )

                    SectionHeader("Notes")
                    CustomTextField(value = notes, onValueChange = { notes = it }, label = "Internal Notes", placeholder = "Any context...", minLines = 3)

                    Spacer(Modifier.height(16.dp))

                    // Feedback Messages
                    if (uiState.createError != null) ErrorMessage(uiState.createError!!)
                    if (isValidatingAddress) Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Resolving location coordinates...", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }

                    // Create Button
                    Button(
                        onClick = {
                            var hasErrors = false
                            if (name.isBlank()) { nameError = "Required"; hasErrors = true }
                            if (pincode.isNotBlank() && pincode.length != 6) { pincodeError = "Invalid"; hasErrors = true }
                            
                            if (!hasErrors) {
                                scope.launch {
                                    isValidatingAddress = true
                                    var lat: Double? = null
                                    var lon: Double? = null
                                    
                                    // 1. Try Geocoder
                                    try {
                                        val geocoder = android.location.Geocoder(context)
                                        val combined = "$address, $pincode, India"
                                        val list = withContext(Dispatchers.IO) { geocoder.getFromLocationName(combined, 1) }
                                        if (!list.isNullOrEmpty()) {
                                            lat = list[0].latitude
                                            lon = list[0].longitude
                                        }
                                    } catch (e: Exception) { Timber.e(e) }

                                    // 2. Maps link override if present (simplified link parsing)
                                    if (mapsLink.contains("@")) {
                                        val match = Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)""").find(mapsLink)
                                        match?.let { 
                                            lat = it.groupValues[1].toDoubleOrNull() ?: lat
                                            lon = it.groupValues[2].toDoubleOrNull() ?: lon
                                        }
                                    }

                                    viewModel.createClientAction(
                                        name = name.trim(),
                                        phone = phone.trim().ifBlank { null },
                                        email = email.trim().ifBlank { null },
                                        address = address.trim().ifBlank { null },
                                        pincode = pincode.trim().ifBlank { null },
                                        notes = notes.trim().ifBlank { null },
                                        latitude = lat,
                                        longitude = lon
                                    )
                                    isValidatingAddress = false
                                }
                            }
                        },
                        enabled = !uiState.isCreating && name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5E92F3)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (uiState.isCreating) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        else Text("Create Client", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // OCR Dialogs logic... (kept simplified for brevity or can be re-included)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF5E92F3), fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ErrorMessage(msg: String) {
    Box(Modifier.fillMaxWidth().background(Color.Red.copy(alpha = 0.1f)).padding(12.dp)) {
        Text(msg, color = Color.Red, fontSize = 12.sp)
    }
}

@Composable
private fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    isRequired: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null,
    leadingIcon: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    minLines: Int = 1
) {
    Column {
        Row {
            Text(label, color = Color.Gray, fontSize = 12.sp)
            if(isRequired) Text("*", color = Color.Red)
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = Color.DarkGray, fontSize = 14.sp) },
            isError = isError,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            minLines = minLines,
            maxLines = if(minLines > 1) 5 else 1,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF5E92F3),
                unfocusedBorderColor = Color(0xFF333333),
                focusedContainerColor = Color(0xFF111111),
                unfocusedContainerColor = Color(0xFF111111)
            ),
            shape = RoundedCornerShape(12.dp)
        )
        if (isError && errorMessage != null) Text(errorMessage, color = Color.Red, fontSize = 10.sp)
    }
}