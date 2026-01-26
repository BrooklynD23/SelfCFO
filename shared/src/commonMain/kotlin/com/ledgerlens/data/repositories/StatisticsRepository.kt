package com.ledgerlens.data.repositories

import com.ledgerlens.domain.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * Monthly spending statistics.
 */
data class MonthlyStats(
    val year: Int,
    val month: Int,
    val totalSpending: Money,
    val totalIncome: Money,
    val netChange: Money,
    val transactionCount: Int
)

/**
 * Category spending breakdown.
 */
data class CategorySpendingStats(
    val categoryId: String,
    val categoryName: String,
    val categoryColor: String?,
    val totalSpent: Money,
    val transactionCount: Int,
    val percentageOfTotal: Float
)

/**
 * Month-over-month comparison.
 */
data class MonthComparison(
    val currentMonth: MonthlyStats,
    val previousMonth: MonthlyStats,
    val spendingChangePercent: Float,
    val incomeChangePercent: Float
)

/**
 * Top merchant by spending.
 */
data class TopMerchant(
    val merchantName: String,
    val totalSpent: Money,
    val transactionCount: Int
)

/**
 * Repository interface for statistics and analytics.
 */
interface StatisticsRepository {
    /**
     * Get monthly statistics.
     */
    fun getMonthlyStats(year: Int, month: Int): Flow<MonthlyStats>

    /**
     * Get statistics for a range of months.
     */
    fun getMonthlyStatsList(startYear: Int, startMonth: Int, endYear: Int, endMonth: Int): Flow<List<MonthlyStats>>

    /**
     * Get category spending breakdown for a month.
     */
    fun getCategoryBreakdown(year: Int, month: Int): Flow<List<CategorySpendingStats>>

    /**
     * Get category spending breakdown for a date range.
     */
    fun getCategoryBreakdownForRange(startDate: LocalDate, endDate: LocalDate): Flow<List<CategorySpendingStats>>

    /**
     * Get month-over-month comparison.
     */
    fun getMonthComparison(year: Int, month: Int): Flow<MonthComparison>

    /**
     * Get top merchants by spending.
     */
    fun getTopMerchants(
        limit: Int = 10,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null
    ): Flow<List<TopMerchant>>

    /**
     * Get daily spending for a month (for charts).
     */
    fun getDailySpending(year: Int, month: Int): Flow<Map<Int, Money>>

    /**
     * Get total spending year-to-date.
     */
    fun getYearToDateStats(year: Int): Flow<MonthlyStats>

    /**
     * Get pending review count.
     */
    fun getPendingReviewCount(): Flow<Int>

    /**
     * Get uncategorized transaction count.
     */
    fun getUncategorizedCount(): Flow<Int>
}
