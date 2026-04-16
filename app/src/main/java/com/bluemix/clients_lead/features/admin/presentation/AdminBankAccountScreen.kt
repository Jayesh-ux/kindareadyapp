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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemix.clients_lead.domain.repository.AgentLocation
import com.bluemix.clients_lead.domain.repository.BankAccount
import com.bluemix.clients_lead.features.admin.vm.AdminBankAccountViewModel
import org.koin.androidx.compose.koinViewModel
import ui.AppTheme
import ui.components.Scaffold
import ui.components.Text
import ui.components.topbar.TopBar
import ui.components.topbar.TopBarDefaults
import ui.components.textfield.TextField

@Composable
fun AdminBankAccountScreen(
    viewModel: AdminBankAccountViewModel = koinViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

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
                    IconButton(onClick = {
                        if (uiState.selectedUser != null) {
                            viewModel.selectUser(null)
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppTheme.colors.onPrimary)
                    }
                    Text(
                        text = if (uiState.selectedUser != null) "Bank Details" else "Manage Bank Accounts",
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
        ) {
            if (uiState.selectedUser == null) {
                UserSelectionContent(uiState, viewModel)
            } else {
                BankDetailsFormContent(uiState, viewModel)
            }
        }
    }
}

@Composable
private fun UserSelectionContent(
    uiState: com.bluemix.clients_lead.features.admin.vm.AdminBankAccountUiState,
    viewModel: AdminBankAccountViewModel
) {
    // Search Bar
    Box(modifier = Modifier.padding(16.dp)) {
        TextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            placeholder = { Text("Search by name or email...") },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E293B)),
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
            }
        )
    }

    if (uiState.isLoadingUsers) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF3B82F6))
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.filteredUsers) { user ->
                UserItem(user = user, onClick = { viewModel.selectUser(user) })
            }
        }
    }
}

@Composable
private fun UserItem(user: AgentLocation, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F172A))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF3B82F6).copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (user.fullName ?: user.email).take(1).uppercase(),
                style = AppTheme.typography.h3,
                color = Color(0xFF3B82F6),
                fontWeight = FontWeight.Bold
            )
        }

        Column {
            Text(user.fullName ?: "Unnamed User", style = AppTheme.typography.body1, color = Color.White)
            Text(user.email, style = AppTheme.typography.body3, color = Color.Gray)
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
    }
}

@Composable
private fun BankDetailsFormContent(
    uiState: com.bluemix.clients_lead.features.admin.vm.AdminBankAccountUiState,
    viewModel: AdminBankAccountViewModel
) {
    val user = uiState.selectedUser ?: return
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // User Info Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF3B82F6).copy(alpha = 0.1f))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF3B82F6))
                Column {
                    Text(user.fullName ?: "Unnamed User", fontWeight = FontWeight.Bold, color = Color.White)
                    Text(user.email, fontSize = 12.sp, color = Color.Gray)
                }
            }
        }

        if (uiState.isLoadingDetails) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF3B82F6))
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text("BANK ACCOUNT DETAILS", style = AppTheme.typography.label1, color = Color(0xFF3B82F6))
                }
                
                item {
                    FieldWithLabel("Account Holder Name", uiState.bankDetails.account_holder_name ?: "") {
                        viewModel.onBankDetailsChanged(uiState.bankDetails.copy(account_holder_name = it))
                    }
                }
                
                item {
                    FieldWithLabel("Account Number", uiState.bankDetails.account_number ?: "") {
                        viewModel.onBankDetailsChanged(uiState.bankDetails.copy(account_number = it))
                    }
                }
                
                item {
                    FieldWithLabel("IFSC Code", uiState.bankDetails.ifsc_code ?: "") {
                        viewModel.onBankDetailsChanged(uiState.bankDetails.copy(ifsc_code = it))
                    }
                }
                
                item {
                    FieldWithLabel("Bank Name", uiState.bankDetails.bank_name ?: "") {
                        viewModel.onBankDetailsChanged(uiState.bankDetails.copy(bank_name = it))
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("UPI DETAILS", style = AppTheme.typography.label1, color = Color(0xFF10B981))
                }
                
                item {
                    FieldWithLabel("UPI ID / VPA", uiState.bankDetails.upi_id ?: "") {
                        viewModel.onBankDetailsChanged(uiState.bankDetails.copy(upi_id = it))
                    }
                }
            }

            if (uiState.error != null) {
                Text(uiState.error!!, color = Color.Red, fontSize = 12.sp)
            }
            
            if (uiState.successMessage != null) {
                Text(uiState.successMessage!!, color = Color(0xFF10B981), fontSize = 12.sp)
            }

            Button(
                onClick = { viewModel.saveBankDetails() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp),
                enabled = !uiState.isSaving
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.horizontalGradient(listOf(Color(0xFF3B82F6), Color(0xFF2563EB)))),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text("Save Bank Details", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldWithLabel(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = AppTheme.typography.label3, color = Color.Gray)
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E293B)),
            placeholder = { Text("Enter $label", color = Color.DarkGray) }
        )
    }
}
