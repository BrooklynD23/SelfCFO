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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.viewmodels.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(viewModel: SettingsViewModel, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.backupState.collectAsState()
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadBackupState() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Export section
                item {
                    Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CloudUpload, null, Modifier.size(24.dp), MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Text("Export Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Create an encrypted backup of all your data", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            state.lastBackupDate?.let {
                                Spacer(Modifier.height(8.dp))
                                Text("Last backup: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Spacer(Modifier.height(16.dp))

                            if (state.isExporting) {
                                LinearProgressIndicator(progress = state.exportProgress, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                Text("Exporting...", style = MaterialTheme.typography.bodySmall)
                            } else {
                                Button(onClick = { showExportDialog = true }, Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Backup, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Create Backup")
                                }
                            }
                        }
                    }
                }

                // Import section
                item {
                    Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CloudDownload, null, Modifier.size(24.dp), MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.width(12.dp))
                                Text("Restore Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Restore your data from a backup file", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))

                            if (state.isImporting) {
                                LinearProgressIndicator(progress = state.importProgress, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                Text("Importing...", style = MaterialTheme.typography.bodySmall)
                            } else {
                                OutlinedButton(onClick = { showImportDialog = true }, Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Restore, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Restore from Backup")
                                }
                            }
                        }
                    }
                }

                // Recovery key section
                item {
                    Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Key, null, Modifier.size(24.dp), MaterialTheme.colorScheme.tertiary)
                                Spacer(Modifier.width(12.dp))
                                Text("Recovery Key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Your 24-word recovery phrase can restore access if you forget your passphrase", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(onClick = { viewModel.showRecoveryKey() }, Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Visibility, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("View Recovery Key")
                            }
                        }
                    }
                }

                // Info card
                item {
                    Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                        Row(Modifier.padding(16.dp)) {
                            Icon(Icons.Default.Info, null, Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text("Backups are encrypted with a passphrase you choose. Keep this passphrase safe - without it, your backup cannot be restored.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    // Export dialog
    if (showExportDialog) {
        ExportBackupDialog(onExport = { pass ->
            viewModel.exportBackup(pass)
            showExportDialog = false
        }, onDismiss = { showExportDialog = false })
    }

    // Import dialog
    if (showImportDialog) {
        ImportBackupDialog(onImport = { exp, newP ->
            viewModel.importBackup(exp, newP)
            showImportDialog = false
        }, onDismiss = { showImportDialog = false })
    }

    // Recovery key dialog
    if (state.showRecoveryKey) {
        RecoveryKeyDialog(recoveryKey = state.recoveryKey, onCopy = {
            viewModel.copyRecoveryKey()
        }, onDismiss = { viewModel.hideRecoveryKey() })
    }

    // Error/success snackbars would go here
}

@Composable
private fun ExportBackupDialog(onExport: (String) -> Unit, onDismiss: () -> Unit) {
    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    val canExport = passphrase.length >= 8 && passphrase == confirmPassphrase

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Backup") },
        text = {
            Column {
                Text("Choose a passphrase to encrypt your backup:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(passphrase, {
                    passphrase = it
                }, label = { Text("Passphrase") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(confirmPassphrase, {
                    confirmPassphrase = it
                }, label = {
                    Text("Confirm Passphrase")
                }, singleLine = true, modifier = Modifier.fillMaxWidth(), isError = confirmPassphrase.isNotBlank() && confirmPassphrase != passphrase)
                if (passphrase.isNotBlank() && passphrase.length < 8) {
                    Text("Passphrase must be at least 8 characters", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { Button(onClick = { onExport(passphrase) }, enabled = canExport) { Text("Export") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ImportBackupDialog(onImport: (String, String) -> Unit, onDismiss: () -> Unit) {
    var exportPassphrase by remember { mutableStateOf("") }
    var newPassphrase by remember { mutableStateOf("") }
    val canImport = exportPassphrase.isNotBlank() && newPassphrase.length >= 8

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore Backup") },
        text = {
            Column {
                Text("Enter the passphrase used when creating the backup:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(exportPassphrase, {
                    exportPassphrase = it
                }, label = { Text("Backup Passphrase") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Text("Set a new passphrase for this device:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(newPassphrase, {
                    newPassphrase = it
                }, label = { Text("New Passphrase") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onImport(exportPassphrase, newPassphrase) }, enabled = canImport) { Text("Restore") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RecoveryKeyDialog(
    recoveryKey: com.ledgerlens.security.RecoveryKey?,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recovery Key") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Warning, null, Modifier.size(48.dp), MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                Text("Write down these words and store them securely. Anyone with this key can access your data.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(8.dp), CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)) {
                    Text(recoveryKey?.mnemonic?.joinToString(" ") ?: "Unable to retrieve recovery key", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = { Button(onClick = onCopy) { Text("Copy") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
