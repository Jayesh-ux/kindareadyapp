package com.bluemix.clients_lead.features.admin.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemix.clients_lead.domain.model.ClientService
import com.bluemix.clients_lead.features.admin.vm.AdminClientServicesViewModel
import org.koin.androidx.compose.koinViewModel
import ui.AppTheme
import ui.components.Scaffold
import ui.components.Text
import ui.components.topbar.TopBar
import ui.components.topbar.TopBarDefaults
import ui.components.textfield.TextField

@Composable
fun AdminClientServicesScreen(
    viewModel: AdminClientServicesViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToAddService: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddService,
                containerColor = Color(0xFF3B82F6),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Service")
            }
        },
        topBar = {
            TopBar(
                colors = TopBarDefaults.topBarColors(
                    containerColor = Color(0xFF0F172A),
                    scrolledContainerColor = Color(0xFF0F172A)
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppTheme.colors.onPrimary)
                    }
                    Text(
                        text = "Client Services",
                        style = AppTheme.typography.h2,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        containerColor = Color(0xFF020617)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Stats Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ServiceStatCard(
                            label = "TOTAL SERVICES",
                            value = uiState.totalCount.toString(),
                            icon = Icons.Default.Inventory2,
                            color = Color(0xFF818CF8),
                            modifier = Modifier.weight(1f)
                        )
                        ServiceStatCard(
                            label = "ACTIVE",
                            value = uiState.activeCount.toString(),
                            icon = Icons.Default.CheckCircle,
                            color = Color(0xFF10B981),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ServiceStatCard(
                            label = "EXPIRING SOON",
                            value = uiState.expiringCount.toString(),
                            icon = Icons.Default.Warning,
                            color = Color(0xFFF59E0B),
                            modifier = Modifier.weight(1f)
                        )
                        ServiceStatCard(
                            label = "EXPIRED",
                            value = uiState.expiredCount.toString(),
                            icon = Icons.Default.Cancel,
                            color = Color(0xFFEF4444),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    ServiceStatCard(
                        label = "TOTAL REVENUE",
                        value = uiState.totalRevenue,
                        icon = Icons.Default.Payments,
                        color = Color(0xFF3B82F6),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Search Bar
            item {
               TextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("Search services, clients, descriptions...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B)),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
                    }
                )
            }

            // Services Table Header (Simplified for Mobile)
            item {
                Text(
                    text = "Showing ${uiState.filteredServices.size} of ${uiState.totalCount} services",
                    style = AppTheme.typography.label3,
                    color = Color.Gray
                )
            }

            // Service List
            items(uiState.filteredServices) { service ->
                ServiceItemCard(service)
            }
            
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun ServiceStatCard(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(text = label, style = AppTheme.typography.label3, color = Color.Gray, fontSize = 10.sp)
                Text(text = value, style = AppTheme.typography.h3, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ServiceItemCard(service: ClientService) {
    val statusColor = when(service.status) {
        "active" -> Color(0xFF10B981)
        "expired" -> Color(0xFFEF4444)
        else -> Color(0xFFF59E0B)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = service.name, style = AppTheme.typography.body1, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(text = service.clientName, style = AppTheme.typography.body2, color = Color.Gray)
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(service.status.uppercase(), color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("START DATE", style = AppTheme.typography.label3, color = Color.Gray, fontSize = 9.sp)
                    Text(service.startDate, style = AppTheme.typography.body3, color = Color.White)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("EXPIRY", style = AppTheme.typography.label3, color = Color.Gray, fontSize = 9.sp)
                    Text(service.expiryDate, style = AppTheme.typography.body3, color = if (service.daysLeft < 30) Color(0xFFEF4444) else Color.White)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (service.daysLeft >= 0) "${service.daysLeft} days ago left" else "Expired",
                    style = AppTheme.typography.label3,
                    color = if (service.daysLeft < 0) Color(0xFFEF4444) else Color.Gray
                )
                
                Button(
                    onClick = { /* Manage */ },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Manage", fontSize = 11.sp, color = Color.White)
                }
            }
        }
    }
}
