package com.bluemix.clients_lead.features.admin.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemix.clients_lead.domain.model.Client
import com.bluemix.clients_lead.domain.repository.AgentLocation
import com.bluemix.clients_lead.features.admin.vm.AdminAddServiceViewModel
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
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
                DatePickerField(
                    label = "Start Date",
                    value = uiState.startDate,
                    onValueChange = { viewModel.updateStartDate(it) },
                    modifier = Modifier.weight(1f)
                )
                DatePickerField(
                    label = "Expiry Date",
                    value = uiState.expiryDate,
                    onValueChange = { viewModel.updateExpiryDate(it) },
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
        ClientSelectorDialog(
            title = "Select Client",
            items = uiState.clients,
            selectedItem = uiState.selectedClient,
            onSelectItem = { viewModel.selectClient(it); showClientDialog.value = false },
            onDismiss = { showClientDialog.value = false },
            isLoading = uiState.isLoading
        )
    }

    if (showAgentDialog.value) {
        AgentSelectorDialog(
            title = "Select Agent",
            items = uiState.agents,
            selectedItem = uiState.selectedAgent,
            onSelectItem = { viewModel.selectAgent(it); showAgentDialog.value = false },
            onDismiss = { showAgentDialog.value = false }
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
    selectedItem: T?,
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
        containerColor = Color(0xFF1E293B),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search...", color = Color(0xFF64748B)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF334155)),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF3B82F6)) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF334155),
                        unfocusedContainerColor = Color(0xFF334155),
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        cursorColor = Color(0xFF3B82F6)
                    ),
                    singleLine = true
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (filteredItems.isEmpty() && searchQuery.isNotBlank()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No results matching \"$searchQuery\"", color = Color(0xFF94A3B8))
                    }
                } else if (items.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No data available", color = Color(0xFF94A3B8))
                    }
                }

                // Option for None (Only show if search is empty or matches)
                if (searchQuery.isBlank()) {
                    ListItem(
                        headlineContent = { 
                            Text(
                                "None / Unassigned", 
                                color = if (selectedItem == null) Color.White else Color(0xFF888888)
                            ) 
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedItem == null) Color(0xFF3B82F6) else Color.Transparent)
                            .clickable { onSelectItem(null) },
                        leadingContent = if (selectedItem == null) {
                            { Icon(Icons.Default.Check, contentDescription = null, tint = Color.White) }
                        } else null
                    )
                }
                
                filteredItems.forEach { item ->
                    val isSelected = selectedItem == item
                    ListItem(
                        headlineContent = { 
                            Text(
                                itemLabel(item), 
                                color = if (isSelected) Color.White else Color(0xFF1E293B),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ) 
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF3B82F6) else Color(0xFFF1F5F9))
                            .clickable { onSelectItem(item) }
                            .padding(vertical = 4.dp),
                        leadingContent = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, tint = Color.White) }
                        } else null
                    )
                }
            }
        },
        confirmButton = { 
            TextButton(onClick = onDismiss) { 
                Text("Cancel", color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold) 
            } 
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val datePickerState = rememberDatePickerState()
    var showDatePicker by remember { mutableStateOf(false) }
    
    Column(modifier = modifier) {
        Text(label, style = AppTheme.typography.label3, color = Color.Gray)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E293B))
                .clickable { showDatePicker = true }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = value.ifEmpty { "Select date" },
                    color = if (value.isEmpty()) Color(0xFF64748B) else Color.White,
                    style = AppTheme.typography.body2
                )
                Icon(Icons.Default.CalendarMonth, null, tint = Color(0xFF64748B))
            }
        }
    }
    
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        onValueChange(sdf.format(java.util.Date(millis)))
                    }
                    showDatePicker = false
                }) {
                    Text("OK", color = Color(0xFF3B82F6))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                modifier = Modifier.background(Color(0xFF0F172A)),
                showModeToggle = false
            )
        }
    }
}

@Composable
private fun ClientSelectorDialog(
    title: String,
    items: List<Client>,
    selectedItem: Client?,
    onSelectItem: (Client?) -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean = false
) {
    var searchQuery by remember { mutableStateOf("") }
    val displayClients = remember(items, searchQuery) {
        val valid = items.filter { !it.name.isNullOrBlank() }
        if (searchQuery.isBlank()) valid
        else valid.filter { it.name!!.contains(searchQuery, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search...", color = Color(0xFF64748B)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF334155)),
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF3B82F6)) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF334155),
                        unfocusedContainerColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF3B82F6)
                    ),
                    singleLine = true
                )
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
            ) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFF3B82F6))
                                Spacer(Modifier.height(12.dp))
                                Text("Loading clients...", color = Color(0xFF94A3B8))
                            }
                        }
                    }
                    displayClients.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No clients found", color = Color(0xFF94A3B8))
                        }
                    }
                    else -> {
                        // Show total count only when filtered
                        if (searchQuery.isNotBlank() && displayClients.size != items.size) {
                            Text(
                                text = "Showing ${displayClients.size} of ${items.size} clients",
                                style = AppTheme.typography.label3,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                count = displayClients.size,
                                key = { displayClients.get(it).id }
                            ) { index ->
                                val client = displayClients[index]
                                val isSelected = selectedItem?.id == client.id
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { onSelectItem(client) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2C3A))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF3B82F6)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = (client.name ?: "?").take(1).uppercase(),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = client.name ?: "Unknown",
                                                color = Color.White,
                                                fontWeight = FontWeight.Medium,
                                                style = AppTheme.typography.body2
                                            )
                                            if (!client.address.isNullOrBlank()) {
                                                Text(
                                                    text = client.address,
                                                    color = Color(0xFF94A3B8),
                                                    style = AppTheme.typography.body3
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF64748B))
            }
        }
    )
}

@Composable
private fun AgentSelectorDialog(
    title: String,
    items: List<AgentLocation>,
    selectedItem: AgentLocation?,
    onSelectItem: (AgentLocation?) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredItems = remember(searchQuery, items) {
        if (searchQuery.isBlank()) items
        else items.filter { 
            (it.fullName ?: it.email ?: "").contains(searchQuery, ignoreCase = true) 
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search...", color = Color(0xFF64748B)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF334155)),
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF3B82F6)) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF334155),
                        unfocusedContainerColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF3B82F6)
                    ),
                    singleLine = true
                )
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
            ) {
                if (filteredItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text("No agents found", color = Color(0xFF94A3B8))
                    }
                } else {
                    if (searchQuery.isNotBlank() && filteredItems.size != items.size) {
                        Text(
                            text = "Showing ${filteredItems.size} of ${items.size} agents",
                            style = AppTheme.typography.label3,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            count = filteredItems.size
                        ) { index ->
                        val agent = filteredItems[index]
                        val isSelected = selectedItem?.id == agent.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onSelectItem(agent) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2C3A))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF3B82F6)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (agent.fullName ?: agent.email ?: "?").take(1).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = agent.fullName ?: agent.email ?: "Unknown",
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        style = AppTheme.typography.body2
                                    )
                                    if (!agent.email.isNullOrBlank()) {
                                        Text(
                                            text = agent.email,
                                            color = Color(0xFF94A3B8),
                                            style = AppTheme.typography.body3
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF64748B))
            }
        }
    )
}
