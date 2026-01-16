package com.ledgerlens.ui.screens.transactions

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.components.EmptyState
import com.ledgerlens.ui.components.ErrorState
import com.ledgerlens.ui.components.LoadingIndicator
import com.ledgerlens.ui.viewmodels.transactions.TransactionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    onNavigateBack: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isSelectionMode) "${uiState.selectedCount} selected" else "Transactions") },
                navigationIcon = {
                    IconButton(onClick = if (uiState.isSelectionMode) viewModel::toggleSelectionMode else onNavigateBack) {
                        Icon(if (uiState.isSelectionMode) Icons.Default.Close else Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (!uiState.isSelectionMode) {
                        TextButton(onClick = viewModel::toggleSelectionMode) { Text("Select") }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Search bar
            OutlinedTextField(
                value = uiState.filters.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search transactions...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (uiState.filters.searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Clear, "Clear")
                        }
                    }
                },
                singleLine = true
            )

            // Filter chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = uiState.filters.showIncomeOnly,
                        onClick = viewModel::toggleIncomeFilter,
                        label = { Text("Income") }
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.filters.showExpensesOnly,
                        onClick = viewModel::toggleExpensesFilter,
                        label = { Text("Expenses") }
                    )
                }
                items(uiState.availableCategories.take(5)) { category ->
                    FilterChip(
                        selected = category.id in uiState.filters.selectedCategories,
                        onClick = { viewModel.toggleCategoryFilter(category.id) },
                        label = { Text(category.name) }
                    )
                }
                if (uiState.filters.hasActiveFilters) {
                    item {
                        TextButton(onClick = viewModel::clearAllFilters) { Text("Clear all") }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Content
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { LoadingIndicator() }
                uiState.error != null -> ErrorState(title = "Error", message = uiState.error ?: "", onRetry = viewModel::loadTransactions)
                !uiState.hasTransactions -> EmptyState(title = "No Transactions", message = "No transactions match your filters")
                else -> {
                    val listState = rememberLazyListState()
                    val shouldLoadMore by remember {
                        derivedStateOf {
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            lastVisible >= uiState.transactions.size - 3
                        }
                    }
                    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) viewModel.loadMoreTransactions() }

                    PullToRefreshBox(isRefreshing = uiState.isRefreshing, onRefresh = viewModel::refreshTransactions) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.transactions, key = { it.id }) { tx ->
                                if (uiState.isSelectionMode) {
                                    SelectableTransactionItem(
                                        transaction = tx,
                                        selected = tx.id in uiState.selectedTransactions,
                                        onSelectionChange = { viewModel.toggleTransactionSelection(tx.id) },
                                        onClick = { onNavigateToDetail(tx.id) }
                                    )
                                } else {
                                    TransactionItem(transaction = tx, onClick = { onNavigateToDetail(tx.id) })
                                }
                            }
                            if (uiState.pagination.isLoadingMore) {
                                item { Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { LoadingIndicator() } }
                            }
                        }
                    }
                }
            }
        }
    }
}
