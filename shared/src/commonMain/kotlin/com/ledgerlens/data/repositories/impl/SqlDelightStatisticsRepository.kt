package com.ledgerlens.data.repositories.impl

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.ledgerlens.data.mappers.TransactionMapper
import com.ledgerlens.data.repositories.CategorySpendingStats
import com.ledgerlens.data.repositories.MonthComparison
import com.ledgerlens.data.repositories.MonthlyStats
import com.ledgerlens.data.repositories.StatisticsRepository
import com.ledgerlens.data.repositories.TopMerchant
import com.ledgerlens.db.LedgerLensDatabase
import com.ledgerlens.domain.Money
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * SQLDelight implementation of StatisticsRepository.
 * Provides read-only aggregation queries for dashboard and analytics.
 */
class SqlDelightStatisticsRepository(
    private val database: LedgerLensDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : StatisticsRepository {

    private val statisticsQueries = database.statisticsQueries

    override fun getMonthlyStats(year: Int, month: Int): Flow<MonthlyStats> {
        return statisticsQueries.selectMonthlyStats(year.toLong(), month.toLong())
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { row ->
                row?.let {
                    MonthlyStats(
                        year = it.year?.toInt() ?: year,
                        month = it.month?.toInt() ?: month,
                        totalSpending = Money(it.total_spending_minor ?: 0L, "USD"),
                        totalIncome = Money(it.total_income_minor ?: 0L, "USD"),
                        netChange = Money(it.net_change_minor ?: 0L, "USD"),
                        transactionCount = it.transaction_count?.toInt() ?: 0
                    )
                } ?: MonthlyStats(
                    year = year,
                    month = month,
                    totalSpending = Money(0L, "USD"),
                    totalIncome = Money(0L, "USD"),
                    netChange = Money(0L, "USD"),
                    transactionCount = 0
                )
            }
    }

    override fun getMonthlyStatsList(
        startYear: Int,
        startMonth: Int,
        endYear: Int,
        endMonth: Int
    ): Flow<List<MonthlyStats>> {
        val startDate = LocalDate(startYear, startMonth, 1)
        val endDate = LocalDate(endYear, endMonth, 28) // Simplified, use last day logic if needed
        val startMillis = TransactionMapper.localDateToEpochMillis(startDate)
        val endMillis = TransactionMapper.localDateToEpochMillis(endDate) + (31L * 24 * 60 * 60 * 1000)

        return statisticsQueries.selectMonthlyStatsList(startMillis, endMillis)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows ->
                rows.map { row ->
                    MonthlyStats(
                        year = row.year?.toInt() ?: 0,
                        month = row.month?.toInt() ?: 0,
                        totalSpending = Money(row.total_spending_minor ?: 0L, "USD"),
                        totalIncome = Money(row.total_income_minor ?: 0L, "USD"),
                        netChange = Money(row.net_change_minor ?: 0L, "USD"),
                        transactionCount = row.transaction_count?.toInt() ?: 0
                    )
                }
            }
    }

    override fun getCategoryBreakdown(year: Int, month: Int): Flow<List<CategorySpendingStats>> {
        return statisticsQueries.selectCategoryBreakdown(year.toLong(), month.toLong())
            .asFlow()
            .mapToList(dispatcher)
            .map { rows ->
                val total = rows.fold(0L) { acc, it -> acc + ((it.total_spent_minor as? Long) ?: it.total_spent_minor?.toLong() ?: 0L) }
                rows.map { row ->
                    CategorySpendingStats(
                        categoryId = row.category_id,
                        categoryName = row.category_name,
                        categoryColor = row.category_color,
                        totalSpent = Money((row.total_spent_minor as? Long) ?: row.total_spent_minor?.toLong() ?: 0L, "USD"),
                        transactionCount = row.transaction_count?.toInt() ?: 0,
                        percentageOfTotal = if (total > 0) {
                            (((row.total_spent_minor as? Long) ?: row.total_spent_minor?.toLong() ?: 0L) * 100f / total)
                        } else {
                            0f
                        }
                    )
                }
            }
    }

    override fun getCategoryBreakdownForRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<CategorySpendingStats>> {
        val startMillis = TransactionMapper.localDateToEpochMillis(startDate)
        val endMillis = TransactionMapper.localDateToEpochMillis(endDate) + (24 * 60 * 60 * 1000 - 1)

        return statisticsQueries.selectCategoryBreakdownForRange(startMillis, endMillis)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows ->
                val total = rows.fold(0L) { acc, it -> acc + ((it.total_spent_minor as? Long) ?: it.total_spent_minor?.toLong() ?: 0L) }
                rows.map { row ->
                    CategorySpendingStats(
                        categoryId = row.category_id,
                        categoryName = row.category_name,
                        categoryColor = row.category_color,
                        totalSpent = Money((row.total_spent_minor as? Long) ?: row.total_spent_minor?.toLong() ?: 0L, "USD"),
                        transactionCount = row.transaction_count?.toInt() ?: 0,
                        percentageOfTotal = if (total > 0) {
                            (((row.total_spent_minor as? Long) ?: row.total_spent_minor?.toLong() ?: 0L) * 100f / total)
                        } else {
                            0f
                        }
                    )
                }
            }
    }

    override fun getMonthComparison(year: Int, month: Int): Flow<MonthComparison> {
        // Calculate previous month
        val prevYear = if (month == 1) year - 1 else year
        val prevMonth = if (month == 1) 12 else month - 1

        return combine(
            getMonthlyStats(year, month),
            getMonthlyStats(prevYear, prevMonth)
        ) { current, previous ->
            val spendingChange = if (previous.totalSpending.minorUnits != 0L) {
                (
                    (current.totalSpending.minorUnits - previous.totalSpending.minorUnits) * 100f /
                        kotlin.math.abs(previous.totalSpending.minorUnits)
                    )
            } else {
                0f
            }

            val incomeChange = if (previous.totalIncome.minorUnits != 0L) {
                (
                    (current.totalIncome.minorUnits - previous.totalIncome.minorUnits) * 100f /
                        previous.totalIncome.minorUnits
                    )
            } else {
                0f
            }

            MonthComparison(
                currentMonth = current,
                previousMonth = previous,
                spendingChangePercent = spendingChange,
                incomeChangePercent = incomeChange
            )
        }
    }

    override fun getTopMerchants(limit: Int, startDate: LocalDate?, endDate: LocalDate?): Flow<List<TopMerchant>> {
        val start = startDate ?: LocalDate(2000, 1, 1)
        val end = endDate ?: LocalDate(2099, 12, 31)
        val startMillis = TransactionMapper.localDateToEpochMillis(start)
        val endMillis = TransactionMapper.localDateToEpochMillis(end) + (24 * 60 * 60 * 1000 - 1)

        return statisticsQueries.selectTopMerchants(startMillis, endMillis, limit.toLong())
            .asFlow()
            .mapToList(dispatcher)
            .map { rows ->
                rows.map { row ->
                    TopMerchant(
                        merchantName = row.merchant_name ?: "Unknown",
                        totalSpent = Money((row.total_spent_minor as? Long) ?: row.total_spent_minor?.toLong() ?: 0L, "USD"),
                        transactionCount = row.transaction_count?.toInt() ?: 0
                    )
                }
            }
    }

    override fun getDailySpending(year: Int, month: Int): Flow<Map<Int, Money>> {
        return statisticsQueries.selectDailySpending(year.toLong(), month.toLong())
            .asFlow()
            .mapToList(dispatcher)
            .map { rows ->
                rows.associate { row ->
                    (row.day?.toInt() ?: 1) to Money(row.spending_minor ?: 0L, "USD")
                }
            }
    }

    override fun getYearToDateStats(year: Int): Flow<MonthlyStats> {
        return statisticsQueries.selectYearToDateStats(year.toLong())
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { row ->
                row?.let {
                    MonthlyStats(
                        year = it.year?.toInt() ?: year,
                        month = 0, // YTD doesn't have a specific month
                        totalSpending = Money(it.total_spending_minor ?: 0L, "USD"),
                        totalIncome = Money(it.total_income_minor ?: 0L, "USD"),
                        netChange = Money(it.net_change_minor ?: 0L, "USD"),
                        transactionCount = it.transaction_count?.toInt() ?: 0
                    )
                } ?: MonthlyStats(
                    year = year,
                    month = 0,
                    totalSpending = Money(0L, "USD"),
                    totalIncome = Money(0L, "USD"),
                    netChange = Money(0L, "USD"),
                    transactionCount = 0
                )
            }
    }

    override fun getPendingReviewCount(): Flow<Int> {
        return statisticsQueries.selectPendingReviewCount()
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it?.toInt() ?: 0 }
    }

    override fun getUncategorizedCount(): Flow<Int> {
        return statisticsQueries.selectUncategorizedCount()
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it?.toInt() ?: 0 }
    }
}
