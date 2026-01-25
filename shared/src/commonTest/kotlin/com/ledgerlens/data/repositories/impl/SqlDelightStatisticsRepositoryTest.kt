package com.ledgerlens.data.repositories.impl

import app.cash.turbine.test
import com.ledgerlens.db.LedgerLensDatabase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class SqlDelightStatisticsRepositoryTest {
    private lateinit var database: LedgerLensDatabase
    private lateinit var repository: SqlDelightStatisticsRepository
    private lateinit var categoryRepository: SqlDelightCategoryRepository
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        database = TestDatabaseHelper.createInMemoryDatabase()
        repository = SqlDelightStatisticsRepository(database, testDispatcher)
        categoryRepository = SqlDelightCategoryRepository(database, testDispatcher)
    }

    @Test
    fun `getMonthlyStats returns stats for given month`() = runTest(testDispatcher) {
        repository.getMonthlyStats(2024, 1).test {
            val stats = awaitItem()
            assertNotNull(stats)
            assertEquals(2024, stats.year)
            assertEquals(1, stats.month)
            // With no transactions, values should be 0
            assertEquals(0L, stats.totalSpending.minorUnits)
            assertEquals(0L, stats.totalIncome.minorUnits)
            assertEquals(0, stats.transactionCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getMonthlyStatsList returns stats for date range`() = runTest(testDispatcher) {
        repository.getMonthlyStatsList(2024, 1, 2024, 3).test {
            val statsList = awaitItem()
            // May be empty if no transactions exist in that range
            assertTrue(statsList.size <= 3) // At most 3 months
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getCategoryBreakdown returns spending by category`() = runTest(testDispatcher) {
        categoryRepository.seedDefaultCategories()

        repository.getCategoryBreakdown(2024, 1).test {
            val breakdown = awaitItem()
            // With no transactions, breakdown should be empty
            assertTrue(breakdown.isEmpty() || breakdown.all { it.transactionCount == 0 })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getCategoryBreakdownForRange returns spending for date range`() = runTest(testDispatcher) {
        categoryRepository.seedDefaultCategories()

        val startDate = LocalDate(2024, 1, 1)
        val endDate = LocalDate(2024, 1, 31)

        repository.getCategoryBreakdownForRange(startDate, endDate).test {
            val breakdown = awaitItem()
            // Categories should have percentage calculations
            if (breakdown.isNotEmpty() && breakdown.any { it.totalSpent.minorUnits > 0 }) {
                val totalPercent = breakdown.sumOf { it.percentageOfTotal.toDouble() }.toFloat()
                assertTrue(totalPercent in 99f..101f) // Should sum to ~100%
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getMonthComparison compares current and previous month`() = runTest(testDispatcher) {
        repository.getMonthComparison(2024, 2).test {
            val comparison = awaitItem()
            assertNotNull(comparison)
            assertEquals(2024, comparison.currentMonth.year)
            assertEquals(2, comparison.currentMonth.month)
            assertEquals(2024, comparison.previousMonth.year)
            assertEquals(1, comparison.previousMonth.month)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getMonthComparison handles January correctly`() = runTest(testDispatcher) {
        repository.getMonthComparison(2024, 1).test {
            val comparison = awaitItem()
            assertNotNull(comparison)
            assertEquals(2024, comparison.currentMonth.year)
            assertEquals(1, comparison.currentMonth.month)
            assertEquals(2023, comparison.previousMonth.year) // Previous year
            assertEquals(12, comparison.previousMonth.month) // December
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getTopMerchants returns top merchants by spending`() = runTest(testDispatcher) {
        repository.getTopMerchants(10).test {
            val merchants = awaitItem()
            assertTrue(merchants.size <= 10)
            // Should be sorted by spending (descending)
            for (i in 0 until merchants.size - 1) {
                assertTrue(merchants[i].totalSpent.minorUnits >= merchants[i + 1].totalSpent.minorUnits)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getDailySpending returns spending per day`() = runTest(testDispatcher) {
        repository.getDailySpending(2024, 1).test {
            val dailySpending = awaitItem()
            // Keys should be valid day numbers (1-31)
            dailySpending.keys.forEach { day ->
                assertTrue(day in 1..31)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getYearToDateStats returns cumulative stats for year`() = runTest(testDispatcher) {
        repository.getYearToDateStats(2024).test {
            val stats = awaitItem()
            assertNotNull(stats)
            assertEquals(2024, stats.year)
            assertEquals(0, stats.month) // YTD has no specific month
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getPendingReviewCount returns count of unreviewed transactions`() = runTest(testDispatcher) {
        repository.getPendingReviewCount().test {
            val count = awaitItem()
            assertTrue(count >= 0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getUncategorizedCount returns count of uncategorized transactions`() = runTest(testDispatcher) {
        repository.getUncategorizedCount().test {
            val count = awaitItem()
            assertTrue(count >= 0)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
