package com.ledgerlens.categorization

import kotlinx.datetime.Instant

/**
 * Repository interface for storing and querying category corrections.
 */
interface CorrectionRepository {
    /**
     * Save a new correction.
     */
    suspend fun save(correction: CategoryCorrection)

    /**
     * Get all corrections for a specific transaction.
     */
    suspend fun getByTransactionId(transactionId: String): List<CategoryCorrection>

    /**
     * Get corrections for a merchant (by normalized name).
     */
    suspend fun getByMerchant(merchantNormalized: String): List<CategoryCorrection>

    /**
     * Get corrections where a specific category was the predicted (old) category.
     */
    suspend fun getByOldCategory(categoryId: String): List<CategoryCorrection>

    /**
     * Get corrections where a specific category was selected (new) category.
     */
    suspend fun getByNewCategory(categoryId: String): List<CategoryCorrection>

    /**
     * Get all corrections within a time range.
     */
    suspend fun getInTimeRange(start: Instant, end: Instant): List<CategoryCorrection>

    /**
     * Get the most recent corrections, limited by count.
     */
    suspend fun getRecent(limit: Int): List<CategoryCorrection>

    /**
     * Get all corrections (use with caution for large datasets).
     */
    suspend fun getAll(): List<CategoryCorrection>

    /**
     * Get total count of corrections.
     */
    suspend fun count(): Int

    /**
     * Get count of corrections for a specific merchant.
     */
    suspend fun countByMerchant(merchantNormalized: String): Int

    /**
     * Delete a correction by ID.
     */
    suspend fun delete(id: String)

    /**
     * Delete all corrections older than a given timestamp.
     */
    suspend fun deleteOlderThan(timestamp: Instant): Int

    /**
     * Clear all corrections (for testing or reset).
     */
    suspend fun clear()
}

/**
 * Repository for tracking prediction statistics (for confidence adjustment).
 */
interface PredictionStatsRepository {
    /**
     * Record a prediction made by the classifier.
     */
    suspend fun recordPrediction(
        merchantNormalized: String,
        categoryId: String,
        confidence: Float,
        wasCorrect: Boolean
    )

    /**
     * Get stats for a merchant.
     */
    suspend fun getMerchantStats(merchantNormalized: String): CorrectionStats?

    /**
     * Get stats for a category.
     */
    suspend fun getCategoryStats(categoryId: String): CorrectionStats?

    /**
     * Get all merchant stats.
     */
    suspend fun getAllMerchantStats(): Map<String, CorrectionStats>

    /**
     * Get all category stats.
     */
    suspend fun getAllCategoryStats(): Map<String, CorrectionStats>

    /**
     * Clear all stats.
     */
    suspend fun clear()
}
