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

data class TransactionFilters(
    val searchQuery: String = "",
    val dateRange: DateRange? = null,
    val selectedCategories: Set<String> = emptySet(),
    val showIncomeOnly: Boolean = false,
    val showExpensesOnly: Boolean = false,
    val showNeedsReview: Boolean = false
) {
    val hasActiveFilters: Boolean
        get() = searchQuery.isNotBlank() || dateRange != null || selectedCategories.isNotEmpty() ||
                showIncomeOnly || showExpensesOnly || showNeedsReview
}

data class PaginationState(
    val currentPage: Int = 0,
    val pageSize: Int = 20,
    val totalItems: Int = 0,
    val isLoadingMore: Boolean = false,
    val hasMorePages: Boolean = true
) {
    val canLoadMore: Boolean get() = hasMorePages && !isLoadingMore
}

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
    val selectedCount: Int get() = selectedTransactions.size
}

data class TransactionDetailUiState(
    val isLoading: Boolean = true,
    val transaction: TransactionUiModel? = null,
    val availableCategories: List<Category> = emptyList(),
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

sealed class TransactionsEvent {
    data class NavigateToDetail(val transactionId: String) : TransactionsEvent()
    object NavigateBack : TransactionsEvent()
    data class ShowSnackbar(val message: String) : TransactionsEvent()
}

class TransactionsViewModel {
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
    }

    fun loadTransactions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                delay(400)
                allTransactions = generateMockTransactions()
                _uiState.update { it.copy(availableCategories = generateMockCategories()) }
                applyFiltersAndPagination()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun refreshTransactions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            delay(300)
            allTransactions = generateMockTransactions()
            applyFiltersAndPagination()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun loadMoreTransactions() {
        if (!_uiState.value.pagination.canLoadMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(pagination = it.pagination.copy(isLoadingMore = true)) }
            delay(300)
            val filtered = applyFilters(allTransactions)
            val nextPage = _uiState.value.pagination.currentPage + 1
            val endIndex = minOf((nextPage + 1) * _uiState.value.pagination.pageSize, filtered.size)
            _uiState.update {
                it.copy(
                    transactions = filtered.take(endIndex),
                    pagination = it.pagination.copy(
                        currentPage = nextPage,
                        isLoadingMore = false,
                        hasMorePages = endIndex < filtered.size
                    )
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(filters = it.filters.copy(searchQuery = query)) }
        applyFiltersAndPagination()
    }

    fun toggleCategoryFilter(categoryId: String) {
        _uiState.update { state ->
            val cats = state.filters.selectedCategories
            state.copy(filters = state.filters.copy(
                selectedCategories = if (categoryId in cats) cats - categoryId else cats + categoryId
            ))
        }
        applyFiltersAndPagination()
    }

    fun toggleIncomeFilter() {
        _uiState.update { it.copy(filters = it.filters.copy(showIncomeOnly = !it.filters.showIncomeOnly, showExpensesOnly = false)) }
        applyFiltersAndPagination()
    }

    fun toggleExpensesFilter() {
        _uiState.update { it.copy(filters = it.filters.copy(showExpensesOnly = !it.filters.showExpensesOnly, showIncomeOnly = false)) }
        applyFiltersAndPagination()
    }

    fun clearAllFilters() {
        _uiState.update { it.copy(filters = TransactionFilters()) }
        applyFiltersAndPagination()
    }

    fun toggleSelectionMode() {
        _uiState.update { it.copy(isSelectionMode = !it.isSelectionMode, selectedTransactions = emptySet()) }
    }

    fun toggleTransactionSelection(transactionId: String) {
        _uiState.update { state ->
            val sel = state.selectedTransactions
            state.copy(selectedTransactions = if (transactionId in sel) sel - transactionId else sel + transactionId)
        }
    }

    fun onTransactionClicked(transactionId: String) {
        if (_uiState.value.isSelectionMode) toggleTransactionSelection(transactionId)
        else _events.value = TransactionsEvent.NavigateToDetail(transactionId)
    }

    fun clearEvent() { _events.value = null }
    fun dismissError() { _uiState.update { it.copy(error = null) } }

    fun loadTransactionDetail(transactionId: String) {
        viewModelScope.launch {
            _detailState.update { it.copy(isLoading = true) }
            delay(200)
            val tx = allTransactions.find { it.id == transactionId }
            _detailState.update { it.copy(isLoading = false, transaction = tx, availableCategories = generateMockCategories()) }
        }
    }

    fun startEditing() { _detailState.update { it.copy(isEditing = true) } }
    fun cancelEditing() { _detailState.update { it.copy(isEditing = false) } }

    fun updateTransactionCategory(categoryId: String) {
        viewModelScope.launch {
            _detailState.update { it.copy(isSaving = true) }
            delay(300)
            val cat = _detailState.value.availableCategories.find { it.id == categoryId }
            _detailState.update { it.copy(isSaving = false, isEditing = false, transaction = it.transaction?.copy(category = cat)) }
            _events.value = TransactionsEvent.ShowSnackbar("Category updated")
        }
    }

    private fun applyFiltersAndPagination() {
        val filtered = applyFilters(allTransactions)
        val pageSize = _uiState.value.pagination.pageSize
        _uiState.update {
            it.copy(
                isLoading = false,
                transactions = filtered.take(pageSize),
                pagination = PaginationState(totalItems = filtered.size, hasMorePages = filtered.size > pageSize)
            )
        }
    }

    private fun applyFilters(transactions: List<TransactionUiModel>): List<TransactionUiModel> {
        val f = _uiState.value.filters
        return transactions.filter { tx ->
            (f.searchQuery.isBlank() || tx.displayMerchant.contains(f.searchQuery, true) || tx.description.contains(f.searchQuery, true)) &&
            (f.selectedCategories.isEmpty() || tx.category?.id in f.selectedCategories) &&
            (!f.showIncomeOnly || tx.isIncome) && (!f.showExpensesOnly || tx.isExpense) &&
            (!f.showNeedsReview || tx.needsReview)
        }
    }

    private fun generateMockCategories() = listOf(
        Category(id = "food", name = "Food & Dining", color = "#FF5722"),
        Category(id = "transport", name = "Transportation", color = "#2196F3"),
        Category(id = "shopping", name = "Shopping", color = "#FF9800"),
        Category(id = "entertainment", name = "Entertainment", color = "#E91E63"),
        Category(id = "income", name = "Income", isSystemDefault = true, color = "#4CAF50")
    )

    private fun generateMockTransactions() = listOf(
        TransactionUiModel("1", "Jan 15, 2026", "STARBUCKS", "Starbucks", "Coffee", Money.fromMinorUnits(-575, "USD"),
            Category(id = "food", name = "Food & Dining", color = "#FF5722"), 0.95f),
        TransactionUiModel("2", "Jan 14, 2026", "AMAZON.COM", "Amazon", "Online purchase", Money.fromMinorUnits(-4299, "USD"),
            Category(id = "shopping", name = "Shopping", color = "#FF9800"), 0.88f),
        TransactionUiModel("3", "Jan 13, 2026", "SHELL OIL", "Shell", "Gas station", Money.fromMinorUnits(-5234, "USD"),
            Category(id = "transport", name = "Transportation", color = "#2196F3"), 0.92f, hasReceipt = true),
        TransactionUiModel("4", "Jan 12, 2026", "PAYROLL", "Payroll", "Direct deposit", Money.fromMinorUnits(250000, "USD"),
            Category(id = "income", name = "Income", color = "#4CAF50"), 1.0f),
        TransactionUiModel("5", "Jan 11, 2026", "WHOLEFDS", "Whole Foods", "Groceries", Money.fromMinorUnits(-8756, "USD"),
            Category(id = "food", name = "Food & Dining", color = "#FF5722"), 0.91f, hasReceipt = true)
    )
}
