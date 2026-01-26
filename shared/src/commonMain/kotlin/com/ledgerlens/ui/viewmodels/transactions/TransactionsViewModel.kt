package com.ledgerlens.ui.viewmodels.transactions

import com.ledgerlens.categorization.Category
import com.ledgerlens.data.repositories.CategoryEntity
import com.ledgerlens.data.repositories.CategoryRepository
import com.ledgerlens.data.repositories.Transaction
import com.ledgerlens.data.repositories.TransactionRepository
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
import kotlinx.coroutines.flow.first
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
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Cache for category lookups
    private var categoryCache: Map<String, CategoryEntity> = emptyMap()

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
                val transactions = transactionRepository.getTransactions().first()
                allTransactions = transactions.map { it.toUiModel() }
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
                val transactions = transactionRepository.getTransactions().first()
                allTransactions = transactions.map { it.toUiModel() }
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
                val transaction = transactionRepository.getTransaction(transactionId).first()

                _detailState.update {
                    it.copy(
                        isLoading = false,
                        transaction = transaction?.toUiModel(),
                        availableCategories = categoryCache.values.map { cat -> cat.toCategory() }
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
                val transaction = _detailState.value.transaction
                if (transaction != null) {
                    transactionRepository.updateCategory(
                        transactionId = transaction.id,
                        categoryId = categoryId,
                        confidence = 1.0f,
                        reason = "user_override"
                    )
                }

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
            try {
                val categories = categoryRepository.getAllCategories().first()
                categoryCache = categories.associateBy { it.id }
                _uiState.update { it.copy(availableCategories = categories.map { cat -> cat.toCategory() }) }
            } catch (e: Exception) {
                // Categories failed to load, use empty list
                _uiState.update { it.copy(availableCategories = emptyList()) }
            }
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

    // Extension function to map Transaction to TransactionUiModel
    private fun Transaction.toUiModel(): TransactionUiModel {
        val cachedCategory = categoryId?.let { categoryCache[it] }
        val category = if (categoryId != null) {
            Category(
                id = categoryId!!,
                name = cachedCategory?.name ?: categoryId!!,
                parentId = cachedCategory?.parentId,
                color = cachedCategory?.color
            )
        } else {
            null
        }

        return TransactionUiModel(
            id = id,
            date = postedDate.toString(),
            merchantName = descriptionRaw,
            normalizedMerchant = merchantNormalized,
            description = merchantDisplay,
            amount = amount,
            category = category,
            categoryConfidence = categoryConfidence ?: 0f,
            needsReview = !isReviewed && (categoryConfidence == null || categoryConfidence!! < 0.5f)
        )
    }

    // Extension function to map CategoryEntity to Category
    private fun CategoryEntity.toCategory(): Category {
        return Category(
            id = id,
            name = name,
            parentId = parentId,
            icon = icon,
            color = color,
            isSystemDefault = isSystemDefault,
            isUserCustom = isUserCustom
        )
    }
}
