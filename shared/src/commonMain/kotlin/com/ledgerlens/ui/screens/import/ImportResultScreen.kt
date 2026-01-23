package com.ledgerlens.ui.screens.import

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
// HorizontalDivider removed - use Divider instead
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.viewmodels.import.ImportResult
import com.ledgerlens.ui.viewmodels.import.ImportUiState
import com.ledgerlens.ui.viewmodels.import.ImportViewModel

/**
 * Screen displaying import results with summary statistics and action buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportResultScreen(
    viewModel: ImportViewModel,
    onGoToReview: () -> Unit,
    onGoToTransactions: () -> Unit,
    onImportAnother: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val result = (uiState as? ImportUiState.Complete)?.result

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Complete") }
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (result != null) {
                ImportResultContent(
                    result = result,
                    onGoToReview = onGoToReview,
                    onGoToTransactions = onGoToTransactions,
                    onImportAnother = {
                        viewModel.resetToIdle()
                        onImportAnother()
                    }
                )
            } else {
                Text("No import result available")
                Button(onClick = onImportAnother) {
                    Text("Start New Import")
                }
            }
        }
    }
}

@Composable
private fun ImportResultContent(
    result: ImportResult,
    onGoToReview: () -> Unit,
    onGoToTransactions: () -> Unit,
    onImportAnother: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Success/Warning header
        ResultHeader(result = result)

        // Main stats card
        MainStatsCard(result = result)

        // Detailed breakdown
        DetailedStatsCard(result = result)

        // Review notice if needed
        if (result.hasReviewItems) {
            ReviewNoticeCard(
                needsReviewCount = result.needsReviewCount,
                possibleDuplicates = result.duplicatesPossible,
                onGoToReview = onGoToReview
            )
        }

        // Errors if any
        if (result.errors.isNotEmpty()) {
            ErrorsCard(errors = result.errors)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (result.hasReviewItems) {
                Button(
                    onClick = onGoToReview,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Review ${result.needsReviewCount + result.duplicatesPossible} Items")
                }
            }

            OutlinedButton(
                onClick = onGoToTransactions,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Transactions")
            }

            OutlinedButton(
                onClick = onImportAnother,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import Another File")
            }
        }
    }
}

@Composable
private fun ResultHeader(
    result: ImportResult,
    modifier: Modifier = Modifier
) {
    val (icon, color, title, subtitle) = when {
        result.isFullySuccessful -> ResultHeaderData(
            icon = Icons.Default.CheckCircle,
            color = Color(0xFF4CAF50), // TODO: Replace with LedgerLensTheme.colors.success
            title = "Import Successful",
            subtitle = "All transactions imported successfully"
        )
        result.hasReviewItems -> ResultHeaderData(
            icon = Icons.Default.Warning,
            color = Color(0xFFFFA726), // TODO: Replace with LedgerLensTheme.colors.warning
            title = "Import Complete",
            subtitle = "Some items need your review"
        )
        else -> ResultHeaderData(
            icon = Icons.Default.Error,
            color = MaterialTheme.colorScheme.error,
            title = "Import Completed with Errors",
            subtitle = "Some transactions could not be imported"
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = color
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private data class ResultHeaderData(
    val icon: ImageVector,
    val color: Color,
    val title: String,
    val subtitle: String
)

@Composable
private fun MainStatsCard(
    result: ImportResult,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = result.importedCount.toString(),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Transactions Imported",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "from ${result.fileName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DetailedStatsCard(
    result: ImportResult,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Import Details",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Divider()

            StatRow(label = "Total in file", value = result.totalTransactions.toString())
            StatRow(label = "Successfully imported", value = result.importedCount.toString())
            StatRow(
                label = "Duplicates skipped",
                value = result.duplicatesSkipped.toString(),
                valueColor = if (result.duplicatesSkipped > 0) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else null
            )
            StatRow(
                label = "Possible duplicates",
                value = result.duplicatesPossible.toString(),
                valueColor = if (result.duplicatesPossible > 0) {
                    Color(0xFFFFA726) // TODO: Replace with LedgerLensTheme.colors.warning
                } else null
            )

            Divider()

            StatRow(label = "Auto-categorized", value = result.categorizedCount.toString())
            StatRow(
                label = "Needs categorization",
                value = result.uncategorizedCount.toString(),
                valueColor = if (result.uncategorizedCount > 0) {
                    Color(0xFFFFA726) // TODO: Replace with LedgerLensTheme.colors.warning
                } else null
            )
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ReviewNoticeCard(
    needsReviewCount: Int,
    possibleDuplicates: Int,
    onGoToReview: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3E0) // TODO: Replace with LedgerLensTheme.colors.warningContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFF57C00), // TODO: Replace with LedgerLensTheme.colors.warning
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Items Need Review",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFE65100) // TODO: Replace with LedgerLensTheme.colors.onWarningContainer
                )
            }

            if (needsReviewCount > 0) {
                Text(
                    text = "• $needsReviewCount transactions with low confidence categorization",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5D4037)
                )
            }
            if (possibleDuplicates > 0) {
                Text(
                    text = "• $possibleDuplicates possible duplicate transactions",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5D4037)
                )
            }
        }
    }
}

@Composable
private fun ErrorsCard(
    errors: List<com.ledgerlens.ui.viewmodels.import.ImportError>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${errors.size} Errors",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            errors.take(5).forEach { error ->
                Text(
                    text = buildString {
                        if (error.lineNumber != null) append("Line ${error.lineNumber}: ")
                        append(error.message)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            if (errors.size > 5) {
                Text(
                    text = "... and ${errors.size - 5} more errors",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}
