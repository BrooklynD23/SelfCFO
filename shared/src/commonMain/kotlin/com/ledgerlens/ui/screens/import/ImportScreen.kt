package com.ledgerlens.ui.screens.import

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.viewmodels.import.BankSource
import com.ledgerlens.ui.viewmodels.import.ImportFileType
import com.ledgerlens.ui.viewmodels.import.ImportUiState
import com.ledgerlens.ui.viewmodels.import.ImportViewModel

/**
 * Main Import screen with file type selection, bank selection, and file picker.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    onNavigateToProgress: () -> Unit,
    onNavigateToResult: () -> Unit,
    onFilePick: (ImportFileType) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedFileType by viewModel.selectedFileType.collectAsState()
    val selectedBank by viewModel.selectedBank.collectAsState()

    // Navigate based on state changes
    when (uiState) {
        is ImportUiState.Importing -> onNavigateToProgress()
        is ImportUiState.Complete -> onNavigateToResult()
        else -> { /* Stay on this screen */ }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Transactions") }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Step 1: File Type Selection
            ImportSectionHeader(
                stepNumber = 1,
                title = "Select File Type",
                subtitle = "Choose the format of your bank statement"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FileTypeCard(
                    fileType = ImportFileType.CSV,
                    isSelected = selectedFileType == ImportFileType.CSV,
                    onClick = { viewModel.selectFileType(ImportFileType.CSV) },
                    modifier = Modifier.weight(1f)
                )
                FileTypeCard(
                    fileType = ImportFileType.PDF,
                    isSelected = selectedFileType == ImportFileType.PDF,
                    onClick = { viewModel.selectFileType(ImportFileType.PDF) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Step 2: Bank Selection
            ImportSectionHeader(
                stepNumber = 2,
                title = "Select Your Bank",
                subtitle = "This helps us parse your statement correctly"
            )

            Text(
                text = "Popular Banks",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BankSource.popularBanks.forEach { bank ->
                    BankChip(
                        bank = bank,
                        isSelected = selectedBank == bank,
                        onClick = { viewModel.selectBank(bank) }
                    )
                }
            }

            Text(
                text = "Other Banks",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BankSource.allBanks.filterNot { it in BankSource.popularBanks }.forEach { bank ->
                    BankChip(
                        bank = bank,
                        isSelected = selectedBank == bank,
                        onClick = { viewModel.selectBank(bank) }
                    )
                }
            }

            // Step 3: File Selection
            ImportSectionHeader(
                stepNumber = 3,
                title = "Select File",
                subtitle = "Choose the file to import"
            )

            FileSelectionArea(
                selectedFile = (uiState as? ImportUiState.FileSelected)?.fileName,
                fileType = selectedFileType,
                onSelectFile = { onFilePick(selectedFileType) }
            )

            // Import Button
            Spacer(modifier = Modifier.height(8.dp))

            val canImport = uiState is ImportUiState.FileSelected && selectedBank != null

            Button(
                onClick = { viewModel.startImport() },
                enabled = canImport,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.FileUpload,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Import")
            }

            if (!canImport && uiState is ImportUiState.FileSelected) {
                Text(
                    text = "Please select a bank to continue",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Error state display
            if (uiState is ImportUiState.Error) {
                val errorState = uiState as ImportUiState.Error
                ImportErrorCard(
                    error = errorState,
                    onRetry = { viewModel.retryImport() },
                    onDismiss = { viewModel.dismissError() }
                )
            }
        }
    }
}

@Composable
private fun ImportSectionHeader(stepNumber: Int, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stepNumber.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileTypeCard(
    fileType: ImportFileType,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon: ImageVector = when (fileType) {
        ImportFileType.CSV -> Icons.Default.TableChart
        ImportFileType.PDF -> Icons.Default.PictureAsPdf
    }

    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = fileType.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BankChip(bank: BankSource, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(bank.displayName) },
        leadingIcon = if (isSelected) {
            {
                Icon(
                    imageVector = Icons.Default.AccountBalance,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        } else {
            null
        },
        modifier = modifier
    )
}

@Composable
private fun FileSelectionArea(
    selectedFile: String?,
    fileType: ImportFileType,
    onSelectFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selectedFile != null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectFile() }
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (selectedFile != null) {
                    Icons.Default.Description
                } else {
                    Icons.Default.FileUpload
                },
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = if (selectedFile != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            if (selectedFile != null) {
                Text(
                    text = selectedFile,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedButton(onClick = onSelectFile) {
                    Text("Choose Different File")
                }
            } else {
                Text(
                    text = "Tap to select a ${fileType.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Supported: ${fileType.extensions.joinToString(", ") { ".$it" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ImportErrorCard(
    error: ImportUiState.Error,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Import Error",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
                if (error.canRetry) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}
