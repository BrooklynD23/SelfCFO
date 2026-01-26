package com.ledgerlens.categorization

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant

/**
 * In-memory implementation of CorrectionRepository.
 */
class InMemoryCorrectionRepository : CorrectionRepository {
    private val mutex = Mutex()
    private val corrections = mutableMapOf<String, CategoryCorrection>()

    override suspend fun save(correction: CategoryCorrection) = mutex.withLock {
        corrections[correction.id] = correction
    }

    override suspend fun getByTransactionId(transactionId: String): List<CategoryCorrection> = mutex.withLock {
        corrections.values.filter { it.transactionId == transactionId }.sortedByDescending { it.timestamp }
    }

    override suspend fun getByMerchant(merchantNormalized: String): List<CategoryCorrection> = mutex.withLock {
        val normalized = merchantNormalized.lowercase()
        corrections.values.filter { it.features.merchantNormalized.lowercase() == normalized }
            .sortedByDescending { it.timestamp }
    }

    override suspend fun getByOldCategory(categoryId: String): List<CategoryCorrection> = mutex.withLock {
        corrections.values.filter { it.oldCategoryId == categoryId }.sortedByDescending { it.timestamp }
    }

    override suspend fun getByNewCategory(categoryId: String): List<CategoryCorrection> = mutex.withLock {
        corrections.values.filter { it.newCategoryId == categoryId }.sortedByDescending { it.timestamp }
    }

    override suspend fun getInTimeRange(start: Instant, end: Instant): List<CategoryCorrection> = mutex.withLock {
        corrections.values.filter { it.timestamp >= start && it.timestamp <= end }
            .sortedByDescending { it.timestamp }
    }

    override suspend fun getRecent(limit: Int): List<CategoryCorrection> = mutex.withLock {
        corrections.values.sortedByDescending { it.timestamp }.take(limit)
    }

    override suspend fun getAll(): List<CategoryCorrection> = mutex.withLock {
        corrections.values.sortedByDescending { it.timestamp }.toList()
    }

    override suspend fun count(): Int = mutex.withLock { corrections.size }

    override suspend fun countByMerchant(merchantNormalized: String): Int = mutex.withLock {
        val normalized = merchantNormalized.lowercase()
        corrections.values.count { it.features.merchantNormalized.lowercase() == normalized }
    }

    override suspend fun delete(id: String) = mutex.withLock {
        corrections.remove(id)
        Unit
    }

    override suspend fun deleteOlderThan(timestamp: Instant): Int = mutex.withLock {
        val toDelete = corrections.filter { it.value.timestamp < timestamp }.keys
        toDelete.forEach { corrections.remove(it) }
        toDelete.size
    }

    override suspend fun clear() = mutex.withLock { corrections.clear() }
}

/**
 * In-memory implementation of PredictionStatsRepository.
 */
class InMemoryPredictionStatsRepository : PredictionStatsRepository {
    private val mutex = Mutex()

    private data class StatsAccumulator(
        var totalPredictions: Int = 0,
        var totalCorrections: Int = 0,
        val correctionsByCategory: MutableMap<String, Int> = mutableMapOf(),
        var mostCommonCorrectionFrom: String? = null,
        var mostCommonCorrectionTo: String? = null,
        var mostCommonCorrectionCount: Int = 0
    )

    private val merchantStats = mutableMapOf<String, StatsAccumulator>()
    private val categoryStats = mutableMapOf<String, StatsAccumulator>()

    override suspend fun recordPrediction(
        merchantNormalized: String,
        categoryId: String,
        confidence: Float,
        wasCorrect: Boolean
    ) = mutex.withLock {
        val normalizedMerchant = merchantNormalized.lowercase()
        val mStats = merchantStats.getOrPut(normalizedMerchant) { StatsAccumulator() }
        mStats.totalPredictions++
        if (!wasCorrect) mStats.totalCorrections++

        val cStats = categoryStats.getOrPut(categoryId) { StatsAccumulator() }
        cStats.totalPredictions++
        if (!wasCorrect) cStats.totalCorrections++
    }

    fun recordCorrection(merchantNormalized: String, oldCategoryId: String, newCategoryId: String) {
        val normalizedMerchant = merchantNormalized.lowercase()
        val mStats = merchantStats.getOrPut(normalizedMerchant) { StatsAccumulator() }
        val corrCount = mStats.correctionsByCategory.getOrPut(newCategoryId) { 0 } + 1
        mStats.correctionsByCategory[newCategoryId] = corrCount
        if (corrCount > mStats.mostCommonCorrectionCount) {
            mStats.mostCommonCorrectionFrom = oldCategoryId
            mStats.mostCommonCorrectionTo = newCategoryId
            mStats.mostCommonCorrectionCount = corrCount
        }
    }

    override suspend fun getMerchantStats(merchantNormalized: String): CorrectionStats? = mutex.withLock {
        merchantStats[merchantNormalized.lowercase()]?.toCorrectionStats()
    }

    override suspend fun getCategoryStats(categoryId: String): CorrectionStats? = mutex.withLock {
        categoryStats[categoryId]?.toCorrectionStats()
    }

    override suspend fun getAllMerchantStats(): Map<String, CorrectionStats> = mutex.withLock {
        merchantStats.mapValues { it.value.toCorrectionStats() }
    }

    override suspend fun getAllCategoryStats(): Map<String, CorrectionStats> = mutex.withLock {
        categoryStats.mapValues { it.value.toCorrectionStats() }
    }

    override suspend fun clear() = mutex.withLock {
        merchantStats.clear()
        categoryStats.clear()
    }

    private fun StatsAccumulator.toCorrectionStats(): CorrectionStats = CorrectionStats(
        totalPredictions = totalPredictions,
        totalCorrections = totalCorrections,
        correctionsByCategory = correctionsByCategory.toMap(),
        mostCommonCorrection = if (mostCommonCorrectionFrom != null && mostCommonCorrectionTo != null) {
            mostCommonCorrectionFrom!! to mostCommonCorrectionTo!!
        } else {
            null
        }
    )
}
