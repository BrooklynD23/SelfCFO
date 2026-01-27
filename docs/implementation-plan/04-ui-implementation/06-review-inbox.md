# 06: Review Inbox

## Overview

Implement the categorization review interface for handling low-confidence predictions, duplicates, and parse errors.

## Repo status note (2026-01-25)

This file is an **implementation plan**. The repository currently contains a working `ReviewInboxScreen` / `ReviewDetailScreen`, but there are important integration gaps:

- The Review UI currently loads from an **in-memory** `ReviewQueueManager`. Core persistence exists via `ReviewQueueRepository` (`ReviewQueue.sq` + SQLDelight repo), but `ReviewViewModel` is not wired to it yet.
- The current Import flow (`ImportViewModel`) is **simulated**. Core CSV import is implemented (`ImportService.importCsv`) and enqueues low-confidence items into the persisted `review_queue`, but UI wiring is pending.
- Cross-file duplicates are recorded as persisted `duplicate_candidate` rows (for later review), but there is no UI yet to surface/resolve them.
- OCR “needs review” is modeled in SQLDelight schema via `parse_status = 'needs_review'`, but there is no screen/query that surfaces these records for review yet.
- On Desktop, the Review screen previously showed “Coming Soon” because `Screen.Review` wasn’t registered in the desktop screen registry; this has been fixed.

See: `docs/audits/2026-01-25-REVIEW-INBOX-IMPORT-REVIEW-AUDIT.md`

---

## Implementation Steps

### Step 1: Review Inbox State

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/review/ReviewInboxState.kt
package com.ledgerlens.ui.screens.review

data class ReviewInboxState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val items: List<ReviewItemDisplay> = emptyList(),
    val groupedItems: Map<ReviewIssueType, List<ReviewItemDisplay>> = emptyMap(),
    val selectedFilter: ReviewFilter = ReviewFilter.ALL,
    val counts: Map<ReviewIssueType, Int> = emptyMap(),
    val selectedItems: Set<String> = emptySet(),
    val isBulkMode: Boolean = false
)

data class ReviewItemDisplay(
    val id: String,
    val transactionId: String,
    val merchantNormalized: String,
    val descriptionRaw: String,
    val amount: Money,
    val date: LocalDate,
    val issueType: ReviewIssueType,
    val suggestedCategory: CategorySuggestion?,
    val alternativeCategories: List<CategorySuggestion>,
    val confidence: Float
)

data class CategorySuggestion(
    val categoryId: String,
    val categoryName: String,
    val categoryIcon: String?,
    val categoryColor: String,
    val confidence: Float
)

data class ReviewItemDetailState(
    val isLoading: Boolean = true,
    val transaction: ReviewItemDisplay? = null,
    val explanation: String? = null,
    val allCategories: List<CategoryOption> = emptyList(),
    val recentCategories: List<CategoryOption> = emptyList(),
    val suggestedRule: SuggestedRule? = null,
    val similarTransactions: List<SimilarTransaction> = emptyList()
)

data class CategoryOption(
    val id: String,
    val name: String,
    val icon: String?,
    val color: String,
    val parentName: String?
)

data class SimilarTransaction(
    val id: String,
    val merchantNormalized: String,
    val amount: Money,
    val date: LocalDate,
    val categoryName: String?
)
```

### Step 2: Review Inbox ViewModel

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/viewmodel/ReviewInboxViewModel.kt
package com.ledgerlens.viewmodel

class ReviewInboxViewModel(
    private val reviewInboxService: ReviewInboxService,
    private val correctionHandler: CorrectionHandler,
    private val bulkActionsService: BulkActionsService
) : BaseViewModel() {

    private val _state = MutableStateFlow(ReviewInboxState())
    val state: StateFlow<ReviewInboxState> = _state.asStateFlow()

    init {
        loadItems()
    }

    fun setFilter(filter: ReviewFilter) {
        _state.update { it.copy(selectedFilter = filter) }
        loadItems()
    }

    fun refresh() {
        loadItems()
    }

    fun toggleBulkMode() {
        _state.update {
            it.copy(
                isBulkMode = !it.isBulkMode,
                selectedItems = emptySet()
            )
        }
    }

    fun toggleItemSelection(itemId: String) {
        _state.update { current ->
            val newSelection = if (itemId in current.selectedItems) {
                current.selectedItems - itemId
            } else {
                current.selectedItems + itemId
            }
            current.copy(selectedItems = newSelection)
        }
    }

    fun selectAll() {
        _state.update { current ->
            current.copy(selectedItems = current.items.map { it.id }.toSet())
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedItems = emptySet()) }
    }

    suspend fun bulkCategorize(categoryId: String): BulkActionResult {
        val transactionIds = _state.value.selectedItems
            .mapNotNull { itemId ->
                _state.value.items.find { it.id == itemId }?.transactionId
            }

        val result = bulkActionsService.bulkCategorize(transactionIds, categoryId)

        if (result.successCount > 0) {
            loadItems() // Refresh after bulk action
        }

        return result
    }

    suspend fun bulkMarkReviewed(): BulkActionResult {
        val transactionIds = _state.value.selectedItems
            .mapNotNull { itemId ->
                _state.value.items.find { it.id == itemId }?.transactionId
            }

        val result = bulkActionsService.bulkMarkReviewed(transactionIds)

        if (result.successCount > 0) {
            loadItems()
        }

        return result
    }

    private fun loadItems() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                val filter = _state.value.selectedFilter
                val items = reviewInboxService.getReviewItems(filter)
                val counts = reviewInboxService.getReviewCounts()

                val displayItems = items.map { it.toDisplay() }

                _state.update {
                    it.copy(
                        isLoading = false,
                        items = displayItems,
                        groupedItems = displayItems.groupBy { item -> item.issueType },
                        counts = counts
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = e.message)
                }
            }
        }
    }
}
```

### Step 3: Review Inbox Screen

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/review/ReviewInboxScreen.kt
package com.ledgerlens.ui.screens.review

@Composable
fun ReviewInboxScreen(
    onBack: () -> Unit,
    onReviewItem: (String) -> Unit,
    viewModel: ReviewInboxViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showCategoryPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ReviewInboxTopBar(
                isBulkMode = state.isBulkMode,
                selectedCount = state.selectedItems.size,
                onBack = onBack,
                onToggleBulkMode = viewModel::toggleBulkMode,
                onSelectAll = viewModel::selectAll,
                onClearSelection = viewModel::clearSelection
            )
        },
        bottomBar = {
            if (state.isBulkMode && state.selectedItems.isNotEmpty()) {
                BulkActionBar(
                    selectedCount = state.selectedItems.size,
                    onCategorize = { showCategoryPicker = true },
                    onMarkReviewed = {
                        viewModel.viewModelScope.launch {
                            viewModel.bulkMarkReviewed()
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Filter chips
            FilterChipsRow(
                selectedFilter = state.selectedFilter,
                counts = state.counts,
                onFilterChange = viewModel::setFilter
            )

            // Content
            when {
                state.isLoading -> LoadingState()
                state.error != null -> ErrorState(state.error!!, viewModel::refresh)
                state.items.isEmpty() -> EmptyReviewState()
                else -> ReviewItemsList(
                    items = state.items,
                    isBulkMode = state.isBulkMode,
                    selectedItems = state.selectedItems,
                    onItemClick = { item ->
                        if (state.isBulkMode) {
                            viewModel.toggleItemSelection(item.id)
                        } else {
                            onReviewItem(item.transactionId)
                        }
                    },
                    onItemLongClick = { item ->
                        if (!state.isBulkMode) {
                            viewModel.toggleBulkMode()
                            viewModel.toggleItemSelection(item.id)
                        }
                    }
                )
            }
        }
    }

    // Category picker dialog
    if (showCategoryPicker) {
        CategoryPickerDialog(
            onCategorySelected = { categoryId ->
                viewModel.viewModelScope.launch {
                    viewModel.bulkCategorize(categoryId)
                }
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false }
        )
    }
}

@Composable
private fun FilterChipsRow(
    selectedFilter: ReviewFilter,
    counts: Map<ReviewIssueType, Int>,
    onFilterChange: (ReviewFilter) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedFilter == ReviewFilter.ALL,
                onClick = { onFilterChange(ReviewFilter.ALL) },
                label = { Text("All (${counts.values.sum()})") }
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == ReviewFilter.LOW_CONFIDENCE,
                onClick = { onFilterChange(ReviewFilter.LOW_CONFIDENCE) },
                label = { Text("Low Confidence (${counts[ReviewIssueType.LOW_CONFIDENCE] ?: 0})") }
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == ReviewFilter.DUPLICATES,
                onClick = { onFilterChange(ReviewFilter.DUPLICATES) },
                label = { Text("Duplicates (${counts[ReviewIssueType.POSSIBLE_DUPLICATE] ?: 0})") }
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == ReviewFilter.PARSE_ERRORS,
                onClick = { onFilterChange(ReviewFilter.PARSE_ERRORS) },
                label = { Text("Errors (${counts[ReviewIssueType.PARSE_ERROR] ?: 0})") }
            )
        }
    }
}

@Composable
private fun ReviewItemsList(
    items: List<ReviewItemDisplay>,
    isBulkMode: Boolean,
    selectedItems: Set<String>,
    onItemClick: (ReviewItemDisplay) -> Unit,
    onItemLongClick: (ReviewItemDisplay) -> Unit
) {
    LazyColumn {
        items(items, key = { it.id }) { item ->
            ReviewItemRow(
                item = item,
                isSelected = item.id in selectedItems,
                isBulkMode = isBulkMode,
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) }
            )
        }
    }
}

@Composable
private fun ReviewItemRow(
    item: ReviewItemDisplay,
    isSelected: Boolean,
    isBulkMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else Color.Transparent
            ),
        headlineContent = {
            Text(item.merchantNormalized, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                Text(
                    text = item.date.formatShort(),
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IssueTypeChip(issueType = item.issueType)
                    if (item.suggestedCategory != null) {
                        Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = item.suggestedCategory.categoryName,
                            style = MaterialTheme.typography.labelSmall
                        )
                        ConfidenceIndicator(
                            confidence = item.suggestedCategory.confidence,
                            showLabel = false
                        )
                    }
                }
            }
        },
        leadingContent = {
            if (isBulkMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null
                )
            } else {
                IssueTypeIcon(issueType = item.issueType)
            }
        },
        trailingContent = {
            MoneyText(money = item.amount, style = MoneySmall, colorBySign = true)
        }
    )
}

@Composable
private fun BulkActionBar(
    selectedCount: Int,
    onCategorize: () -> Unit,
    onMarkReviewed: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(onClick = onMarkReviewed) {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.width(8.dp))
                Text("Keep ($selectedCount)")
            }
            Button(onClick = onCategorize) {
                Icon(Icons.Default.Category, null)
                Spacer(Modifier.width(8.dp))
                Text("Categorize")
            }
        }
    }
}
```

### Step 4: Review Item Detail Screen

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/review/ReviewItemScreen.kt
package com.ledgerlens.ui.screens.review

@Composable
fun ReviewItemScreen(
    transactionId: String,
    onBack: () -> Unit,
    onNextItem: (String) -> Unit,
    viewModel: ReviewItemViewModel = koinViewModel { parametersOf(transactionId) }
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            LoadingState(modifier = Modifier.padding(padding))
        } else {
            state.transaction?.let { transaction ->
                ReviewItemContent(
                    transaction = transaction,
                    explanation = state.explanation,
                    allCategories = state.allCategories,
                    recentCategories = state.recentCategories,
                    suggestedRule = state.suggestedRule,
                    similarTransactions = state.similarTransactions,
                    onCategorySelected = { categoryId ->
                        viewModel.categorize(categoryId)
                        // Navigate to next item or back
                    },
                    onSkip = onBack,
                    onCreateRule = { viewModel.createRule() },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun ReviewItemContent(
    transaction: ReviewItemDisplay,
    explanation: String?,
    allCategories: List<CategoryOption>,
    recentCategories: List<CategoryOption>,
    suggestedRule: SuggestedRule?,
    similarTransactions: List<SimilarTransaction>,
    onCategorySelected: (String) -> Unit,
    onSkip: () -> Unit,
    onCreateRule: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Transaction info
        item {
            TransactionInfoCard(transaction = transaction)
        }

        // Explanation
        explanation?.let {
            item {
                ExplanationCard(explanation = it)
            }
        }

        // Suggested category
        transaction.suggestedCategory?.let { suggestion ->
            item {
                SuggestedCategoryCard(
                    suggestion = suggestion,
                    alternatives = transaction.alternativeCategories,
                    onAccept = { onCategorySelected(suggestion.categoryId) },
                    onSelectAlternative = onCategorySelected
                )
            }
        }

        // Rule suggestion
        suggestedRule?.let { rule ->
            item {
                RuleSuggestionCard(
                    rule = rule,
                    onAccept = onCreateRule
                )
            }
        }

        // Recent categories
        if (recentCategories.isNotEmpty()) {
            item {
                Text("Recent Categories", style = MaterialTheme.typography.titleSmall)
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recentCategories.forEach { category ->
                        CategoryChip(
                            name = category.name,
                            icon = category.icon,
                            color = category.color,
                            onClick = { onCategorySelected(category.id) }
                        )
                    }
                }
            }
        }

        // All categories
        item {
            Text("All Categories", style = MaterialTheme.typography.titleSmall)
        }
        item {
            CategoryGrid(
                categories = allCategories,
                onCategorySelected = onCategorySelected
            )
        }

        // Skip button
        item {
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Skip for now")
            }
        }
    }
}
```

---

## Acceptance Criteria

- [ ] Review inbox shows items grouped by issue type
- [ ] Filter by issue type works
- [ ] Bulk selection mode with checkbox
- [ ] Bulk categorize multiple items
- [ ] Bulk mark as reviewed
- [ ] Individual item review with suggestions
- [ ] Category explanation shown
- [ ] Alternative categories shown
- [ ] Rule suggestion shown when pattern detected
- [ ] Quick category selection from recent/all
- [ ] Skip option available

---

## Estimated Complexity

**Medium** - List with bulk actions and category selection.

