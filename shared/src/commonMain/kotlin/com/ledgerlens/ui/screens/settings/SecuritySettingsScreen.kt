package com.ledgerlens.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.viewmodels.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.securityState.collectAsState()
    
    LaunchedEffect(Unit) { viewModel.loadSecuritySettings() }
    
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Security") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading && !state.showChangePassphrase && !state.showCryptoEraseConfirm) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Passphrase section
                item {
                    Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Password, null, Modifier.size(24.dp), MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Text("Passphrase", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Your passphrase protects all your data", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(onClick = { viewModel.showChangePassphrase() }, Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Change Passphrase")
                            }
                        }
                    }
                }
                
                // Biometrics section
                if (state.biometricsAvailable) {
                    item {
                        Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp)) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Fingerprint, null, Modifier.size(24.dp), MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Biometric Unlock", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Use fingerprint or face to unlock", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(state.biometricsEnabled, { viewModel.toggleSecurityBiometrics(it) })
                            }
                        }
                    }
                }
                
                // Danger zone
                item {
                    Spacer(Modifier.height(16.dp))
                    Text("Danger Zone", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                }
                
                item {
                    Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DeleteForever, null, Modifier.size(24.dp), MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(12.dp))
                                Text("Crypto Erase", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Permanently and irreversibly delete all data by destroying the encryption keys. This cannot be undone.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = { viewModel.showCryptoEraseConfirm() },
                                Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Warning, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Erase All Data")
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Change passphrase dialog
    if (state.showChangePassphrase) {
        ChangePassphraseDialog(
            currentPassphrase = state.currentPassphrase,
            newPassphrase = state.newPassphrase,
            confirmPassphrase = state.confirmPassphrase,
            currentError = state.currentPassphraseError,
            newError = state.newPassphraseError,
            confirmError = state.confirmPassphraseError,
            canSave = state.canChangePassphrase,
            isLoading = state.isLoading,
            onCurrentChange = viewModel::setCurrentPassphrase,
            onNewChange = viewModel::setNewPassphrase,
            onConfirmChange = viewModel::setConfirmPassphrase,
            onSave = viewModel::changePassphrase,
            onDismiss = viewModel::hideChangePassphrase
        )
    }
    
    // Crypto erase confirmation dialog
    if (state.showCryptoEraseConfirm) {
        CryptoEraseDialog(
            confirmText = state.cryptoEraseConfirmText,
            canErase = state.canCryptoErase,
            isLoading = state.isLoading,
            onConfirmTextChange = viewModel::setCryptoEraseConfirmText,
            onErase = viewModel::executeCryptoErase,
            onDismiss = viewModel::hideCryptoEraseConfirm
        )
    }
}

@Composable
private fun ChangePassphraseDialog(
    currentPassphrase: String,
    newPassphrase: String,
    confirmPassphrase: String,
    currentError: String?,
    newError: String?,
    confirmError: String?,
    canSave: Boolean,
    isLoading: Boolean,
    onCurrentChange: (String) -> Unit,
    onNewChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    var showCurrent by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Passphrase") },
        text = {
            Column {
                OutlinedTextField(
                    value = currentPassphrase,
                    onValueChange = onCurrentChange,
                    label = { Text("Current Passphrase") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = currentError != null,
                    supportingText = currentError?.let { { Text(it) } },
                    visualTransformation = if (showCurrent) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showCurrent = !showCurrent }) {
                            Icon(if (showCurrent) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Toggle visibility")
                        }
                    }
                )
                
                Spacer(Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = newPassphrase,
                    onValueChange = onNewChange,
                    label = { Text("New Passphrase") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = newError != null,
                    supportingText = newError?.let { { Text(it) } },
                    visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showNew = !showNew }) {
                            Icon(if (showNew) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Toggle visibility")
                        }
                    }
                )
                
                Spacer(Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = confirmPassphrase,
                    onValueChange = onConfirmChange,
                    label = { Text("Confirm New Passphrase") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = confirmError != null,
                    supportingText = confirmError?.let { { Text(it) } },
                    visualTransformation = PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = canSave && !isLoading) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Change")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Cancel") } }
    )
}

@Composable
private fun CryptoEraseDialog(
    confirmText: String,
    canErase: Boolean,
    isLoading: Boolean,
    onConfirmTextChange: (String) -> Unit,
    onErase: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text("Erase All Data", color = MaterialTheme.colorScheme.error)
            }
        },
        text = {
            Column {
                Text("This action will permanently destroy all encryption keys, making your data unrecoverable.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                
                Spacer(Modifier.height(16.dp))
                
                Text("Type DELETE ALL DATA to confirm:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = confirmText,
                    onValueChange = onConfirmTextChange,
                    placeholder = { Text("DELETE ALL DATA") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.error,
                        cursorColor = MaterialTheme.colorScheme.error
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onErase,
                enabled = canErase && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onError)
                } else {
                    Text("Erase Everything")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Cancel") } }
    )
}
