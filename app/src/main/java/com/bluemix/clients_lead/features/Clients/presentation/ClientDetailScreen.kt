package com.bluemix.clients_lead.features.Clients.presentation

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bluemix.clients_lead.domain.model.Client
import com.bluemix.clients_lead.domain.model.LocationStatus
import com.bluemix.clients_lead.features.Clients.vm.ClientDetailViewModel
import org.koin.androidx.compose.koinViewModel
import ui.AppTheme
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailScreen(
    clientId: String,
    onNavigateBack: () -> Unit,
    onNavigateToLandmarkSearch: (String, String) -> Unit = { _, _ -> },
    viewModel: ClientDetailViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var showLocationDialog by remember { mutableStateOf(false) }
    var pendingLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var dialogTitle by remember { mutableStateOf("") }
    var landmarkName by remember { mutableStateOf("") }
    
    // Location permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (fineLocationGranted || coarseLocationGranted) {
            // Get current location
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        pendingLocation = Pair(it.latitude, it.longitude)
                        dialogTitle = "Tag this client at your current location?"
                        landmarkName = "Current GPS (${String.format("%.6f", it.latitude)}, ${String.format("%.6f", it.longitude)})"
                        showLocationDialog = true
                    }
                }
            } catch (e: SecurityException) {
                // Handle exception
            }
        }
    }

    LaunchedEffect(clientId) {
        viewModel.loadClient(clientId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.client?.name ?: "Client") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(uiState.error ?: "Error")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.refresh() }) { Text("Retry") }
                    }
                }
            }
            uiState.client != null -> {
                ClientDetailContent(
                    client = uiState.client!!,
                    paddingValues = paddingValues,
                    context = context,
                    onNavigateToLandmarkSearch = onNavigateToLandmarkSearch,
                    onTagLocation = { lat, lng, source ->
                        viewModel.tagLocation(lat, lng, source)
                    },
                    onRequestLocation = {
                        val hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val hasCoarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        
                        if (hasFineLocation || hasCoarseLocation) {
                            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                            try {
                                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                    location?.let {
                                        pendingLocation = Pair(it.latitude, it.longitude)
                                        dialogTitle = "Tag this client at your current location?"
                                        landmarkName = "Current GPS (${String.format("%.6f", it.latitude)}, ${String.format("%.6f", it.longitude)})"
                                        showLocationDialog = true
                                    }
                                }
                            } catch (e: SecurityException) {
                                // Handle
                            }
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    }
                )
            }
        }
    }
    
    // Location confirmation dialog
    if (showLocationDialog && pendingLocation != null) {
        AlertDialog(
            onDismissRequest = { 
                showLocationDialog = false
                pendingLocation = null
            },
            title = { Text(dialogTitle) },
            text = { Text("$landmarkName\n\nDo you want to tag this client here?") },
            confirmButton = {
                Button(onClick = {
                    pendingLocation?.let { (lat, lng) ->
                        viewModel.tagLocation(lat, lng, "AGENT")
                    }
                    showLocationDialog = false
                    pendingLocation = null
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showLocationDialog = false
                    pendingLocation = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ClientDetailContent(
    client: Client,
    paddingValues: PaddingValues,
    context: android.content.Context,
    onNavigateToLandmarkSearch: (String, String) -> Unit,
    onTagLocation: (Double, Double, String) -> Unit = { _, _, _ -> },
    onRequestLocation: () -> Unit = {}
) {
    val needsTagging = client.needsLocationTagging()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3B82F6)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = client.name.take(2).uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(client.name, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { },
                        label = { Text(client.status.uppercase()) }
                    )
                    // Location status chip
                    val locationStatus = client.getLocationStatus()
                    val (chipColor, chipText) = when (locationStatus) {
                        LocationStatus.VERIFIED -> Pair(Color(0xFF4CAF50), "GPS Verified")
                        LocationStatus.NEEDS_VERIFICATION -> Pair(Color(0xFFFFA500), "Needs Verification")
                        LocationStatus.MISSING -> Pair(Color(0xFFF44336), "No GPS")
                    }
                    AssistChip(
                        onClick = { },
                        label = { Text(chipText) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = chipColor.copy(alpha = 0.2f))
                    )
                }
            }
        }

        // Contact Section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Contact", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                
                client.phone?.let { phone ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF3B82F6))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(phone)
                        }
                        IconButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                        }) {
                            Icon(Icons.Default.Call, contentDescription = "Call")
                        }
                    }
                }

                client.email?.let { email ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF3B82F6))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(email)
                        }
                        IconButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")))
                        }) {
                            Icon(Icons.Default.Send, contentDescription = "Email")
                        }
                    }
                }
            }
        }

        // Location Section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Location", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                
                // Show location source if available
                client.locationSource?.let { source ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (source) {
                                "AGENT" -> Icons.Default.Person
                                "ADMIN" -> Icons.Default.AdminPanelSettings
                                "LANDMARK" -> Icons.Default.Place
                                "GOOGLE" -> Icons.Default.Cloud
                                else -> Icons.Default.LocationOn
                            },
                            contentDescription = null,
                            tint = Color(0xFF3B82F6),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Source: $source • Accuracy: ${client.locationAccuracy ?: "unknown"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                client.address?.let { address ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF3B82F6))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(address)
                        }
                        if (client.hasLocation && client.latitude != null && client.longitude != null) {
                            IconButton(onClick = {
                                val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${client.latitude},${client.longitude}")
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            }) {
                                Icon(Icons.Default.Directions, contentDescription = "Navigate")
                            }
                        }
                    }
                }

                if (client.hasLocation && client.latitude != null && client.longitude != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Coordinates: ${String.format("%.6f", client.latitude)}, ${String.format("%.6f", client.longitude)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                // Tag Location button - show when needs tagging
                if (needsTagging) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "⚠️ This client needs GPS location",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFA500)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Tag Location button
                    Button(
                        onClick = onRequestLocation,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Tag My Location")
                    }
                }
            }
        }

        // Notes Section
        client.notes?.let { notes ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Notes", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(notes)
                }
            }
        }
    }
}