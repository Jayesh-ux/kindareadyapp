// EditNameDialog.kt - Fixed version
package com.bluemix.clients_lead.features.settings.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.AppTheme
import ui.components.Button
import ui.components.ButtonVariant

@Composable
fun EditNameDialog(
    show: Boolean,
    currentName: String?,
    isLoading: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var nameInput by remember(currentName) { mutableStateOf(currentName ?: "") }
    var nameError by remember { mutableStateOf<String?>(null) }

    // Reset input when dialog opens
    LaunchedEffect(show) {
        if (show) {
            nameInput = currentName ?: ""
            nameError = null
        }
    }

    fun validateAndSave() {
        when {
            nameInput.isBlank() -> nameError = "Name cannot be empty"
            nameInput.length < 2 -> nameError = "Name must be at least 2 characters"
            nameInput.length > 50 -> nameError = "Name must be less than 50 characters"
            else -> {
                nameError = null
                onSave(nameInput.trim())
            }
        }
    }

    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = AppTheme.colors.surface,
            tonalElevation = 0.dp,
            icon = {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = AppTheme.colors.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = if (currentName.isNullOrBlank()) "Add Your Name" else "Edit Name",
                    style = AppTheme.typography.h3,
                    color = AppTheme.colors.text
                )
            },
            text = {
                Column {
                    Text(
                        text = if (currentName.isNullOrBlank())
                            "Please add your name for better experience."
                        else
                            "Update your display name",
                        style = AppTheme.typography.body1,
                        color = AppTheme.colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { newValue ->
                            nameInput = newValue
                            nameError = null
                        },
                        label = { Text("Full Name") },
                        placeholder = { Text("Enter your full name") },
                        isError = nameError != null,
                        enabled = !isLoading,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = AppTheme.colors.text,
                            fontSize = 16.sp
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppTheme.colors.text,
                            unfocusedTextColor = AppTheme.colors.text,
                            focusedBorderColor = AppTheme.colors.primary,
                            unfocusedBorderColor = Color(0xFF64748B),
                            focusedLabelColor = AppTheme.colors.primary,
                            unfocusedLabelColor = Color(0xFF64748B),
                            cursorColor = AppTheme.colors.primary,
                            errorBorderColor = AppTheme.colors.error,
                            errorLabelColor = AppTheme.colors.error
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (nameError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = nameError!!,
                            style = AppTheme.typography.body3,
                            color = AppTheme.colors.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { validateAndSave() },
                    variant = ButtonVariant.Primary,
                    enabled = !isLoading && nameInput.isNotBlank()
                ) {
                    Text(
                        text = if (isLoading) "Saving..." else "Save",
                        style = AppTheme.typography.button
                    )
                }
            },
            dismissButton = {
                Button(
                    onClick = onDismiss,
                    variant = ButtonVariant.Secondary,
                    enabled = !isLoading
                ) {
                    Text(
                        text = if (currentName.isNullOrBlank()) "Skip" else "Cancel",
                        style = AppTheme.typography.button
                    )
                }
            }
        )
    }
}
