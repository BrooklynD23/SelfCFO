package com.ledgerlens.categorization

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory implementation of MerchantPriorRepository.
 * 
 * This implementation stores priors in memory and is suitable for:
 * - Testing
 * - Small datasets
 * - As a cache layer in front of persistent storage
 * 
 * For production use with SQLDelight, extend this or create a separate
 * implementation that delegates to SQLDelight queries.
 * 
 * @param timeProvider Function to get current time in milliseconds (for testability)
 */
class MerchantPriorRepositoryImpl(
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : MerchantPriorRepository {

    private val mutex = Mutex()
    private val priors = mutableMapOf<String, MerchantPrior>()

    override suspend fun getPrior(merchantId: String): MerchantPrior? {
        return mutex.withLock {
            priors[merchantId.lowercase()]
        }
    }

    override suspend fun getPriors(merchantIds: List<String>): Map<String, MerchantPrior> {
        return mutex.withLock {
            merchantIds
                .map { it.lowercase() }
                .mapNotNull { id -> priors[id]?.let { id to it } }
                .toMap()
        }
    }

    override suspend fun savePrior(prior: MerchantPrior) {
        mutex.withLock {
            priors[prior.merchantId.lowercase()] = prior
        }
    }

    override suspend fun savePriors(priors: List<MerchantPrior>) {
        mutex.withLock {
            priors.forEach { prior ->
                this.priors[prior.merchantId.lowercase()] = prior
            }
        }
    }

    override suspend fun recordCategoryAssignment(
        merchantId: String,
        categoryId: String,
        transactionDateMs: Long
    ) {
        mutex.withLock {
            val normalizedId = merchantId.lowercase()
            val existing = priors[normalizedId]
            val currentTime = timeProvider()

            if (existing == null) {
                priors[normalizedId] = MerchantPrior(
                    merchantId = normalizedId,
                    categoryCounts = mapOf(categoryId to 1),
                    lastCategoryId = categoryId,
                    totalTransactions = 1,
                    lastUpdatedEpochMs = currentTime
                )
            } else {
                val newCounts = existing.categoryCounts.toMutableMap()
                newCounts[categoryId] = (newCounts[categoryId] ?: 0) + 1

                priors[normalizedId] = existing.copy(
                    categoryCounts = newCounts,
                    lastCategoryId = categoryId,
                    totalTransactions = existing.totalTransactions + 1,
                    lastUpdatedEpochMs = currentTime
                )
            }
        }
    }

    override suspend fun recordCategoryAssignments(assignments: List<CategoryAssignment>) {
        mutex.withLock {
            val currentTime = timeProvider()

            for (assignment in assignments) {
                val normalizedId = assignment.merchantId.lowercase()
                val existing = priors[normalizedId]

                if (existing == null) {
                    priors[normalizedId] = MerchantPrior(
                        merchantId = normalizedId,
                        categoryCounts = mapOf(assignment.categoryId to 1),
                        lastCategoryId = assignment.categoryId,
                        totalTransactions = 1,
                        lastUpdatedEpochMs = currentTime
                    )
                } else {
                    val newCounts = existing.categoryCounts.toMutableMap()
                    newCounts[assignment.categoryId] = (newCounts[assignment.categoryId] ?: 0) + 1

                    priors[normalizedId] = existing.copy(
                        categoryCounts = newCounts,
                        lastCategoryId = assignment.categoryId,
                        totalTransactions = existing.totalTransactions + 1,
                        lastUpdatedEpochMs = currentTime
                    )
                }
            }
        }
    }

    override suspend fun deletePrior(merchantId: String) {
        mutex.withLock {
            priors.remove(merchantId.lowercase())
        }
    }

    override suspend fun getMerchantsForCategory(categoryId: String): List<String> {
        return mutex.withLock {
            priors.values
                .filter { it.categoryCounts.containsKey(categoryId) }
                .map { it.merchantId }
        }
    }

    override suspend fun getTopMerchants(limit: Int): List<MerchantPrior> {
        return mutex.withLock {
            priors.values
                .sortedByDescending { it.totalTransactions }
                .take(limit)
        }
    }

    override suspend fun clearAll() {
        mutex.withLock {
            priors.clear()
        }
    }

    override suspend fun count(): Int {
        return mutex.withLock {
            priors.size
        }
    }
}
