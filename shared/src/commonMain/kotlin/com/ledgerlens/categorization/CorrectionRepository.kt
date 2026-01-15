package com.ledgerlens.categorization

import kotlinx.datetime.Instant

/**
 * Repository interface for storing and querying category corrections.
 */
interface CorrectionRepository {
    suspend fun save(correction: CategoryCorrection)
    suspend fun getByTransactionId(transactionId: String): List<CategoryCorrection>
    suspend fun getByMerchant(merchantNormalized: String): List<CategoryCorrection>
    suspend fun getByOldCategory(categoryId: String): List<CategoryCorrection>
    suspend fun getByNewCategory(categoryId: String): List<CategoryCorrection>
    suspend fun getInTimeRange(start: Instant, end: Instant): List<CategoryCorrection>
    suspend fun getRecent(limit: Int): List<CategoryCorrection>
    suspend fun getAll(): List<CategoryCorrection>
    suspend fun count(): Int
    suspend fun countByMerchant(merchantNormalized: String): Int
    suspend fun delete(id: String)
    suspend fun deleteOlderThan(timestamp: Instant): Int
    suspend fun clear()
}

interface PredictionStatsRepository {
    suspend fun recordPrediction(merchantNormalized: String, categoryId: String, confidence: Float, wasCorrect: Boolean)
    suspend fun getMerchantStats(merchantNormalized: String): CorrectionStats?
    suspend fun getCategoryStats(categoryId: String): CorrectionStats?
    suspend fun getAllMerchantStats(): Map<String, CorrectionStats>
    suspend fun getAllCategoryStats(): Map<String, CorrectionStats>
    suspend fun clear()
}
