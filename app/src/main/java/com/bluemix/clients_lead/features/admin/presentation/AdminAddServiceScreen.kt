package com.bluemix.clients_lead.features.admin.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemix.clients_lead.features.admin.vm.AdminAddServiceViewModel
import org.koin.androidx.compose.koinViewModel
import ui.AppTheme
import ui.components.Scaffold
import ui.components.Text
import ui.components.topbar.TopBar
import ui.components.topbar.TopBarDefaults
import ui.components.textfield.TextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAddServiceScreen(
    viewModel: AdminAddServiceViewModel = koinViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val showClientDialog = remember { mutableStateOf(false) }
    val showAgentDialog = remember { mutableStateOf(false) }
    
    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onNavigateBack()
        }
    }

    Scaffold(
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
                        text = "Add Client Service",
                        style = AppTheme.typography.h2,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        containerColor = Color(0xFF020617)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            InfoSection("Service Details")
            
            TextField(
                value = uiState.name,
                onValueChange = { viewModel.updateName(it) },
                placeholder = { Text("Service Name (e.g. Annual Maintenance)") },
                modifier = Modifier.fillMaxWidth()
            )

            TextField(
                value = uiState.price,
                onValueChange = { viewModel.updatePrice(it) },
                placeholder = { Text("Price (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, tint = Color.Gray) }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = uiState.startDate,
                    onValueChange = { viewModel.updateStartDate(it) },
                    placeholder = { Text("Start (Optional)") },
                    modifier = Modifier.weight(1f)
                )
                TextField(
                    value = uiState.expiryDate,
                    onValueChange = { viewModel.updateExpiryDate(it) },
                    placeholder = { Text("Expiry (Optional)") },
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            InfoSection("Assignments (All Optional)")

            SelectableField(
                label = "Client",
                value = uiState.selectedClient?.name ?: "Select Client",
                icon = Icons.Default.Store,
                onClick = { showClientDialog.value = true }
            )

            // Agent Selector
            SelectableField(
                label = "Field Agent",
                value = uiState.selectedAgent?.fullName ?: uiState.selectedAgent?.email ?: "Select Agent",
                icon = Icons.Default.Person,
                onClick = { showAgentDialog.value = true }
            )

            TextField(
                value = uiState.center,
                onValueChange = { viewModel.updateCenter(it) },
                placeholder = { Text("Business Center / Branch (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, tint = Color.Gray) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.submitService() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3B82F6),
                    disabledContainerColor = Color(0xFF3B82F6).copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = !uiState.isLoading && uiState.selectedClient != null && uiState.selectedAgent != null
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text("Create Service", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            if (uiState.error != null) {
                Text(uiState.error!!, color = Color.Red, style = AppTheme.typography.body3)
            }
        }
    }

    // Dialogs
    if (showClientDialog.value) {
        SelectorDialog(
            title = "Select Client",
            items = uiState.clients,
            onSelectItem = { viewModel.selectClient(it); showClientDialog.value = false },
            onDismiss = { showClientDialog.value = false },
            itemLabel = { it.name }
        )
    }

    if (showAgentDialog.value) {
        SelectorDialog(
            title = "Select Agent",
            items = uiState.agents,
            onSelectItem = { viewModel.selectAgent(it); showAgentDialog.value = false },
            onDismiss = { showAgentDialog.value = false },
            itemLabel = { it.fullName ?: it.email }
        )
    }
}

@Composable
private fun InfoSection(text: String) {
    Text(
        text = text,
        style = AppTheme.typography.label2,
        color = Color(0xFF3B82F6),
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun SelectableField(label: String, value: String, icon: ImageVector, onClick: () -> Unit) {
    Column {
        Text(label, style = AppTheme.typography.label3, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E293B))
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
            Text(value, color = Color.White, style = AppTheme.typography.body2)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SelectorDialog(
    title: String,
    items: List<T>,
    onSelectItem: (T?) -> Unit,
    onDismiss: () -> Unit,
    itemLabel: (T) -> String
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredItems = remember(searchQuery, items) {
        if (searchQuery.isBlank()) items
        else items.filter { itemLabel(it).contains(searchQuery, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface)
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    singleLine = true
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState())
            ) {
                if (filteredItems.isEmpty() && searchQuery.isNotBlank()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No results matching \"$searchQuery\"", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                } else if (items.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No data available", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }

                // Option for None (Only show if search is empty or matches)
                if (searchQuery.isBlank()) {
                    ListItem(
                        headlineContent = { Text("None / Unassigned", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) },
                        modifier = Modifier.clickable { onSelectItem(null) }
                    )
                }
                
                filteredItems.forEach { item ->
                    ListItem(
                        headlineContent = { Text(itemLabel(item), color = MaterialTheme.colorScheme.onSurface) },
                        modifier = Modifier.clickable { onSelectItem(item) }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.primary) } }
    )
}
