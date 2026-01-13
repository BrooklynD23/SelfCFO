# 09: Settings Screen

## Overview

Implement the settings screen with preferences, data management, account settings, and app information.

---

## Implementation Steps

### Step 1: Settings State

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/settings/SettingsState.kt
package com.ledgerlens.ui.screens.settings

data class SettingsState(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val defaultCurrency: String = "USD",
    val reviewInboxCount: Int = 0,
    val categoriesCount: Int = 0,
    val rulesCount: Int = 0,
    val accountsCount: Int = 0,
    val appVersion: String = "",
    val buildNumber: String = ""
)

enum class ThemeMode(val label: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System Default")
}

data class DataManagementState(
    val isLoading: Boolean = false,
    val totalTransactions: Int = 0,
    val totalImports: Int = 0,
    val databaseSize: String = "",
    val lastBackup: String? = null,
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f
)

data class AccountSettingsState(
    val isLoading: Boolean = true,
    val accounts: List<AccountDisplay> = emptyList()
)

data class AccountDisplay(
    val id: String,
    val displayName: String,
    val institutionName: String?,
    val accountType: String,
    val lastImportDate: String?,
    val transactionCount: Int,
    val isHidden: Boolean
)
```

### Step 2: Settings Screen

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/settings/SettingsScreen.kt
package com.ledgerlens.ui.screens.settings

@Composable
fun SettingsScreen(
    onNavigateToCategories: () -> Unit,
    onNavigateToRules: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToData: () -> Unit,
    onNavigateToAbout: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Appearance section
            item {
                SettingsSectionHeader("Appearance")
            }
            item {
                ThemeSelector(
                    currentTheme = state.theme,
                    onThemeChange = viewModel::setTheme
                )
            }

            // Data section
            item {
                SettingsSectionHeader("Data")
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Category,
                    title = "Categories",
                    subtitle = "${state.categoriesCount} categories",
                    onClick = onNavigateToCategories
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Rule,
                    title = "Categorization Rules",
                    subtitle = "${state.rulesCount} rules",
                    onClick = onNavigateToRules
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.AccountBalance,
                    title = "Accounts",
                    subtitle = "${state.accountsCount} accounts",
                    onClick = onNavigateToAccounts
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Storage,
                    title = "Data Management",
                    subtitle = "Export, backup, clear data",
                    onClick = onNavigateToData
                )
            }

            // Review section
            if (state.reviewInboxCount > 0) {
                item {
                    SettingsSectionHeader("Review")
                }
                item {
                    SettingsItem(
                        icon = Icons.Default.Inbox,
                        title = "Review Inbox",
                        subtitle = "${state.reviewInboxCount} items need review",
                        onClick = { /* Navigate to review */ },
                        badge = state.reviewInboxCount.toString()
                    )
                }
            }

            // About section
            item {
                SettingsSectionHeader("About")
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "About LedgerLens",
                    subtitle = "Version ${state.appVersion}",
                    onClick = onNavigateToAbout
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    badge: String? = null
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(icon, contentDescription = null)
        },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                badge?.let {
                    Badge { Text(it) }
                }
                Icon(Icons.Default.ChevronRight, null)
            }
        }
    )
}

@Composable
private fun ThemeSelector(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ListItem(
        modifier = Modifier.clickable { expanded = true },
        headlineContent = { Text("Theme") },
        supportingContent = { Text(currentTheme.label) },
        leadingContent = {
            Icon(Icons.Default.Palette, null)
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, null)
        }
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        ThemeMode.entries.forEach { theme ->
            DropdownMenuItem(
                text = { Text(theme.label) },
                onClick = {
                    onThemeChange(theme)
                    expanded = false
                },
                leadingIcon = {
                    if (theme == currentTheme) {
                        Icon(Icons.Default.Check, null)
                    }
                }
            )
        }
    }
}
```

### Step 3: Data Management Screen

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/settings/DataManagementScreen.kt
package com.ledgerlens.ui.screens.settings

@Composable
fun DataManagementScreen(
    onBack: () -> Unit,
    viewModel: DataManagementViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showClearDataDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data Management") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Statistics
            item {
                LedgerCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Statistics", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(16.dp))

                        StatRow("Transactions", state.totalTransactions.toString())
                        StatRow("Imports", state.totalImports.toString())
                        StatRow("Database Size", state.databaseSize)
                    }
                }
            }

            // Export
            item {
                LedgerCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Export", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Export all your data for backup or use in other apps",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        if (state.isExporting) {
                            LinearProgressIndicator(
                                progress = { state.exportProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.exportCsv() }
                                ) {
                                    Icon(Icons.Default.TableChart, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Export CSV")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.exportJson() }
                                ) {
                                    Icon(Icons.Default.Code, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Export JSON")
                                }
                            }
                        }
                    }
                }
            }

            // Backup
            item {
                LedgerCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Backup", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))

                        state.lastBackup?.let {
                            Text(
                                text = "Last backup: $it",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.createBackup() }) {
                                Icon(Icons.Default.Backup, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Create Backup")
                            }
                            OutlinedButton(onClick = { viewModel.restoreBackup() }) {
                                Icon(Icons.Default.Restore, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Restore")
                            }
                        }
                    }
                }
            }

            // Danger zone
            item {
                LedgerCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Danger Zone",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "These actions cannot be undone",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = { showClearDataDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.DeleteForever, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Clear All Data")
                        }
                    }
                }
            }
        }
    }

    // Clear data confirmation dialog
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Clear All Data?") },
            text = {
                Text("This will permanently delete all your transactions, categories, and rules. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllData()
                        showClearDataDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
```

### Step 4: About Screen

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/settings/AboutScreen.kt
package com.ledgerlens.ui.screens.settings

@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App icon/logo
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Primary, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "LL",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "LedgerLens",
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = "Version 1.0.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "A local-first personal finance app that keeps your data private and secure.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Links
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinkItem(
                    icon = Icons.Default.Security,
                    text = "Privacy Policy",
                    onClick = { /* Open URL */ }
                )
                LinkItem(
                    icon = Icons.Default.Description,
                    text = "Terms of Service",
                    onClick = { /* Open URL */ }
                )
                LinkItem(
                    icon = Icons.Default.Code,
                    text = "Open Source Licenses",
                    onClick = { /* Show licenses */ }
                )
                LinkItem(
                    icon = Icons.Default.Email,
                    text = "Send Feedback",
                    onClick = { /* Open email */ }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Made with ❤️ for your financial privacy",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LinkItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            Icons.Default.OpenInNew,
            null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

---

## Acceptance Criteria

- [ ] Theme selector (light/dark/system) works
- [ ] Navigate to categories, rules, accounts
- [ ] Data statistics displayed correctly
- [ ] Export to CSV works
- [ ] Export to JSON works
- [ ] Create/restore backup works
- [ ] Clear all data with confirmation
- [ ] About screen shows version info
- [ ] External links work

---

## Estimated Complexity

**Low** - Standard settings interface with basic functionality.

