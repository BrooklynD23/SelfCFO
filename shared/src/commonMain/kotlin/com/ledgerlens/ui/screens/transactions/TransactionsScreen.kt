package com.ledgerlens.ui.screens.transactions

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.components.EmptyState
import com.ledgerlens.ui.components.ErrorState
import com.ledgerlens.ui.components.LoadingIndicator
import com.ledgerlens.ui.components.SelectableCategoryChip
import com.ledgerlens.ui.viewmodels.transactions.TransactionsUiState
import com.ledgerlens.ui.viewmodels.transactions.TransactionsViewModel

/**
 * Transactions screen with list, search, and filtering.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    onNavigateBack: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSearchBar by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (uiState.isSelectionMode) {
                SelectionModeTopBar(
                    selectedCount = uiState.selectedCount,
                    onClearSelection = viewModel::clearSelection,
                    onSelectAll = viewModel::selectAllTransactions,
                    onExitSelectionMode = viewModel::toggleSelectionMode
                )
            } else {
                TransactionsTopBar(
                    onNavigateBack = onNavigateBack,
                    onSearchClick = { showSearchBar = true },
                    onFilterClick = { /* TODO: Show filter dialog */ },
                    hasActiveFilters = uiState.filters.hasActiveFilters,
                    activeFilterCount = uiState.filters.activeFilterCount
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar (expandable)
            if (showSearchBar) {
                SearchBarSection(
                    query = uiState.filters.searchQuery,
                    onQueryChange = viewModel::updateSearchQuery,
                    onClose = {
                        showSearchBar = false
                        viewModel.updateSearchQuery("")
                    }
                )
            }

            // Filter chips
            if (uiState.filters.hasActiveFilters || uiState.availableCategories.isNotEmpty()) {
                FilterChipsRow(
                    uiState = uiState,
                    onToggleCategory = viewModel::toggleCategoryFilter,
                    onToggleIncome = viewModel::toggleIncomeFilter,
                    onToggleExpenses = viewModel::toggleExpensesFilter,
                    onToggleNeedsReview = viewModel::toggleNeedsReviewFilter,
                    onClearFilters = viewModel::clearAllFilters
                )
            }

            // Main content
            TransactionsContent(
                uiState = uiState,
                onRefresh = viewModel::refreshTransactions,
                onLoadMore = viewModel::loadMoreTransactions,
                onTransactionClick = { id ->
                    viewModel.onTransactionClicked(id)
                    if (!uiState.isSelectionMode) {
                        onNavigateToDetail(id)
                    }
                },
                onRetry = viewModel::loadTransactions,
                onDismissError = viewModel::dismissError
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionsTopBar(
    onNavigateBack: () -> Unit,
    onSearchClick: () -> Unit,
    onFilterClick: () -> Unit,
    hasActiveFilters: Boolean,
    activeFilterCount: Int
) {
    TopAppBar(
        title = { Text("Transactions") },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            }

            if (hasActiveFilters) {
                BadgedBox(
                    badge = {
                        Badge { Text(activeFilterCount.toString()) }
                    }
                ) {
                    IconButton(onClick = onFilterClick) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter"
                        )
                    }
                }
            } else {
                IconButton(onClick = onFilterClick) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filter"
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionModeTopBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onExitSelectionMode: () -> Unit
) {
    TopAppBar(
        title = { Text("$selectedCount selected") },
        navigationIcon = {
            IconButton(onClick = onExitSelectionMode) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Exit selection mode"
                )
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = "Select all"
                )
            }
            if (selectedCount > 0) {
                IconButton(onClick = onClearSelection) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear selection"
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBarSection(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // TODO: Replace with LedgerLensSearchBar when available from Agent 1
        androidx.compose.material3.TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Search transactions...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null
                )
            },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear"
                        )
                    }
                }
            },
            singleLine = true
        )

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close search"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipsRow(
    uiState: TransactionsUiState,
    onToggleCategory: (String) -> Unit,
    onToggleIncome: () -> Unit,
    onToggleExpenses: () -> Unit,
    onToggleNeedsReview: () -> Unit,
    onClearFilters: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Quick filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = uiState.filters.showIncomeOnly,
                onClick = onToggleIncome,
                label = { Text("Income") }
            )

            FilterChip(
                selected = uiState.filters.showExpensesOnly,
                onClick = onToggleExpenses,
                label = { Text("Expenses") }
            )

            FilterChip(
                selected = uiState.filters.showNeedsReview,
                onClick = onToggleNeedsReview,
                label = { Text("Needs Review") }
            )

            if (uiState.filters.hasActiveFilters) {
                TextButton(onClick = onClearFilters) {
                    Text("Clear All")
                }
            }
        }

        // Category chips (horizontal scroll)
        if (uiState.availableCategories.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = uiState.availableCategories.filter { it.parentId == null },
                    key = { it.id }
                ) { category ->
                    SelectableCategoryChip(
                        categoryName = category.name,
                        selected = category.id in uiState.filters.selectedCategories,
                        onClick = { onToggleCategory(category.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionsContent(
    uiState: TransactionsUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onTransactionClick: (String) -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit
) {
    when {
        uiState.isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator(message = "Loading transactions...")
            }
        }

        uiState.error != null && !uiState.hasTransactions -> {
            ErrorState(
                title = "Unable to Load Transactions",
                message = uiState.error,
                onRetry = onRetry,
                onDismiss = onDismissError,
                modifier = Modifier.fillMaxSize()
            )
        }

        !uiState.hasTransactions -> {
            EmptyState(
                title = if (uiState.filters.hasActiveFilters) {
                    "No Matching Transactions"
                } else {
                    "No Transactions Yet"
                },
                description = if (uiState.filters.hasActiveFilters) {
                    "Try adjusting your filters"
                } else {
                    "Import your bank statements to get started"
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        else -> {
            val listState = rememberLazyListState()

            // Load more when reaching end of list
            val shouldLoadMore by remember {
                derivedStateOf {
                    val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    val totalItems = listState.layoutInfo.totalItemsCount
                    lastVisibleItem >= totalItems - 3
                }
            }

            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore && uiState.pagination.canLoadMore) {
                    onLoadMore()
                }
            }

            // Pull to refresh with Box fallback for compatibility
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Refresh indicator at top when refreshing
                    if (uiState.isRefreshing) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    // Results count header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${uiState.pagination.totalItems} transactions",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Transaction items
                    items(
                        items = uiState.transactions,
                        key = { it.id }
                    ) { transaction ->
                        if (uiState.isSelectionMode) {
                            SelectableTransactionItem(
                                transaction = transaction,
                                selected = transaction.id in uiState.selectedTransactions,
                                onSelectionChange = { /* Handled by click */ },
                                onClick = { onTransactionClick(transaction.id) }
                            )
                        } else {
                            TransactionItem(
                                transaction = transaction,
                                onClick = { onTransactionClick(transaction.id) }
                            )
                        }
                    }

                    // Loading more indicator
                    if (uiState.pagination.isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    // End of list indicator
                    if (!uiState.pagination.hasMorePages && uiState.transactions.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "End of transactions",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
