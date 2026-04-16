package com.bluemix.clients_lead.features.admin.presentation

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemix.clients_lead.features.admin.vm.AdminPlanUsageViewModel
import org.koin.androidx.compose.koinViewModel
import ui.AppTheme
import ui.components.Scaffold
import ui.components.Text
import ui.components.topbar.TopBar
import ui.components.topbar.TopBarDefaults

@Composable
fun AdminPlanUsageScreen(
    viewModel: AdminPlanUsageViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToUpgrade: () -> Unit
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppTheme.colors.onPrimary)
                    }
                    Text(
                        text = "Plan & Usage",
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
        } else if (uiState.planData != null) {
            val plan = uiState.planData!!.plan
            val usage = uiState.planData!!.usage

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Plan Info Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6))))
                        .padding(24.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Stars, contentDescription = null, tint = Color.Yellow)
                            Text(plan.displayName, style = AppTheme.typography.h2, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Text("Current Subscription", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("₹${plan.priceINR}", style = AppTheme.typography.h1, color = Color.White, fontWeight = FontWeight.Black)
                        Text("per month", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }

                // Resource Usage Table
                Text("RESOURCE USAGE", style = AppTheme.typography.label1, color = Color(0xFF3B82F6))
                
                ResourceUsageItem("Team members", usage.users.current, usage.users.max, Color(0xFF3B82F6))
                ResourceUsageItem("Clients", usage.clients.current, usage.clients.max, Color(0xFF10B981))
                
                // Activity Stats
                Text("ACTIVITY SUMMARY", style = AppTheme.typography.label1, color = Color(0xFF3B82F6))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActivityStatBox("Meetings", usage.meetings.toString(), Color(0xFF8B5CF6), Modifier.weight(1f))
                    ActivityStatBox("Expenses", usage.expenses.toString(), Color(0xFFF59E0B), Modifier.weight(1f))
                }
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActivityStatBox("Services", usage.services.toString(), Color(0xFF10B981), Modifier.weight(1f))
                    ActivityStatBox("Location Logs", "${usage.locationLogs / 1000}K", Color(0xFF3B82F6), Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onNavigateToUpgrade,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                ) {
                    Text("Upgrade Plan", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(uiState.error!!, color = Color.Red)
            }
        }
    }
}

@Composable
private fun ResourceUsageItem(label: String, current: Int, max: Int?, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F172A))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = AppTheme.typography.body1, color = Color.White)
                Text("$current / ${max ?: "∞"}", style = AppTheme.typography.body1, color = Color.White, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = if (max != null && max > 0) current.toFloat() / max else 0f,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = color,
                trackColor = color.copy(alpha = 0.1f)
            )
        }
    }
}

@Composable
private fun ActivityStatBox(label: String, value: String, color: Color, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F172A))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = AppTheme.typography.label3, color = Color.Gray)
            Text(value, style = AppTheme.typography.h2, color = color, fontWeight = FontWeight.Bold)
        }
    }
}
