package com.ledgerlens.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.viewmodels.settings.AppTheme
import com.ledgerlens.ui.viewmodels.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToBackup: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.settingsState.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsSection("Account") {
                    SettingsItem(Icons.Default.Backup, "Backup & Restore", "Export, import, recovery key", onNavigateToBackup)
                    Divider(Modifier.padding(start = 56.dp))
                    SettingsItem(Icons.Default.FileDownload, "Export Data", "Export as CSV", {})
                }
            }
            item {
                SettingsSection("Security") {
                    SettingsItem(Icons.Default.Lock, "Security Settings", "Passphrase, biometrics", onNavigateToSecurity)
                    if (state.biometricsAvailable) {
                        Divider(Modifier.padding(start = 56.dp))
                        SettingsToggle(Icons.Default.Fingerprint, "Use Biometrics", state.useBiometrics, viewModel::toggleBiometrics)
                    }
                }
            }
            item {
                SettingsSection("Appearance") {
                    SettingsItem(
                        Icons.Default.Palette, "Theme",
                        state.theme.name.lowercase().replaceFirstChar {
                            it.uppercase()
                        },
                        onClick = { showThemeDialog = true }
                    )
                    Divider(Modifier.padding(start = 56.dp))
                    SettingsItem(Icons.Default.AttachMoney, "Default Currency", state.defaultCurrency, onClick = {
                        showCurrencyDialog = true
                    })
                }
            }
            item {
                SettingsSection("About") {
                    SettingsItem(Icons.Default.Info, "Version", "${state.appVersion} (${state.buildNumber})", {}, false)
                    Divider(Modifier.padding(start = 56.dp))
                    SettingsItem(Icons.Default.Description, "Privacy Policy", "How we handle data", {})
                }
            }
        }
    }

    if (showThemeDialog) {
        ThemeDialog(state.theme, {
            viewModel.setTheme(it)
            showThemeDialog = false
        }) { showThemeDialog = false }
    }
    if (showCurrencyDialog) {
        CurrencyDialog(state.defaultCurrency, {
            viewModel.setDefaultCurrency(it)
            showCurrencyDialog = false
        }) { showCurrencyDialog = false }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
        Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(vertical = 8.dp), content = content)
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showChevron: Boolean = true
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showChevron) Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsToggle(icon: ImageVector, title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable {
            onCheckedChange(!checked)
        }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Switch(checked, onCheckedChange)
    }
}

@Composable
private fun ThemeDialog(current: AppTheme, onSelect: (AppTheme) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismiss, { TextButton(onDismiss) { Text("Cancel") } }, title = { Text("Select Theme") }, text = {
        Column {
            AppTheme.entries.forEach { theme ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        onSelect(theme)
                    }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(theme == current, { onSelect(theme) })
                    Spacer(Modifier.width(12.dp))
                    Text(theme.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
        }
    })
}

@Composable
private fun CurrencyDialog(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val currencies = listOf("USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF", "CNY", "INR", "MXN")
    AlertDialog(onDismiss, { TextButton(onDismiss) { Text("Cancel") } }, title = { Text("Select Currency") }, text = {
        Column {
            currencies.forEach { code ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        onSelect(code)
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(code == current, { onSelect(code) })
                    Spacer(Modifier.width(12.dp))
                    Text(code, fontWeight = if (code == current) FontWeight.Medium else FontWeight.Normal)
                }
            }
        }
    })
}
