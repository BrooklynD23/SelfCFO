package com.ledgerlens.ui.viewmodels.transactions

import com.ledgerlens.categorization.Category
import com.ledgerlens.domain.Money
import com.ledgerlens.ui.screens.dashboard.DateRange
import com.ledgerlens.ui.screens.dashboard.TransactionUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Filter state for transactions list.
 */
data class TransactionFilters(
    val searchQuery: String = "",
    val dateRange: DateRange? = null,
    val selectedCategories: Set<String> = emptySet(),
    val showIncomeOnly: Boolean = false,
    val showExpensesOnly: Boolean = false,
    val showNeedsReview: Boolean = false,
    val minAmount: Money? = null,
    val maxAmount: Money? = null
) {
    val hasActiveFilters: Boolean
        get() = searchQuery.isNotBlank() ||
                dateRange != null ||
                selectedCategories.isNotEmpty() ||
                showIncomeOnly ||
                showExpensesOnly ||
                showNeedsReview ||
                minAmount != null ||
                maxAmount != null

    val activeFilterCount: Int
        get() = listOf(
            searchQuery.isNotBlank(),
            dateRange != null,
            selectedCategories.isNotEmpty(),
            showIncomeOnly,
            showExpensesOnly,
            showNeedsReview,
            minAmount != null,
            maxAmount != null
        ).count { it }
}

/**
 * Pagination state for transactions list.
 */
data class PaginationState(
    val currentPage: Int = 0,
    val pageSize: Int = 20,
    val totalItems: Int = 0,
    val isLoadingMore: Boolean = false,
    val hasMorePages: Boolean = true
) {
    val totalPages: Int get() = if (pageSize > 0) (totalItems + pageSize - 1) / pageSize else 0
    val canLoadMore: Boolean get() = hasMorePages && !isLoadingMore
}

/**
 * UI state for the Transactions screen.
 */
data class TransactionsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val transactions: List<TransactionUiModel> = emptyList(),
    val availableCategories: List<Category> = emptyList(),
    val filters: TransactionFilters = TransactionFilters(),
    val pagination: PaginationState = PaginationState(),
    val selectedTransactions: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val error: String? = null
) {
    val hasTransactions: Boolean get() = transactions.isNotEmpty()
    val filteredCount: Int get() = transactions.size
    val selectedCount: Int get() = selectedTransactions.size
    val hasSelection: Boolean get() = selectedTransactions.isNotEmpty()
}

/**
 * UI state for transaction detail screen.
 */
data class TransactionDetailUiState(
    val isLoading: Boolean = true,
    val transaction: TransactionUiModel? = null,
    val availableCategories: List<Category> = emptyList(),
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

/**
 * Events emitted by the Transactions screen.
 */
sealed class TransactionsEvent {
    data class NavigateToDetail(val transactionId: String) : TransactionsEvent()
    object NavigateBack : TransactionsEvent()
    data class ShowSnackbar(val message: String) : TransactionsEvent()
}

/**
 * ViewModel for the Transactions screen.
 * Provides transaction list with filtering, search, and pagination.
 */
class TransactionsViewModel(
    // TODO: Inject actual dependencies when available
    // private val transactionRepository: TransactionRepository,
    // private val categoryRepository: CategoryRepository
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    private val _detailState = MutableStateFlow(TransactionDetailUiState())
    val detailState: StateFlow<TransactionDetailUiState> = _detailState.asStateFlow()

    private val _events = MutableStateFlow<TransactionsEvent?>(null)
    val events: StateFlow<TransactionsEvent?> = _events.asStateFlow()

    private var allTransactions: List<TransactionUiModel> = emptyList()

    init {
        loadTransactions()
        loadCategories()
    }

    fun loadTransactions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                delay(400) // Simulated delay
                allTransactions = generateMockTransactions()
                applyFiltersAndPagination()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load transactions"
                    )
                }
            }
        }
    }

    fun refreshTransactions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }

            try {
                delay(300)
                allTransactions = generateMockTransactions()
                applyFiltersAndPagination()
                _uiState.update { it.copy(isRefreshing = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        error = e.message ?: "Failed to refresh"
                    )
                }
            }
        }
    }

    fun loadMoreTransactions() {
        if (!_uiState.value.pagination.canLoadMore) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(pagination = it.pagination.copy(isLoadingMore = true))
            }

            delay(300) // Simulated delay

            val currentState = _uiState.value
            val nextPage = currentState.pagination.currentPage + 1
            val startIndex = nextPage * currentState.pagination.pageSize
            val endIndex = minOf(startIndex + currentState.pagination.pageSize, allTransactions.size)

            if (startIndex < allTransactions.size) {
                val newTransactions = applyFilters(allTransactions).subList(
                    0,
                    minOf(endIndex, applyFilters(allTransactions).size)
                )

                _uiState.update {
                    it.copy(
                        transactions = newTransactions,
                        pagination = it.pagination.copy(
                            currentPage = nextPage,
                            isLoadingMore = false,
                            hasMorePages = endIndex < applyFilters(allTransactions).size
                        )
                    )
                }
            } else {
                _uiState.update {
                    it.copy(pagination = it.pagination.copy(isLoadingMore = false, hasMorePages = false))
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update {
            it.copy(filters = it.filters.copy(searchQuery = query))
        }
        applyFiltersAndPagination()
    }

    fun updateDateRange(dateRange: DateRange?) {
        _uiState.update {
            it.copy(filters = it.filters.copy(dateRange = dateRange))
        }
        applyFiltersAndPagination()
    }

    fun toggleCategoryFilter(categoryId: String) {
        _uiState.update { state ->
            val currentCategories = state.filters.selectedCategories
            val newCategories = if (categoryId in currentCategories) {
                currentCategories - categoryId
            } else {
                currentCategories + categoryId
            }
            state.copy(filters = state.filters.copy(selectedCategories = newCategories))
        }
        applyFiltersAndPagination()
    }

    fun setCategoryFilters(categoryIds: Set<String>) {
        _uiState.update {
            it.copy(filters = it.filters.copy(selectedCategories = categoryIds))
        }
        applyFiltersAndPagination()
    }

    fun toggleIncomeFilter() {
        _uiState.update {
            it.copy(
                filters = it.filters.copy(
                    showIncomeOnly = !it.filters.showIncomeOnly,
                    showExpensesOnly = false
                )
            )
        }
        applyFiltersAndPagination()
    }

    fun toggleExpensesFilter() {
        _uiState.update {
            it.copy(
                filters = it.filters.copy(
                    showExpensesOnly = !it.filters.showExpensesOnly,
                    showIncomeOnly = false
                )
            )
        }
        applyFiltersAndPagination()
    }

    fun toggleNeedsReviewFilter() {
        _uiState.update {
            it.copy(filters = it.filters.copy(showNeedsReview = !it.filters.showNeedsReview))
        }
        applyFiltersAndPagination()
    }

    fun clearAllFilters() {
        _uiState.update {
            it.copy(filters = TransactionFilters())
        }
        applyFiltersAndPagination()
    }

    fun toggleSelectionMode() {
        _uiState.update {
            it.copy(
                isSelectionMode = !it.isSelectionMode,
                selectedTransactions = emptySet()
            )
        }
    }

    fun toggleTransactionSelection(transactionId: String) {
        _uiState.update { state ->
            val currentSelection = state.selectedTransactions
            val newSelection = if (transactionId in currentSelection) {
                currentSelection - transactionId
            } else {
                currentSelection + transactionId
            }
            state.copy(selectedTransactions = newSelection)
        }
    }

    fun selectAllTransactions() {
        _uiState.update { state ->
            state.copy(selectedTransactions = state.transactions.map { it.id }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedTransactions = emptySet()) }
    }

    fun onTransactionClicked(transactionId: String) {
        if (_uiState.value.isSelectionMode) {
            toggleTransactionSelection(transactionId)
        } else {
            _events.value = TransactionsEvent.NavigateToDetail(transactionId)
        }
    }

    fun clearEvent() {
        _events.value = null
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    // Transaction Detail methods

    fun loadTransactionDetail(transactionId: String) {
        viewModelScope.launch {
            _detailState.update { it.copy(isLoading = true, error = null) }

            try {
                delay(200)
                val transaction = allTransactions.find { it.id == transactionId }
                    ?: generateMockTransactions().find { it.id == transactionId }

                _detailState.update {
                    it.copy(
                        isLoading = false,
                        transaction = transaction,
                        availableCategories = generateMockCategories()
                    )
                }
            } catch (e: Exception) {
                _detailState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load transaction"
                    )
                }
            }
        }
    }

    fun startEditing() {
        _detailState.update { it.copy(isEditing = true) }
    }

    fun cancelEditing() {
        _detailState.update { it.copy(isEditing = false) }
    }

    fun updateTransactionCategory(categoryId: String) {
        viewModelScope.launch {
            _detailState.update { it.copy(isSaving = true) }

            try {
                delay(300) // Simulated save
                val newCategory = _detailState.value.availableCategories.find { it.id == categoryId }
                _detailState.update { state ->
                    state.copy(
                        isSaving = false,
                        isEditing = false,
                        transaction = state.transaction?.copy(category = newCategory)
                    )
                }
                _events.value = TransactionsEvent.ShowSnackbar("Category updated")
            } catch (e: Exception) {
                _detailState.update {
                    it.copy(
                        isSaving = false,
                        error = e.message ?: "Failed to update category"
                    )
                }
            }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            _uiState.update { it.copy(availableCategories = generateMockCategories()) }
        }
    }

    private fun applyFiltersAndPagination() {
        val filtered = applyFilters(allTransactions)
        val pageSize = _uiState.value.pagination.pageSize
        val paged = filtered.take(pageSize)

        _uiState.update {
            it.copy(
                isLoading = false,
                transactions = paged,
                pagination = PaginationState(
                    currentPage = 0,
                    pageSize = pageSize,
                    totalItems = filtered.size,
                    hasMorePages = filtered.size > pageSize
                )
            )
        }
    }

    private fun applyFilters(transactions: List<TransactionUiModel>): List<TransactionUiModel> {
        val filters = _uiState.value.filters
        return transactions.filter { transaction ->
            // Search query filter
            val matchesSearch = filters.searchQuery.isBlank() ||
                    transaction.displayMerchant.contains(filters.searchQuery, ignoreCase = true) ||
                    transaction.description.contains(filters.searchQuery, ignoreCase = true)

            // Category filter
            val matchesCategory = filters.selectedCategories.isEmpty() ||
                    transaction.category?.id in filters.selectedCategories

            // Income/Expense filter
            val matchesType = when {
                filters.showIncomeOnly -> transaction.isIncome
                filters.showExpensesOnly -> transaction.isExpense
                else -> true
            }

            // Needs review filter
            val matchesReview = !filters.showNeedsReview || transaction.needsReview

            matchesSearch && matchesCategory && matchesType && matchesReview
        }
    }

    private fun generateMockCategories(): List<Category> = listOf(
        Category(id = "food", name = "Food & Dining", color = "#FF5722"),
        Category(id = "food_groceries", name = "Groceries", parentId = "food", color = "#8BC34A"),
        Category(id = "food_restaurants", name = "Restaurants", parentId = "food", color = "#FF7043"),
        Category(id = "food_coffee", name = "Coffee & Tea", parentId = "food", color = "#6F4E37"),
        Category(id = "transport", name = "Transportation", color = "#2196F3"),
        Category(id = "transport_gas", name = "Gas & Fuel", parentId = "transport", color = "#795548"),
        Category(id = "transport_parking", name = "Parking", parentId = "transport", color = "#607D8B"),
        Category(id = "shopping", name = "Shopping", color = "#FF9800"),
        Category(id = "bills", name = "Bills & Utilities", color = "#9C27B0"),
        Category(id = "entertainment", name = "Entertainment", color = "#E91E63"),
        Category(id = "health", name = "Health", color = "#4CAF50"),
        Category(id = "income", name = "Income", isSystemDefault = true, color = "#4CAF50"),
        Category(id = "transfer", name = "Transfer", isSystemDefault = true, color = "#9E9E9E"),
        Category(id = "uncategorized", name = "Uncategorized", isSystemDefault = true, color = "#757575")
    )

    private fun generateMockTransactions(): List<TransactionUiModel> = listOf(
        TransactionUiModel(
            id = "1",
            date = "Jan 15, 2026",
            merchantName = "STARBUCKS STORE 12345",
            normalizedMerchant = "Starbucks",
            description = "Coffee purchase",
            amount = Money.fromMinorUnits(-575, "USD"),
            category = Category(id = "food_coffee", name = "Coffee & Tea", parentId = "food", color = "#6F4E37"),
            categoryConfidence = 0.95f,
            accountName = "Chase Checking"
        ),
        TransactionUiModel(
            id = "2",
            date = "Jan 14, 2026",
            merchantName = "AMAZON.COM*123456",
            normalizedMerchant = "Amazon",
            description = "Online purchase",
            amount = Money.fromMinorUnits(-4299, "USD"),
            category = Category(id = "shopping", name = "Shopping", color = "#FF9800"),
            categoryConfidence = 0.88f,
            accountName = "Chase Credit"
        ),
        TransactionUiModel(
            id = "3",
            date = "Jan 13, 2026",
            merchantName = "SHELL OIL 12345678",
            normalizedMerchant = "Shell",
            description = "Gas station",
            amount = Money.fromMinorUnits(-5234, "USD"),
            category = Category(id = "transport_gas", name = "Gas & Fuel", parentId = "transport", color = "#795548"),
            categoryConfidence = 0.92f,
            hasReceipt = true
        ),
        TransactionUiModel(
            id = "4",
            date = "Jan 12, 2026",
            merchantName = "PAYROLL DEPOSIT",
            normalizedMerchant = "Payroll",
            description = "Direct deposit - ACME Corp",
            amount = Money.fromMinorUnits(250000, "USD"),
            category = Category(id = "income", name = "Income", color = "#4CAF50"),
            categoryConfidence = 1.0f
        ),
        TransactionUiModel(
            id = "5",
            date = "Jan 11, 2026",
            merchantName = "WHOLEFDS MKT 10234",
            normalizedMerchant = "Whole Foods",
            description = "Grocery shopping",
            amount = Money.fromMinorUnits(-8756, "USD"),
            category = Category(id = "food_groceries", name = "Groceries", parentId = "food", color = "#8BC34A"),
            categoryConfidence = 0.91f,
            hasReceipt = true
        ),
        TransactionUiModel(
            id = "6",
            date = "Jan 10, 2026",
            merchantName = "NETFLIX.COM",
            normalizedMerchant = "Netflix",
            description = "Monthly subscription",
            amount = Money.fromMinorUnits(-1599, "USD"),
            category = Category(id = "entertainment", name = "Entertainment", color = "#E91E63"),
            categoryConfidence = 0.98f
        ),
        TransactionUiModel(
            id = "7",
            date = "Jan 9, 2026",
            merchantName = "UBER TRIP HELP.UBER.COM",
            normalizedMerchant = "Uber",
            description = "Ride share",
            amount = Money.fromMinorUnits(-2347, "USD"),
            category = Category(id = "transport", name = "Transportation", color = "#2196F3"),
            categoryConfidence = 0.89f
        ),
        TransactionUiModel(
            id = "8",
            date = "Jan 8, 2026",
            merchantName = "COSTCO WHSE #1234",
            normalizedMerchant = "Costco",
            description = "Wholesale shopping",
            amount = Money.fromMinorUnits(-15678, "USD"),
            category = null,
            needsReview = true
        ),
        TransactionUiModel(
            id = "9",
            date = "Jan 7, 2026",
            merchantName = "CVS/PHARM 12345",
            normalizedMerchant = "CVS Pharmacy",
            description = "Pharmacy",
            amount = Money.fromMinorUnits(-2345, "USD"),
            category = Category(id = "health", name = "Health", color = "#4CAF50"),
            categoryConfidence = 0.85f
        ),
        TransactionUiModel(
            id = "10",
            date = "Jan 6, 2026",
            merchantName = "VENMO PAYMENT",
            normalizedMerchant = "Venmo",
            description = "Payment to friend",
            amount = Money.fromMinorUnits(-5000, "USD"),
            category = Category(id = "transfer", name = "Transfer", color = "#9E9E9E"),
            categoryConfidence = 0.75f,
            needsReview = true
        ),
        TransactionUiModel(
            id = "11",
            date = "Jan 5, 2026",
            merchantName = "TARGET #1234",
            normalizedMerchant = "Target",
            description = "General merchandise",
            amount = Money.fromMinorUnits(-6789, "USD"),
            category = Category(id = "shopping", name = "Shopping", color = "#FF9800"),
            categoryConfidence = 0.82f
        ),
        TransactionUiModel(
            id = "12",
            date = "Jan 4, 2026",
            merchantName = "ELECTRIC COMPANY",
            normalizedMerchant = "Electric Company",
            description = "Utility bill",
            amount = Money.fromMinorUnits(-14523, "USD"),
            category = Category(id = "bills", name = "Bills & Utilities", color = "#9C27B0"),
            categoryConfidence = 0.94f
        )
    )
}
