package com.ledgerlens.ui.screens.receipts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.viewmodels.receipts.ReceiptSortOption
import com.ledgerlens.ui.viewmodels.receipts.ReceiptUiModel
import com.ledgerlens.ui.viewmodels.receipts.ReceiptsUiState
import com.ledgerlens.ui.viewmodels.receipts.ReceiptsViewModel

/**
 * Main receipts list screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptsScreen(
    viewModel: ReceiptsViewModel,
    onReceiptClick: (String) -> Unit,
    onAddReceipt: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.receiptsState.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    
    Scaffold(
        modifier = modifier,
        topBar = {
            ReceiptsTopBar(
                state = state,
                onSearchQueryChange = viewModel::setSearchQuery,
                onToggleSelectionMode = viewModel::toggleSelectionMode,
                onSelectAll = viewModel::selectAllReceipts,
                onDeleteSelected = viewModel::deleteSelectedReceipts,
                onClearSelection = viewModel::clearSelection
            )
        },
        floatingActionButton = {
            if (!state.isSelectionMode) {
                FloatingActionButton(
                    onClick = onAddReceipt,
                    // TODO: Replace with LedgerLensTheme.colors.primary
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Receipt")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter chips row
            FilterChipsRow(
                state = state,
                showSortMenu = showSortMenu,
                showFilterMenu = showFilterMenu,
                onShowSortMenu = { showSortMenu = it },
                onShowFilterMenu = { showFilterMenu = it },
                onSortOptionSelected = { 
                    viewModel.setSortOption(it)
                    showSortMenu = false
                },
                onFilterLinkedSelected = {
                    viewModel.setFilterLinked(it)
                    showFilterMenu = false
                }
            )
            
            // Content
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null -> {
                    ErrorContent(
                        error = state.error!!,
                        onRetry = viewModel::loadReceipts,
                        onDismiss = viewModel::clearError
                    )
                }
                state.filteredReceipts.isEmpty() -> {
                    EmptyReceiptsContent(
                        hasFilters = state.searchQuery.isNotBlank() || state.filterLinked != null,
                        onAddReceipt = onAddReceipt
                    )
                }
                else -> {
                    ReceiptsList(
                        receipts = state.filteredReceipts,
                        isSelectionMode = state.isSelectionMode,
                        selectedIds = state.selectedReceiptIds,
                        onReceiptClick = { receiptId ->
                            if (state.isSelectionMode) {
                                viewModel.toggleReceiptSelection(receiptId)
                            } else {
                                onReceiptClick(receiptId)
                            }
                        },
                        onReceiptLongClick = { receiptId ->
                            if (!state.isSelectionMode) {
                                viewModel.toggleSelectionMode()
                            }
                            viewModel.toggleReceiptSelection(receiptId)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptsTopBar(
    state: ReceiptsUiState,
    onSearchQueryChange: (String) -> Unit,
    onToggleSelectionMode: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onClearSelection: () -> Unit
) {
    var isSearchExpanded by remember { mutableStateOf(false) }
    
    TopAppBar(
        title = {
            if (state.isSelectionMode) {
                Text("${state.selectedCount} selected")
            } else if (isSearchExpanded) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search receipts...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
            } else {
                Text("Receipts")
            }
        },
        navigationIcon = {
            if (state.isSelectionMode) {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                }
            }
        },
        actions = {
            if (state.isSelectionMode) {
                IconButton(onClick = onSelectAll) {
                    Icon(Icons.Default.SelectAll, contentDescription = "Select all")
                }
                IconButton(
                    onClick = onDeleteSelected,
                    enabled = state.hasSelection
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete selected",
                        tint = if (state.hasSelection) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            } else {
                IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                    Icon(
                        if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = if (isSearchExpanded) "Close search" else "Search"
                    )
                }
                IconButton(onClick = onToggleSelectionMode) {
                    Icon(Icons.Default.Checklist, contentDescription = "Select multiple")
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipsRow(
    state: ReceiptsUiState,
    showSortMenu: Boolean,
    showFilterMenu: Boolean,
    onShowSortMenu: (Boolean) -> Unit,
    onShowFilterMenu: (Boolean) -> Unit,
    onSortOptionSelected: (ReceiptSortOption) -> Unit,
    onFilterLinkedSelected: (Boolean?) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Sort chip
        item {
            Box {
                FilterChip(
                    selected = false,
                    onClick = { onShowSortMenu(true) },
                    label = { 
                        Text(when (state.sortBy) {
                            ReceiptSortOption.DATE_DESC -> "Newest"
                            ReceiptSortOption.DATE_ASC -> "Oldest"
                            ReceiptSortOption.AMOUNT_DESC -> "Highest"
                            ReceiptSortOption.AMOUNT_ASC -> "Lowest"
                            ReceiptSortOption.MERCHANT -> "Merchant"
                        })
                    },
                    leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { onShowSortMenu(false) }
                ) {
                    ReceiptSortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { 
                                Text(when (option) {
                                    ReceiptSortOption.DATE_DESC -> "Newest first"
                                    ReceiptSortOption.DATE_ASC -> "Oldest first"
                                    ReceiptSortOption.AMOUNT_DESC -> "Highest amount"
                                    ReceiptSortOption.AMOUNT_ASC -> "Lowest amount"
                                    ReceiptSortOption.MERCHANT -> "By merchant"
                                })
                            },
                            onClick = { onSortOptionSelected(option) },
                            leadingIcon = {
                                if (state.sortBy == option) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            }
                        )
                    }
                }
            }
        }
        
        // Linked filter chip
        item {
            Box {
                FilterChip(
                    selected = state.filterLinked != null,
                    onClick = { onShowFilterMenu(true) },
                    label = { 
                        Text(when (state.filterLinked) {
                            true -> "Linked"
                            false -> "Unlinked"
                            null -> "All"
                        })
                    },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                
                DropdownMenu(
                    expanded = showFilterMenu,
                    onDismissRequest = { onShowFilterMenu(false) }
                ) {
                    DropdownMenuItem(
                        text = { Text("All receipts") },
                        onClick = { onFilterLinkedSelected(null) },
                        leadingIcon = { if (state.filterLinked == null) Icon(Icons.Default.Check, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Linked only") },
                        onClick = { onFilterLinkedSelected(true) },
                        leadingIcon = { if (state.filterLinked == true) Icon(Icons.Default.Check, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Unlinked only") },
                        onClick = { onFilterLinkedSelected(false) },
                        leadingIcon = { if (state.filterLinked == false) Icon(Icons.Default.Check, contentDescription = null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiptsList(
    receipts: List<ReceiptUiModel>,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    onReceiptClick: (String) -> Unit,
    onReceiptLongClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(receipts, key = { it.id }) { receipt ->
            ReceiptListItem(
                receipt = receipt,
                isSelectionMode = isSelectionMode,
                isSelected = receipt.id in selectedIds,
                onClick = { onReceiptClick(receipt.id) },
                onLongClick = { onReceiptLongClick(receipt.id) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptListItem(
    receipt: ReceiptUiModel,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // TODO: Replace with LedgerLensCard when available
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection checkbox or thumbnail
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 12.dp)
                )
            } else {
                // Receipt thumbnail placeholder
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Receipt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            
            // Receipt info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = receipt.merchant,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    if (receipt.isLinked) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Link,
                            contentDescription = "Linked to transaction",
                            modifier = Modifier.size(16.dp),
                            // TODO: Replace with LedgerLensTheme.colors.success
                            tint = Color(0xFF4CAF50)
                        )
                    }
                    
                    if (receipt.hasLowConfidence) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Low confidence",
                            modifier = Modifier.size(16.dp),
                            // TODO: Replace with LedgerLensTheme.colors.warning
                            tint = Color(0xFFFF9800)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = receipt.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "${receipt.itemCount} items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Amount
            // TODO: Replace with MoneyText component
            Text(
                text = "$${receipt.totalAmount.minorUnits / 100}.${(receipt.totalAmount.minorUnits % 100).toString().padStart(2, '0')}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun EmptyReceiptsContent(
    hasFilters: Boolean,
    onAddReceipt: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Receipt,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = if (hasFilters) "No receipts match your filters" else "No receipts yet",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = if (hasFilters) 
                "Try adjusting your search or filters" 
            else 
                "Scan or import receipts to track your purchases",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        
        if (!hasFilters) {
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(onClick = onAddReceipt) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Receipt")
            }
        }
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss) {
                Text("Dismiss")
            }
            
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}
