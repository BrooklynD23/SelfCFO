package com.ledgerlens.ui.screens.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.viewmodels.review.ReviewFilter
import com.ledgerlens.ui.viewmodels.review.ReviewItemUi
import com.ledgerlens.ui.viewmodels.review.ReviewStats
import com.ledgerlens.ui.viewmodels.review.ReviewViewModel

/**
 * Main Review inbox screen showing items that need user review.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewInboxScreen(
    viewModel: ReviewViewModel,
    onItemClick: (ReviewItemUi) -> Unit,
    onNavigateToCategories: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Inbox") },
                actions = {
                    IconButton(onClick = { viewModel.loadReviewItems() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Stats header
            ReviewStatsHeader(stats = uiState.stats)

            // Filter chips
            FilterChipsRow(
                currentFilter = uiState.currentFilter,
                stats = uiState.stats,
                onFilterSelected = { viewModel.setFilter(it) }
            )

            // Bulk actions
            if (uiState.filteredItems.isNotEmpty()) {
                BulkActionsRow(
                    pendingCount = uiState.stats.pendingCount,
                    onAcceptAll = { viewModel.acceptAll() },
                    onDismissAll = { viewModel.dismissAll() }
                )
            }

            // Content
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.filteredItems.isEmpty() -> {
                    EmptyReviewState(
                        filter = uiState.currentFilter,
                        totalItems = uiState.items.size
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = uiState.filteredItems,
                            key = { it.id }
                        ) { item ->
                            ReviewItemCard(
                                item = item,
                                onAccept = { viewModel.acceptSuggestion(item.id) },
                                onReject = {
                                    // For inline reject, use first alternative or navigate to detail
                                    if (item.alternatives.isNotEmpty()) {
                                        viewModel.rejectSuggestion(
                                            item.id,
                                            item.alternatives.first().categoryId
                                        )
                                    } else {
                                        onItemClick(item)
                                    }
                                },
                                onEdit = { onItemClick(item) },
                                onClick = { onItemClick(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewStatsHeader(
    stats: ReviewStats,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Review Progress",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${stats.processedCount}/${stats.totalItems} reviewed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LinearProgressIndicator(
                progress = { if (stats.totalItems > 0) stats.processedCount.toFloat() / stats.totalItems else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(label = "Pending", value = stats.pendingCount, color = MaterialTheme.colorScheme.primary)
                StatItem(label = "Accepted", value = stats.acceptedCount, color = Color(0xFF4CAF50))
                StatItem(label = "Rejected", value = stats.rejectedCount, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipsRow(
    currentFilter: ReviewFilter,
    stats: ReviewStats,
    onFilterSelected: (ReviewFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(ReviewFilter.values()) { filter ->
            val count = when (filter) {
                ReviewFilter.ALL -> stats.totalItems
                ReviewFilter.PENDING -> stats.pendingCount
                ReviewFilter.LOW_CONFIDENCE -> stats.lowConfidenceCount
                ReviewFilter.DUPLICATES -> stats.duplicateCount
                ReviewFilter.UNCATEGORIZED -> stats.uncategorizedCount
            }

            FilterChip(
                selected = currentFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(filter.displayName)
                        if (count > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "($count)",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

@Composable
private fun BulkActionsRow(
    pendingCount: Int,
    onAcceptAll: () -> Unit,
    onDismissAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onAcceptAll,
            enabled = pendingCount > 0,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Accept All")
        }

        OutlinedButton(
            onClick = onDismissAll,
            enabled = pendingCount > 0,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.DeleteSweep,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Dismiss All")
        }
    }
}

@Composable
private fun EmptyReviewState(
    filter: ReviewFilter,
    totalItems: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = if (filter == ReviewFilter.ALL && totalItems == 0) {
                    Icons.Default.Inbox
                } else {
                    Icons.Default.CheckCircle
                },
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = if (totalItems == 0) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    Color(0xFF4CAF50) // TODO: Use theme success color
                }
            )

            Text(
                text = when {
                    totalItems == 0 -> "No items to review"
                    filter == ReviewFilter.PENDING -> "All caught up!"
                    else -> "No ${filter.displayName.lowercase()} items"
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            Text(
                text = when {
                    totalItems == 0 -> "Import transactions to start reviewing"
                    filter == ReviewFilter.PENDING -> "You've reviewed all pending items"
                    else -> "Try a different filter to see more items"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
