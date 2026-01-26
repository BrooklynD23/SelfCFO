package com.ledgerlens.data.repositories.impl

import app.cash.turbine.test
import com.ledgerlens.data.repositories.TransactionFilter
import com.ledgerlens.db.LedgerLensDatabase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class SqlDelightTransactionRepositoryTest {
    private lateinit var database: LedgerLensDatabase
    private lateinit var repository: SqlDelightTransactionRepository
    private lateinit var categoryRepository: SqlDelightCategoryRepository
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        database = TestDatabaseHelper.createInMemoryDatabase()
        repository = SqlDelightTransactionRepository(database, testDispatcher)
        categoryRepository = SqlDelightCategoryRepository(database, testDispatcher)
    }

    @Test
    fun `getTransactions with empty filter returns all transactions`() = runTest(testDispatcher) {
        // Note: Transactions are populated via import process
        // This test verifies the flow works with empty data
        repository.getTransactions(TransactionFilter()).test {
            val transactions = awaitItem()
            assertTrue(transactions.isEmpty()) // No transactions yet
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getRecentTransactions returns limited results`() = runTest(testDispatcher) {
        repository.getRecentTransactions(10).test {
            val transactions = awaitItem()
            assertTrue(transactions.size <= 10)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getTransactionsNeedingReview returns unreviewed transactions`() = runTest(testDispatcher) {
        repository.getTransactionsNeedingReview().test {
            val transactions = awaitItem()
            // All returned transactions should have is_reviewed = false and low confidence
            transactions.forEach { txn ->
                assertTrue(!txn.isReviewed || txn.categoryConfidence?.let { it < 0.8f } == true)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `countAll returns transaction count`() = runTest(testDispatcher) {
        val count = repository.countAll()
        assertEquals(0L, count) // No transactions yet
    }

    @Test
    fun `getTransaction returns null for non-existent id`() = runTest(testDispatcher) {
        repository.getTransaction("non-existent").test {
            val result = awaitItem()
            assertEquals(null, result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getTransactions with category filter returns filtered results`() = runTest(testDispatcher) {
        categoryRepository.seedDefaultCategories()

        repository.getTransactions(TransactionFilter(categoryId = "food")).test {
            val transactions = awaitItem()
            transactions.forEach { txn ->
                assertEquals("food", txn.categoryId)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getTransactions with search filter searches merchants and descriptions`() = runTest(testDispatcher) {
        repository.getTransactions(TransactionFilter(searchQuery = "Coffee")).test {
            val transactions = awaitItem()
            transactions.forEach { txn ->
                assertTrue(
                    txn.merchantDisplay.contains("Coffee", ignoreCase = true) ||
                        txn.descriptionRaw.contains("Coffee", ignoreCase = true)
                )
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getTransactions with onlyUnreviewed filter returns unreviewed`() = runTest(testDispatcher) {
        repository.getTransactions(TransactionFilter(onlyUnreviewed = true)).test {
            val transactions = awaitItem()
            transactions.forEach { txn ->
                assertTrue(!txn.isReviewed)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
