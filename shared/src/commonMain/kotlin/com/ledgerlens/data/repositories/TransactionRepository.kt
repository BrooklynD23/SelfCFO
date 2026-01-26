package com.ledgerlens.data.repositories

import com.ledgerlens.domain.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * Domain model for a transaction displayed in the UI.
 */
data class Transaction(
    val id: String,
    val accountId: String?,
    val postedDate: LocalDate,
    val transactionDate: LocalDate?,
    val descriptionRaw: String,
    val merchantDisplay: String,
    val merchantNormalized: String,
    val amount: Money,
    val categoryId: String?,
    val categoryConfidence: Float?,
    val categoryReason: String?,
    val tags: List<String>,
    val notes: String,
    val isTransfer: Boolean,
    val isExcluded: Boolean,
    val isReviewed: Boolean,
    val hashFingerprint: String,
    val importedAt: Long
)

/**
 * Filter criteria for querying transactions.
 */
data class TransactionFilter(
    val accountId: String? = null,
    val categoryId: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val searchQuery: String? = null,
    val includeExcluded: Boolean = false,
    val onlyUnreviewed: Boolean = false,
    val minAmount: Money? = null,
    val maxAmount: Money? = null
)

/**
 * Repository interface for transaction data access.
 */
interface TransactionRepository {
    /**
     * Get all transactions matching the filter criteria.
     */
    fun getTransactions(filter: TransactionFilter = TransactionFilter()): Flow<List<Transaction>>

    /**
     * Get a single transaction by ID.
     */
    fun getTransaction(id: String): Flow<Transaction?>

    /**
     * Get the most recent transactions.
     */
    fun getRecentTransactions(limit: Int = 10): Flow<List<Transaction>>

    /**
     * Get transactions within a date range.
     */
    fun getTransactionsByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<Transaction>>

    /**
     * Get transactions for a specific category.
     */
    fun getTransactionsByCategory(categoryId: String): Flow<List<Transaction>>

    /**
     * Get transactions that need review (low confidence).
     */
    fun getTransactionsNeedingReview(): Flow<List<Transaction>>

    /**
     * Insert a new transaction.
     * @return The ID of the inserted transaction.
     */
    suspend fun insertTransaction(transaction: Transaction): String

    /**
     * Update an existing transaction.
     */
    suspend fun updateTransaction(transaction: Transaction)

    /**
     * Delete a transaction by ID.
     */
    suspend fun deleteTransaction(id: String)

    /**
     * Update just the category of a transaction.
     */
    suspend fun updateCategory(
        transactionId: String,
        categoryId: String,
        confidence: Float = 1.0f,
        reason: String = "user_override"
    )

    /**
     * Mark a transaction as reviewed.
     */
    suspend fun markAsReviewed(transactionId: String)

    /**
     * Mark a transaction as excluded.
     */
    suspend fun markAsExcluded(transactionId: String, excluded: Boolean)

    /**
     * Add a note to a transaction.
     */
    suspend fun updateNotes(transactionId: String, notes: String)

    /**
     * Count transactions by account.
     */
    suspend fun countByAccount(accountId: String): Long

    /**
     * Count all transactions.
     */
    suspend fun countAll(): Long
}
