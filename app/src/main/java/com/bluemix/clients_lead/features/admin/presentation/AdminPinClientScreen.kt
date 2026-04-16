package com.bluemix.clients_lead.features.admin.presentation

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemix.clients_lead.domain.model.Client
import com.bluemix.clients_lead.features.admin.vm.AdminPinClientViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import org.koin.androidx.compose.koinViewModel
import ui.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPinClientScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: AdminPinClientViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()

    var clientBeingPinned by remember { mutableStateOf<Client?>(null) }

    LaunchedEffect(Unit) {
        viewModel.clearPinnedCount()
        viewModel.loadClientsWithoutLocation()
    }

    if (clientBeingPinned != null) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A))) {
            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(
                    LatLng(19.0760, 72.8777), 14f
                )
            }

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    scrollGesturesEnabled = true,
                    zoomGesturesEnabled = true,
                    tiltGesturesEnabled = false
                )
            )

            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Pin location",
                tint = Color(0xFFEF4444),
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center)
                    .offset(y = (-24).dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .background(Color(0xFF1E293B))
                    .padding(16.dp)
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { clientBeingPinned = null }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Pin: ${clientBeingPinned!!.name}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color(0xFF1E293B))
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                val lat = cameraPositionState.position.target.latitude
                val lng = cameraPositionState.position.target.longitude

                Text(
                    text = "📍 %.4f, %.4f".format(lat, lng),
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Button(
                    onClick = {
                        val target = cameraPositionState.position.target
                        viewModel.pinClientLocation(
                            clientId = clientBeingPinned!!.id,
                            clientName = clientBeingPinned!!.name,
                            latitude = target.latitude,
                            longitude = target.longitude
                        )
                        Toast.makeText(context, "✅ ${clientBeingPinned!!.name} pinned", Toast.LENGTH_SHORT).show()
                        clientBeingPinned = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF16A34A),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Confirm This Location",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Pin Missing Clients", color = AppTheme.colors.text) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppTheme.colors.text)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AppTheme.colors.surface
                    )
                )
            },
            containerColor = AppTheme.colors.background
        ) { paddingValues ->
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AppTheme.colors.background)
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AppTheme.colors.primary)
                    }
                }
                uiState.clientsWithoutLocation.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AppTheme.colors.background)
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = AppTheme.colors.success,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "✅ All clients have locations",
                                style = MaterialTheme.typography.bodyLarge,
                                color = AppTheme.colors.success
                            )
                        }
                    }
                }
                else -> {
                    val totalClients = uiState.clientsWithoutLocation.size + uiState.pinnedCount

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AppTheme.colors.background)
                            .padding(paddingValues)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "$totalClients clients need manual pinning",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = AppTheme.colors.text
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (uiState.pinnedCount > 0) "${uiState.pinnedCount} pinned this session" else "Tap Pin to place each client on the map",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppTheme.colors.text.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                        items(uiState.clientsWithoutLocation) { client ->
                            ClientWithoutLocationCard(
                                client = client,
                                onPinClick = { clientBeingPinned = client }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientWithoutLocationCard(
    client: Client,
    onPinClick: () -> Unit
) {
    val isApproximate = client.locationAccuracy == "approximate"
    val hasNoLocation = client.latitude == null || client.longitude == null

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = client.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = AppTheme.colors.text
                    )
                    client.address?.let { address ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = address,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.textSecondary
                        )
                    }
                }
                Button(
                    onClick = onPinClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isApproximate) Color(0xFFF59E0B) else Color(0xFF2563EB),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.width(90.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isApproximate) "Fix" else "Pin",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            if (isApproximate) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFEF3C7), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚠️ Approximate location - needs manual pinning",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB45309)
                    )
                }
            } else if (hasNoLocation) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFEE2E2), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "❌ No location data",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB91C1C)
                    )
                }
            }
        }
    }
}