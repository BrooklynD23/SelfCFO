package com.ledgerlens.ui.viewmodels.dashboard

import com.ledgerlens.categorization.Category
import com.ledgerlens.data.repositories.CategoryRepository
import com.ledgerlens.data.repositories.CategorySpendingStats
import com.ledgerlens.data.repositories.StatisticsRepository
import com.ledgerlens.data.repositories.Transaction
import com.ledgerlens.data.repositories.TransactionRepository
import com.ledgerlens.domain.Money
import com.ledgerlens.ui.components.cards.PillarData
import com.ledgerlens.ui.components.cards.PillarType
import com.ledgerlens.ui.components.cards.WeeklyInsight
import com.ledgerlens.ui.components.charts.ChartDataPoint
import com.ledgerlens.ui.components.charts.TimeRange
import com.ledgerlens.ui.screens.dashboard.CategoryBreakdownUiModel
import com.ledgerlens.ui.screens.dashboard.TransactionUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * UI state for the Dashboard screen.
 * Includes StitchUI additions: weekly insight, net worth history, pillars.
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
    val error: String? = null,
    // StitchUI additions
    val userName: String = "there",
    val weeklyInsight: WeeklyInsight? = null,
    val netWorthHistory: List<ChartDataPoint> = emptyList(),
    val selectedTimeRange: TimeRange = TimeRange.ONE_MONTH,
    val totalNetWorth: Money = Money.zero("USD"),
    val pillars: List<PillarData> = emptyList()
) {
    val hasRecentTransactions: Boolean get() = recentTransactions.isNotEmpty()
    val hasPendingItems: Boolean get() = pendingReviewCount > 0 || uncategorizedCount > 0
    val totalPendingCount: Int get() = pendingReviewCount + uncategorizedCount
    val greeting: String get() {
        val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
        return when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
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

                // Compute StitchUI additions
                val weeklyInsight = computeWeeklyInsight(
                    monthlyStats.totalSpending,
                    monthlyStats.totalIncome,
                    monthlyStats.netChange
                )
                val pillars = computePillars(
                    totalSavings = monthlyStats.totalIncome,
                    totalSpending = monthlyStats.totalSpending,
                    totalInvestments = Money.zero("USD")
                )
                val netWorthHistory = generateSampleNetWorthHistory(
                    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
                    30
                )

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
                        error = null,
                        weeklyInsight = weeklyInsight,
                        netWorthHistory = netWorthHistory,
                        pillars = pillars,
                        totalNetWorth = Money.of(45000.0, "USD")
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

                // Compute StitchUI additions for refresh
                val weeklyInsight = computeWeeklyInsight(
                    monthlyStats.totalSpending,
                    monthlyStats.totalIncome,
                    monthlyStats.netChange
                )
                val pillars = computePillars(
                    totalSavings = monthlyStats.totalIncome,
                    totalSpending = monthlyStats.totalSpending,
                    totalInvestments = Money.zero("USD")
                )
                val netWorthHistory = generateSampleNetWorthHistory(
                    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
                    _uiState.value.selectedTimeRange.days.let { if (it == -1) 365 else it }
                )

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
                        error = null,
                        selectedTimeRange = it.selectedTimeRange,
                        weeklyInsight = weeklyInsight,
                        netWorthHistory = netWorthHistory,
                        pillars = pillars,
                        totalNetWorth = Money.of(45000.0, "USD")
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

    fun selectTimeRange(range: TimeRange) {
        _uiState.update { it.copy(selectedTimeRange = range) }
        loadNetWorthHistory(range)
    }

    private fun loadNetWorthHistory(range: TimeRange) {
        viewModelScope.launch {
            try {
                // Generate sample net worth history based on time range
                // In a real app, this would come from the statistics repository
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                val days = if (range.days == -1) 365 else range.days
                val points = generateSampleNetWorthHistory(now, days)
                _uiState.update { it.copy(netWorthHistory = points) }
            } catch (e: Exception) {
                // Silently fail for chart data
            }
        }
    }

    private fun generateSampleNetWorthHistory(endDate: LocalDate, days: Int): List<ChartDataPoint> {
        // Generate sample data points for demonstration
        // In production, this would come from actual account balances
        val baseValue = 45000.0
        val variance = 5000.0
        val numPoints = days.coerceAtMost(30).coerceAtLeast(7)
        val step = days / numPoints

        return (0 until numPoints).map { i ->
            val daysBack = (numPoints - 1 - i) * step
            val date = endDate.minus(DatePeriod(days = daysBack))
            val trend = i.toDouble() / numPoints * 0.1 // 10% growth trend
            val noise = kotlin.math.sin(i * 0.5) * 0.02
            val value = baseValue * (1 + trend + noise)
            ChartDataPoint(date = date, value = value)
        }
    }

    private fun computeWeeklyInsight(
        totalSpending: Money,
        totalIncome: Money,
        netChange: Money
    ): WeeklyInsight? {
        // Compute a simple insight based on the week's performance
        // In a real app, this would compare against budget/historical data
        val netAmount = netChange.amount.toDouble()
        return when {
            netAmount > 0 -> WeeklyInsight.UnderBudget(netChange.formatted())
            netAmount < 0 -> WeeklyInsight.OverBudget(netChange.abs().formatted())
            else -> null
        }
    }

    private fun computePillars(
        totalSavings: Money,
        totalSpending: Money,
        totalInvestments: Money
    ): List<PillarData> {
        return listOf(
            PillarData(
                type = PillarType.SAVINGS,
                currentAmount = totalSavings.formatted(),
                changeText = "+2.3% this month",
                isPositiveChange = true
            ),
            PillarData(
                type = PillarType.EXPENSES,
                currentAmount = totalSpending.abs().formatted(),
                changeText = "-5% vs last month",
                isPositiveChange = true
            ),
            PillarData(
                type = PillarType.INVESTMENTS,
                currentAmount = totalInvestments.formatted(),
                changeText = "+8.2% YTD",
                isPositiveChange = true
            )
        )
    }

    // Extension function to map Transaction to TransactionUiModel
    private fun Transaction.toTransactionUiModel(): TransactionUiModel {
        val category = if (categoryId != null) {
            Category(
                id = categoryId!!,
                name = categoryId!!, // Name will be looked up separately if needed
                color = null
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
