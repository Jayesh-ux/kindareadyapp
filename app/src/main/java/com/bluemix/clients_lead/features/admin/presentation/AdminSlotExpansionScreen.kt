package com.bluemix.clients_lead.features.admin.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.bluemix.clients_lead.features.admin.vm.AdminSlotExpansionViewModel
import org.koin.androidx.compose.koinViewModel
import ui.AppTheme
import ui.components.Scaffold
import ui.components.Text
import ui.components.topbar.TopBar
import ui.components.topbar.TopBarDefaults

@Composable
fun AdminSlotExpansionScreen(
    viewModel: AdminSlotExpansionViewModel = koinViewModel(),
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
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        text = "Expand Capacity",
                        style = AppTheme.typography.h2,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        containerColor = Color(0xFF020617)
    ) { padding ->
        if (uiState.isLoading && uiState.planData == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF3B82F6))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                UsageCard(uiState)
                
                Text("PURCHASE ADDITIONAL SLOTS", style = AppTheme.typography.label1, color = Color(0xFF3B82F6))
                
                SlotPurchaseSection(
                    title = "User Slots",
                    price = viewModel.PRICE_PER_USER_SLOT,
                    currentQuantity = uiState.userSlotsToPurchase,
                    onQuantityChange = { viewModel.updateUserSlots(it) },
                    icon = Icons.Default.Person
                )
                
                SlotPurchaseSection(
                    title = "Client Slots",
                    price = viewModel.PRICE_PER_CLIENT_SLOT,
                    currentQuantity = uiState.clientSlotsToPurchase,
                    onQuantityChange = { viewModel.updateClientSlots(it) },
                    icon = Icons.Default.Description
                )

                OrderSummary(uiState, viewModel)

                if (uiState.error != null) {
                    Text(uiState.error!!, color = Color.Red, fontSize = 12.sp)
                }
                
                if (uiState.successMessage != null) {
                    Text(uiState.successMessage!!, color = Color(0xFF10B981), fontSize = 12.sp)
                }

                Button(
                    onClick = { viewModel.purchaseSlots() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp),
                    enabled = !uiState.isPurchasing && (uiState.userSlotsToPurchase > 0 || uiState.clientSlotsToPurchase > 0)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.horizontalGradient(listOf(Color(0xFF10B981), Color(0xFF059669)))),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.isPurchasing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        } else {
                            Text("Proceed to Payment", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageCard(uiState: com.bluemix.clients_lead.features.admin.vm.AdminSlotExpansionUiState) {
    val usage = uiState.planData?.usage ?: return
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F172A))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("CURRENT USAGE", style = AppTheme.typography.label3, color = Color.Gray)
            
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                UsageItem(
                    label = "Users",
                    current = usage.users.current,
                    max = usage.users.max,
                    color = Color(0xFF3B82F6),
                    modifier = Modifier.weight(1f)
                )
                UsageItem(
                    label = "Clients",
                    current = usage.clients.current,
                    max = usage.clients.max,
                    color = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun UsageItem(label: String, current: Int, max: Int?, color: Color, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = AppTheme.typography.label3, color = Color.Gray)
        Text("$current / ${max ?: "∞"}", style = AppTheme.typography.h3, color = Color.White, fontWeight = FontWeight.Bold)
        LinearProgressIndicator(
            progress = if (max != null && max > 0) current.toFloat() / max else 0f,
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            color = color,
            trackColor = color.copy(alpha = 0.1f)
        )
    }
}

@Composable
private fun SlotPurchaseSection(
    title: String,
    price: Int,
    currentQuantity: Int,
    onQuantityChange: (Int) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F172A))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF3B82F6).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color(0xFF3B82F6))
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = AppTheme.typography.body1, color = Color.White, fontWeight = FontWeight.Bold)
                Text("₹$price per slot", style = AppTheme.typography.body3, color = Color.Gray)
            }
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(
                    onClick = { onQuantityChange(currentQuantity - 1) },
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF1E293B)),
                    enabled = currentQuantity > 0
                ) {
                    Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                
                Text("$currentQuantity", style = AppTheme.typography.body1, color = Color.White, fontWeight = FontWeight.Bold)
                
                IconButton(
                    onClick = { onQuantityChange(currentQuantity + 1) },
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF3B82F6))
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun OrderSummary(
    uiState: com.bluemix.clients_lead.features.admin.vm.AdminSlotExpansionUiState,
    viewModel: AdminSlotExpansionViewModel
) {
    val total = (uiState.userSlotsToPurchase * viewModel.PRICE_PER_USER_SLOT) + 
                (uiState.clientSlotsToPurchase * viewModel.PRICE_PER_CLIENT_SLOT)
                
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E293B).copy(alpha = 0.5f))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ORDER SUMMARY", style = AppTheme.typography.label3, color = Color.Gray)
            
            if (uiState.userSlotsToPurchase > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${uiState.userSlotsToPurchase} User Slots", color = Color.White.copy(alpha = 0.7f))
                    Text("₹${uiState.userSlotsToPurchase * viewModel.PRICE_PER_USER_SLOT}", color = Color.White)
                }
            }
            
            if (uiState.clientSlotsToPurchase > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${uiState.clientSlotsToPurchase} Client Slots", color = Color.White.copy(alpha = 0.7f))
                    Text("₹${uiState.clientSlotsToPurchase * viewModel.PRICE_PER_CLIENT_SLOT}", color = Color.White)
                }
            }
            
            androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total Amount", style = AppTheme.typography.body1, color = Color.White, fontWeight = FontWeight.Bold)
                Text("₹$total", style = AppTheme.typography.h3, color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold)
            }
        }
    }
}
