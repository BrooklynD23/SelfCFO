package com.ledgerlens.ui.viewmodels.dashboard

import com.ledgerlens.categorization.Category
import com.ledgerlens.domain.Money
import com.ledgerlens.ui.screens.dashboard.CategoryBreakdownUiModel
import com.ledgerlens.ui.screens.dashboard.SpendingSummaryUiModel
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
 * UI state for the Dashboard screen.
 */
data class DashboardUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val totalSpendingThisMonth: Money = Money.zero("USD"),
    val totalIncomeThisMonth: Money = Money.zero("USD"),
    val netChangeThisMonth: Money = Money.zero("USD"),
    val recentTransactions: List<TransactionUiModel> = emptyList(),
    val categoryBreakdown: List<CategoryBreakdownUiModel> = emptyList(),
    val pendingReviewCount: Int = 0,
    val uncategorizedCount: Int = 0,
    val currentMonthLabel: String = "",
    val error: String? = null
) {
    val hasRecentTransactions: Boolean get() = recentTransactions.isNotEmpty()
    val hasPendingItems: Boolean get() = pendingReviewCount > 0 || uncategorizedCount > 0
    val totalPendingCount: Int get() = pendingReviewCount + uncategorizedCount
}

/**
 * Events emitted by the Dashboard screen.
 */
sealed class DashboardEvent {
    object NavigateToImport : DashboardEvent()
    object NavigateToReview : DashboardEvent()
    object NavigateToTransactions : DashboardEvent()
    data class NavigateToTransactionDetail(val transactionId: String) : DashboardEvent()
    data class NavigateToCategory(val categoryId: String) : DashboardEvent()
}

/**
 * ViewModel for the Dashboard screen.
 * Provides monthly spending summary, recent transactions, and category breakdown.
 */
class DashboardViewModel(
    // TODO: Inject actual dependencies when available
    // private val transactionRepository: TransactionRepository,
    // private val categoryRepository: CategoryRepository,
    // private val reviewQueueManager: ReviewQueueManager
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<DashboardEvent?>(null)
    val events: StateFlow<DashboardEvent?> = _events.asStateFlow()

    init {
        loadDashboardData()
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // TODO: Replace with actual data loading
                delay(500) // Simulated network delay
                
                val mockData = generateMockDashboardData()
                _uiState.update { mockData }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load dashboard data"
                    )
                }
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }

            try {
                delay(300) // Simulated refresh
                val mockData = generateMockDashboardData()
                _uiState.update { mockData.copy(isRefreshing = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        error = e.message ?: "Failed to refresh data"
                    )
                }
            }
        }
    }

    fun onImportClicked() {
        _events.value = DashboardEvent.NavigateToImport
    }

    fun onReviewClicked() {
        _events.value = DashboardEvent.NavigateToReview
    }

    fun onViewAllTransactionsClicked() {
        _events.value = DashboardEvent.NavigateToTransactions
    }

    fun onTransactionClicked(transactionId: String) {
        _events.value = DashboardEvent.NavigateToTransactionDetail(transactionId)
    }

    fun onCategoryClicked(categoryId: String) {
        _events.value = DashboardEvent.NavigateToCategory(categoryId)
    }

    fun clearEvent() {
        _events.value = null
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun generateMockDashboardData(): DashboardUiState {
        val mockTransactions = listOf(
            TransactionUiModel(
                id = "1",
                date = "Jan 15, 2026",
                merchantName = "STARBUCKS STORE 12345",
                normalizedMerchant = "Starbucks",
                description = "Coffee purchase",
                amount = Money.fromMinorUnits(-575, "USD"),
                category = Category(
                    id = "food_coffee",
                    name = "Coffee & Tea",
                    parentId = "food",
                    color = "#6F4E37"
                ),
                categoryConfidence = 0.95f
            ),
            TransactionUiModel(
                id = "2",
                date = "Jan 14, 2026",
                merchantName = "AMAZON.COM",
                normalizedMerchant = "Amazon",
                description = "Online purchase",
                amount = Money.fromMinorUnits(-4299, "USD"),
                category = Category(
                    id = "shopping",
                    name = "Shopping",
                    color = "#FF9800"
                ),
                categoryConfidence = 0.88f
            ),
            TransactionUiModel(
                id = "3",
                date = "Jan 13, 2026",
                merchantName = "SHELL OIL 12345678",
                normalizedMerchant = "Shell",
                description = "Gas station",
                amount = Money.fromMinorUnits(-5234, "USD"),
                category = Category(
                    id = "transport_gas",
                    name = "Gas & Fuel",
                    parentId = "transport",
                    color = "#795548"
                ),
                categoryConfidence = 0.92f
            ),
            TransactionUiModel(
                id = "4",
                date = "Jan 12, 2026",
                merchantName = "PAYROLL DEPOSIT",
                normalizedMerchant = "Payroll",
                description = "Direct deposit",
                amount = Money.fromMinorUnits(250000, "USD"),
                category = Category(
                    id = "income",
                    name = "Income",
                    color = "#4CAF50"
                ),
                categoryConfidence = 1.0f
            ),
            TransactionUiModel(
                id = "5",
                date = "Jan 11, 2026",
                merchantName = "WHOLEFDS MKT 10234",
                normalizedMerchant = "Whole Foods",
                description = "Grocery shopping",
                amount = Money.fromMinorUnits(-8756, "USD"),
                category = Category(
                    id = "food_groceries",
                    name = "Groceries",
                    parentId = "food",
                    color = "#8BC34A"
                ),
                categoryConfidence = 0.91f
            )
        )

        val categoryBreakdown = listOf(
            CategoryBreakdownUiModel(
                category = Category(id = "food", name = "Food & Dining", color = "#FF5722"),
                amount = Money.fromMinorUnits(-42350, "USD"),
                transactionCount = 15,
                percentage = 0.35f
            ),
            CategoryBreakdownUiModel(
                category = Category(id = "transport", name = "Transportation", color = "#2196F3"),
                amount = Money.fromMinorUnits(-28500, "USD"),
                transactionCount = 8,
                percentage = 0.24f
            ),
            CategoryBreakdownUiModel(
                category = Category(id = "shopping", name = "Shopping", color = "#FF9800"),
                amount = Money.fromMinorUnits(-21200, "USD"),
                transactionCount = 6,
                percentage = 0.18f
            ),
            CategoryBreakdownUiModel(
                category = Category(id = "bills", name = "Bills & Utilities", color = "#9C27B0"),
                amount = Money.fromMinorUnits(-15800, "USD"),
                transactionCount = 4,
                percentage = 0.13f
            ),
            CategoryBreakdownUiModel(
                category = Category(id = "entertainment", name = "Entertainment", color = "#E91E63"),
                amount = Money.fromMinorUnits(-12150, "USD"),
                transactionCount = 5,
                percentage = 0.10f
            )
        )

        val totalSpending = Money.fromMinorUnits(-120000, "USD")
        val totalIncome = Money.fromMinorUnits(250000, "USD")

        return DashboardUiState(
            isLoading = false,
            isRefreshing = false,
            totalSpendingThisMonth = totalSpending,
            totalIncomeThisMonth = totalIncome,
            netChangeThisMonth = totalIncome + totalSpending,
            recentTransactions = mockTransactions,
            categoryBreakdown = categoryBreakdown,
            pendingReviewCount = 3,
            uncategorizedCount = 7,
            currentMonthLabel = "January 2026",
            error = null
        )
    }
}
