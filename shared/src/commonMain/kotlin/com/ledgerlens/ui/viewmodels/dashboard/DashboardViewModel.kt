package com.ledgerlens.ui.viewmodels.dashboard

import com.ledgerlens.categorization.Category
import com.ledgerlens.data.repositories.CategoryRepository
import com.ledgerlens.data.repositories.StatisticsRepository
import com.ledgerlens.data.repositories.Transaction
import com.ledgerlens.data.repositories.TransactionRepository
import com.ledgerlens.data.repositories.CategorySpendingStats
import com.ledgerlens.domain.Money
import com.ledgerlens.ui.screens.dashboard.CategoryBreakdownUiModel
import com.ledgerlens.ui.screens.dashboard.SpendingSummaryUiModel
import com.ledgerlens.ui.screens.dashboard.TransactionUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val statisticsRepository: StatisticsRepository
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
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val year = now.year
                val month = now.monthNumber
                val monthNames = listOf(
                    "January", "February", "March", "April", "May", "June",
                    "July", "August", "September", "October", "November", "December"
                )
                val monthLabel = "${monthNames[month - 1]} $year"

                // Load all data from repositories
                val monthlyStats = statisticsRepository.getMonthlyStats(year, month).first()
                val categoryBreakdown = statisticsRepository.getCategoryBreakdown(year, month).first()
                val recentTransactions = transactionRepository.getRecentTransactions(limit = 5).first()
                val pendingReviewCount = statisticsRepository.getPendingReviewCount().first()
                val uncategorizedCount = statisticsRepository.getUncategorizedCount().first()

                // Map to UI models
                val transactionUiModels = recentTransactions.map { it.toTransactionUiModel() }
                val categoryUiModels = categoryBreakdown.map { it.toCategoryBreakdownUiModel() }

                _uiState.update {
                    DashboardUiState(
                        isLoading = false,
                        isRefreshing = false,
                        totalSpendingThisMonth = monthlyStats.totalSpending,
                        totalIncomeThisMonth = monthlyStats.totalIncome,
                        netChangeThisMonth = monthlyStats.netChange,
                        recentTransactions = transactionUiModels,
                        categoryBreakdown = categoryUiModels,
                        pendingReviewCount = pendingReviewCount,
                        uncategorizedCount = uncategorizedCount,
                        currentMonthLabel = monthLabel,
                        error = null
                    )
                }
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
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val year = now.year
                val month = now.monthNumber
                val monthNames = listOf(
                    "January", "February", "March", "April", "May", "June",
                    "July", "August", "September", "October", "November", "December"
                )
                val monthLabel = "${monthNames[month - 1]} $year"

                val monthlyStats = statisticsRepository.getMonthlyStats(year, month).first()
                val categoryBreakdown = statisticsRepository.getCategoryBreakdown(year, month).first()
                val recentTransactions = transactionRepository.getRecentTransactions(limit = 5).first()
                val pendingReviewCount = statisticsRepository.getPendingReviewCount().first()
                val uncategorizedCount = statisticsRepository.getUncategorizedCount().first()

                val transactionUiModels = recentTransactions.map { it.toTransactionUiModel() }
                val categoryUiModels = categoryBreakdown.map { it.toCategoryBreakdownUiModel() }

                _uiState.update {
                    DashboardUiState(
                        isLoading = false,
                        isRefreshing = false,
                        totalSpendingThisMonth = monthlyStats.totalSpending,
                        totalIncomeThisMonth = monthlyStats.totalIncome,
                        netChangeThisMonth = monthlyStats.netChange,
                        recentTransactions = transactionUiModels,
                        categoryBreakdown = categoryUiModels,
                        pendingReviewCount = pendingReviewCount,
                        uncategorizedCount = uncategorizedCount,
                        currentMonthLabel = monthLabel,
                        error = null
                    )
                }
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

    // Extension function to map Transaction to TransactionUiModel
    private fun Transaction.toTransactionUiModel(): TransactionUiModel {
        val category = if (categoryId != null) {
            Category(
                id = categoryId!!,
                name = categoryId!!, // Name will be looked up separately if needed
                color = null
            )
        } else null

        return TransactionUiModel(
            id = id,
            date = postedDate.toString(),
            merchantName = descriptionRaw,
            normalizedMerchant = merchantNormalized,
            description = merchantDisplay,
            amount = amount,
            category = category,
            categoryConfidence = categoryConfidence ?: 0f
        )
    }

    // Extension function to map CategorySpendingStats to CategoryBreakdownUiModel
    private fun CategorySpendingStats.toCategoryBreakdownUiModel(): CategoryBreakdownUiModel {
        return CategoryBreakdownUiModel(
            category = Category(
                id = categoryId,
                name = categoryName,
                color = categoryColor
            ),
            amount = totalSpent,
            transactionCount = transactionCount,
            percentage = percentageOfTotal
        )
    }
}
