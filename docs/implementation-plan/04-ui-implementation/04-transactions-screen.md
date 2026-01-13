# 04: Transactions Screen

## Overview

Implement the transaction list with search, filtering, sorting, and transaction detail/edit views.

---

## Implementation Steps

### Step 1: Transaction List State

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/transactions/TransactionsState.kt
package com.ledgerlens.ui.screens.transactions

data class TransactionsState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val transactions: List<TransactionListItem> = emptyList(),
    val groupedTransactions: Map<String, List<TransactionListItem>> = emptyMap(),
    val filter: TransactionFilter = TransactionFilter(),
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false
)

data class TransactionListItem(
    val id: String,
    val merchantNormalized: String,
    val descriptionRaw: String,
    val amount: Money,
    val date: LocalDate,
    val categoryId: String?,
    val categoryName: String?,
    val categoryIcon: String?,
    val categoryColor: String?,
    val confidence: Float?,
    val needsReview: Boolean,
    val isTransfer: Boolean
)

data class TransactionFilter(
    val dateRange: ClosedRange<LocalDate>? = null,
    val categoryIds: Set<String> = emptySet(),
    val accountIds: Set<String> = emptySet(),
    val amountMin: Long? = null,
    val amountMax: Long? = null,
    val showExpensesOnly: Boolean = false,
    val showIncomeOnly: Boolean = false,
    val showNeedsReview: Boolean = false
) {
    val isActive: Boolean
        get() = dateRange != null ||
                categoryIds.isNotEmpty() ||
                accountIds.isNotEmpty() ||
                amountMin != null ||
                amountMax != null ||
                showExpensesOnly ||
                showIncomeOnly ||
                showNeedsReview
}

enum class SortOrder(val label: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    AMOUNT_DESC("Highest Amount"),
    AMOUNT_ASC("Lowest Amount"),
    MERCHANT("Merchant A-Z")
}
```

### Step 2: Transactions ViewModel

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/viewmodel/TransactionsViewModel.kt
package com.ledgerlens.viewmodel

class TransactionsViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : BaseViewModel() {

    private val _state = MutableStateFlow(TransactionsState())
    val state: StateFlow<TransactionsState> = _state.asStateFlow()

    private var currentPage = 0
    private val pageSize = 50

    init {
        loadTransactions()
    }

    fun search(query: String) {
        _state.update { it.copy(searchQuery = query) }
        resetAndLoad()
    }

    fun setFilter(filter: TransactionFilter) {
        _state.update { it.copy(filter = filter) }
        resetAndLoad()
    }

    fun clearFilter() {
        _state.update { it.copy(filter = TransactionFilter()) }
        resetAndLoad()
    }

    fun setSortOrder(order: SortOrder) {
        _state.update { it.copy(sortOrder = order) }
        resetAndLoad()
    }

    fun loadMore() {
        if (_state.value.isLoadingMore || !_state.value.hasMore) return

        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }

            try {
                currentPage++
                val newTransactions = fetchTransactions(currentPage)

                _state.update { current ->
                    current.copy(
                        isLoadingMore = false,
                        transactions = current.transactions + newTransactions,
                        groupedTransactions = groupByDate(current.transactions + newTransactions),
                        hasMore = newTransactions.size == pageSize
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun refresh() {
        resetAndLoad()
    }

    private fun resetAndLoad() {
        currentPage = 0
        loadTransactions()
    }

    private fun loadTransactions() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                val transactions = fetchTransactions(0)

                _state.update {
                    it.copy(
                        isLoading = false,
                        transactions = transactions,
                        groupedTransactions = groupByDate(transactions),
                        hasMore = transactions.size == pageSize
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = e.message)
                }
            }
        }
    }

    private suspend fun fetchTransactions(page: Int): List<TransactionListItem> {
        val filter = _state.value.filter
        val query = _state.value.searchQuery
        val sort = _state.value.sortOrder

        val transactions = transactionRepository.query(
            searchQuery = query.takeIf { it.isNotBlank() },
            dateRange = filter.dateRange,
            categoryIds = filter.categoryIds.takeIf { it.isNotEmpty() },
            accountIds = filter.accountIds.takeIf { it.isNotEmpty() },
            amountMin = filter.amountMin,
            amountMax = filter.amountMax,
            needsReview = filter.showNeedsReview.takeIf { it },
            sortBy = sort.toDbSort(),
            limit = pageSize,
            offset = page * pageSize
        )

        return transactions.map { tx ->
            val category = tx.categoryId?.let { categoryRepository.getById(it) }
            TransactionListItem(
                id = tx.id,
                merchantNormalized = tx.merchantNormalized,
                descriptionRaw = tx.descriptionRaw,
                amount = Money(tx.amountMinorUnits, tx.currencyCode),
                date = tx.postedDate,
                categoryId = tx.categoryId,
                categoryName = category?.name,
                categoryIcon = category?.icon,
                categoryColor = category?.color,
                confidence = tx.categoryConfidence,
                needsReview = tx.needsReview,
                isTransfer = tx.isTransfer
            )
        }
    }

    private fun groupByDate(
        transactions: List<TransactionListItem>
    ): Map<String, List<TransactionListItem>> {
        return transactions.groupBy { tx ->
            tx.date.formatGroupHeader()
        }
    }
}
```

### Step 3: Transactions Screen

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/transactions/TransactionsScreen.kt
package com.ledgerlens.ui.screens.transactions

@Composable
fun TransactionsScreen(
    onTransactionClick: (String) -> Unit,
    onFilterClick: () -> Unit,
    viewModel: TransactionsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TransactionsTopBar(
                searchQuery = state.searchQuery,
                onSearchChange = viewModel::search,
                filter = state.filter,
                onFilterClick = { showFilterSheet = true },
                sortOrder = state.sortOrder,
                onSortClick = { showSortMenu = true }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            state.error != null -> ErrorState(
                message = state.error!!,
                onRetry = viewModel::refresh,
                modifier = Modifier.padding(padding)
            )
            state.transactions.isEmpty() -> EmptyTransactionsState(
                hasFilter = state.filter.isActive || state.searchQuery.isNotBlank(),
                onClearFilter = viewModel::clearFilter,
                modifier = Modifier.padding(padding)
            )
            else -> TransactionsList(
                groupedTransactions = state.groupedTransactions,
                onTransactionClick = onTransactionClick,
                onLoadMore = viewModel::loadMore,
                isLoadingMore = state.isLoadingMore,
                modifier = Modifier.padding(padding)
            )
        }
    }

    // Sort menu
    DropdownMenu(
        expanded = showSortMenu,
        onDismissRequest = { showSortMenu = false }
    ) {
        SortOrder.entries.forEach { order ->
            DropdownMenuItem(
                text = { Text(order.label) },
                leadingIcon = {
                    if (state.sortOrder == order) {
                        Icon(Icons.Default.Check, null)
                    }
                },
                onClick = {
                    viewModel.setSortOrder(order)
                    showSortMenu = false
                }
            )
        }
    }

    // Filter sheet
    if (showFilterSheet) {
        FilterBottomSheet(
            currentFilter = state.filter,
            onApply = { filter ->
                viewModel.setFilter(filter)
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false }
        )
    }
}

@Composable
private fun TransactionsTopBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    filter: TransactionFilter,
    onFilterClick: () -> Unit,
    sortOrder: SortOrder,
    onSortClick: () -> Unit
) {
    var isSearchActive by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            if (isSearchActive) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Search transactions") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text("Transactions")
            }
        },
        actions = {
            IconButton(onClick = { isSearchActive = !isSearchActive }) {
                Icon(
                    imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = "Search"
                )
            }
            BadgedBox(
                badge = {
                    if (filter.isActive) {
                        Badge { Text("!") }
                    }
                }
            ) {
                IconButton(onClick = onFilterClick) {
                    Icon(Icons.Default.FilterList, "Filter")
                }
            }
            IconButton(onClick = onSortClick) {
                Icon(Icons.Default.Sort, "Sort")
            }
        }
    )
}

@Composable
private fun TransactionsList(
    groupedTransactions: Map<String, List<TransactionListItem>>,
    onTransactionClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    isLoadingMore: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Detect when scrolled to bottom
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                val totalItems = listState.layoutInfo.totalItemsCount
                if (lastIndex != null && lastIndex >= totalItems - 5) {
                    onLoadMore()
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize()
    ) {
        groupedTransactions.forEach { (dateGroup, transactions) ->
            stickyHeader {
                DateGroupHeader(dateGroup)
            }

            items(
                items = transactions,
                key = { it.id }
            ) { transaction ->
                TransactionListItemRow(
                    item = transaction,
                    onClick = { onTransactionClick(transaction.id) }
                )
            }
        }

        if (isLoadingMore) {
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
    }
}

@Composable
private fun DateGroupHeader(dateGroup: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = dateGroup,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun TransactionListItemRow(
    item: TransactionListItem,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = item.merchantNormalized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.categoryName != null) {
                    CategoryChip(
                        name = item.categoryName,
                        icon = item.categoryIcon,
                        color = item.categoryColor
                    )
                } else {
                    Text(
                        text = "Uncategorized",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.needsReview) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Needs review",
                        tint = Pending,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        trailingContent = {
            MoneyText(
                money = item.amount,
                style = MoneySmall,
                colorBySign = true
            )
        },
        leadingContent = {
            if (item.isTransfer) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = "Transfer",
                    tint = Transfer
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
```

### Step 4: Filter Bottom Sheet

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/transactions/FilterBottomSheet.kt
package com.ledgerlens.ui.screens.transactions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    currentFilter: TransactionFilter,
    onApply: (TransactionFilter) -> Unit,
    onDismiss: () -> Unit
) {
    var filter by remember { mutableStateOf(currentFilter) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Filter Transactions",
                style = MaterialTheme.typography.titleLarge
            )

            // Date range
            DateRangeSelector(
                dateRange = filter.dateRange,
                onDateRangeChange = { filter = filter.copy(dateRange = it) }
            )

            // Amount range
            AmountRangeSelector(
                min = filter.amountMin,
                max = filter.amountMax,
                onRangeChange = { min, max ->
                    filter = filter.copy(amountMin = min, amountMax = max)
                }
            )

            // Transaction type
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filter.showExpensesOnly,
                    onClick = {
                        filter = filter.copy(
                            showExpensesOnly = !filter.showExpensesOnly,
                            showIncomeOnly = false
                        )
                    },
                    label = { Text("Expenses Only") }
                )
                FilterChip(
                    selected = filter.showIncomeOnly,
                    onClick = {
                        filter = filter.copy(
                            showIncomeOnly = !filter.showIncomeOnly,
                            showExpensesOnly = false
                        )
                    },
                    label = { Text("Income Only") }
                )
            }

            // Needs review
            FilterChip(
                selected = filter.showNeedsReview,
                onClick = {
                    filter = filter.copy(showNeedsReview = !filter.showNeedsReview)
                },
                label = { Text("Needs Review") },
                leadingIcon = {
                    if (filter.showNeedsReview) {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                    }
                }
            )

            // Category selector (expandable)
            CategorySelector(
                selectedIds = filter.categoryIds,
                onSelectionChange = { filter = filter.copy(categoryIds = it) }
            )

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { filter = TransactionFilter() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear")
                }
                Button(
                    onClick = { onApply(filter) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Apply")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
```

---

## Acceptance Criteria

- [ ] Transaction list displays with grouping by date
- [ ] Search filters by merchant/description
- [ ] Multiple filter options work
- [ ] Sort order changes list order
- [ ] Infinite scroll pagination works
- [ ] Pull-to-refresh works
- [ ] Empty state with clear filter option
- [ ] Transaction details navigable
- [ ] Category chips show correctly
- [ ] Needs review indicator visible

---

## Estimated Complexity

**High** - List with filtering, sorting, search, and pagination.

