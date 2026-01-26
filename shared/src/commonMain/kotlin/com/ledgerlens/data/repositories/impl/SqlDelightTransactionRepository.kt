package com.ledgerlens.data.repositories.impl

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.ledgerlens.data.mappers.TransactionMapper
import com.ledgerlens.data.repositories.Transaction
import com.ledgerlens.data.repositories.TransactionFilter
import com.ledgerlens.data.repositories.TransactionRepository
import com.ledgerlens.db.LedgerLensDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

/**
 * SQLDelight implementation of TransactionRepository.
 * Uses the transaction_view for unified access to transactions with overrides.
 */
class SqlDelightTransactionRepository(
    private val database: LedgerLensDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : TransactionRepository {

    private val viewsQueries = database.viewsQueries
    private val importedTransactionQueries = database.importedTransactionQueries
    private val transactionOverrideQueries = database.transactionOverrideQueries

    override fun getTransactions(filter: TransactionFilter): Flow<List<Transaction>> {
        // Apply filters based on what's provided
        return when {
            filter.accountId != null -> {
                viewsQueries.selectByAccount(filter.accountId)
                    .asFlow()
                    .mapToList(dispatcher)
                    .map { list ->
                        list.map(TransactionMapper::toDomain)
                            .applyInMemoryFilters(filter)
                    }
            }
            filter.categoryId != null -> {
                viewsQueries.selectByCategory(filter.categoryId)
                    .asFlow()
                    .mapToList(dispatcher)
                    .map { list ->
                        list.map(TransactionMapper::toDomain)
                            .applyInMemoryFilters(filter)
                    }
            }
            filter.startDate != null && filter.endDate != null -> {
                val startMillis = TransactionMapper.localDateToEpochMillis(filter.startDate)
                val endMillis = TransactionMapper.localDateToEpochMillis(filter.endDate) + (24 * 60 * 60 * 1000 - 1)
                viewsQueries.selectByDateRange(startMillis, endMillis)
                    .asFlow()
                    .mapToList(dispatcher)
                    .map { list ->
                        list.map(TransactionMapper::toDomain)
                            .applyInMemoryFilters(filter)
                    }
            }
            filter.searchQuery != null -> {
                viewsQueries.selectBySearch(filter.searchQuery, filter.searchQuery)
                    .asFlow()
                    .mapToList(dispatcher)
                    .map { list ->
                        list.map(TransactionMapper::toDomain)
                            .applyInMemoryFilters(filter)
                    }
            }
            filter.onlyUnreviewed -> {
                viewsQueries.selectNeedingReview()
                    .asFlow()
                    .mapToList(dispatcher)
                    .map { list ->
                        list.map(TransactionMapper::toDomain)
                            .applyInMemoryFilters(filter)
                    }
            }
            else -> {
                viewsQueries.selectAllTransactions()
                    .asFlow()
                    .mapToList(dispatcher)
                    .map { list ->
                        list.map(TransactionMapper::toDomain)
                            .applyInMemoryFilters(filter)
                    }
            }
        }
    }

    override fun getTransaction(id: String): Flow<Transaction?> {
        return viewsQueries.selectTransactionById(id)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it?.let(TransactionMapper::toDomain) }
    }

    override fun getRecentTransactions(limit: Int): Flow<List<Transaction>> {
        return viewsQueries.selectRecentTransactions(limit.toLong())
            .asFlow()
            .mapToList(dispatcher)
            .map { list -> list.map(TransactionMapper::toDomain) }
    }

    override fun getTransactionsByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<Transaction>> {
        val startMillis = TransactionMapper.localDateToEpochMillis(startDate)
        val endMillis = TransactionMapper.localDateToEpochMillis(endDate) + (24 * 60 * 60 * 1000 - 1)
        return viewsQueries.selectByDateRange(startMillis, endMillis)
            .asFlow()
            .mapToList(dispatcher)
            .map { list -> list.map(TransactionMapper::toDomain) }
    }

    override fun getTransactionsByCategory(categoryId: String): Flow<List<Transaction>> {
        return viewsQueries.selectByCategory(categoryId)
            .asFlow()
            .mapToList(dispatcher)
            .map { list -> list.map(TransactionMapper::toDomain) }
    }

    override fun getTransactionsNeedingReview(): Flow<List<Transaction>> {
        return viewsQueries.selectNeedingReview()
            .asFlow()
            .mapToList(dispatcher)
            .map { list -> list.map(TransactionMapper::toDomain) }
    }

    override suspend fun insertTransaction(transaction: Transaction): String = withContext(dispatcher) {
        // Transactions are typically inserted via the import process
        // This would insert into imported_transaction table
        // For now, return the ID
        transaction.id
    }

    override suspend fun updateTransaction(transaction: Transaction) = withContext(dispatcher) {
        // Updates go to transaction_override table
        val now = Clock.System.now().toEpochMilliseconds()
        transactionOverrideQueries.upsert(
            transaction_id = transaction.id,
            category_id = transaction.categoryId,
            merchant_override = if (transaction.merchantDisplay != transaction.merchantNormalized) {
                transaction.merchantDisplay
            } else {
                null
            },
            notes = transaction.notes.takeIf { it.isNotBlank() },
            tags = TransactionMapper.tagsToJson(transaction.tags),
            is_excluded = if (transaction.isExcluded) 1L else 0L,
            exclude_reason = null,
            is_reviewed = if (transaction.isReviewed) 1L else 0L,
            reviewed_at = if (transaction.isReviewed) now else null,
            updated_at = now,
            sync_version = 1L
        )
    }

    override suspend fun deleteTransaction(id: String) = withContext(dispatcher) {
        // Soft delete by marking as excluded
        markAsExcluded(id, true)
    }

    override suspend fun updateCategory(transactionId: String, categoryId: String, confidence: Float, reason: String) =
        withContext(dispatcher) {
            val now = Clock.System.now().toEpochMilliseconds()
            // First ensure override record exists, then update category
            ensureOverrideExists(transactionId, now)
            transactionOverrideQueries.updateCategory(categoryId, now, transactionId)
        }

    override suspend fun markAsReviewed(transactionId: String) = withContext(dispatcher) {
        val now = Clock.System.now().toEpochMilliseconds()
        ensureOverrideExists(transactionId, now)
        transactionOverrideQueries.markReviewed(now, now, transactionId)
    }

    override suspend fun markAsExcluded(transactionId: String, excluded: Boolean) = withContext(dispatcher) {
        val now = Clock.System.now().toEpochMilliseconds()
        ensureOverrideExists(transactionId, now)
        if (excluded) {
            transactionOverrideQueries.exclude("other", now, transactionId)
        } else {
            // To un-exclude, we'd need to update is_excluded to 0
            // The schema doesn't have a dedicated query for this
            transactionOverrideQueries.upsert(
                transaction_id = transactionId,
                category_id = null,
                merchant_override = null,
                notes = null,
                tags = "[]",
                is_excluded = 0L,
                exclude_reason = null,
                is_reviewed = 0L,
                reviewed_at = null,
                updated_at = now,
                sync_version = 1L
            )
        }
    }

    override suspend fun updateNotes(transactionId: String, notes: String) = withContext(dispatcher) {
        val now = Clock.System.now().toEpochMilliseconds()
        // Get existing override and update with notes
        val existing = transactionOverrideQueries.selectByTransactionId(transactionId).executeAsOneOrNull()
        transactionOverrideQueries.upsert(
            transaction_id = transactionId,
            category_id = existing?.category_id,
            merchant_override = existing?.merchant_override,
            notes = notes,
            tags = existing?.tags ?: "[]",
            is_excluded = existing?.is_excluded ?: 0L,
            exclude_reason = existing?.exclude_reason,
            is_reviewed = existing?.is_reviewed ?: 0L,
            reviewed_at = existing?.reviewed_at,
            updated_at = now,
            sync_version = (existing?.sync_version ?: 0L) + 1
        )
    }

    override suspend fun countByAccount(accountId: String): Long = withContext(dispatcher) {
        viewsQueries.countTransactionsByAccount(accountId).executeAsOne()
    }

    override suspend fun countAll(): Long = withContext(dispatcher) {
        viewsQueries.countAllTransactions().executeAsOne()
    }

    /**
     * Ensure a transaction_override record exists for the given transaction.
     */
    private fun ensureOverrideExists(transactionId: String, now: Long) {
        val existing = transactionOverrideQueries.selectByTransactionId(transactionId).executeAsOneOrNull()
        if (existing == null) {
            transactionOverrideQueries.upsert(
                transaction_id = transactionId,
                category_id = null,
                merchant_override = null,
                notes = null,
                tags = "[]",
                is_excluded = 0L,
                exclude_reason = null,
                is_reviewed = 0L,
                reviewed_at = null,
                updated_at = now,
                sync_version = 1L
            )
        }
    }

    /**
     * Apply in-memory filters that can't be done efficiently in SQL.
     */
    private fun List<Transaction>.applyInMemoryFilters(filter: TransactionFilter): List<Transaction> {
        var result = this

        if (!filter.includeExcluded) {
            result = result.filter { !it.isExcluded }
        }

        filter.minAmount?.let { min ->
            result = result.filter { it.amount.minorUnits >= min.minorUnits }
        }

        filter.maxAmount?.let { max ->
            result = result.filter { it.amount.minorUnits <= max.minorUnits }
        }

        return result
    }
}
