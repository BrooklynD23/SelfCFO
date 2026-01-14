package com.ledgerlens.categorization

import kotlinx.datetime.Instant

/**
 * Interface for storing labeled transactions used for training classifiers.
 *
 * Implementations may use database, file storage, or in-memory storage.
 */
interface TrainingDataStore {
    /**
     * Store a labeled example for training.
     */
    suspend fun storeLabeledExample(example: LabeledExample)

    /**
     * Retrieve all labeled examples.
     */
    suspend fun getAllExamples(): List<LabeledExample>

    /**
     * Retrieve examples for a specific category.
     */
    suspend fun getExamplesForCategory(categoryId: String): List<LabeledExample>

    /**
     * Retrieve examples for a specific merchant.
     */
    suspend fun getExamplesForMerchant(merchantNormalized: String): List<LabeledExample>

    /**
     * Get the count of examples per category.
     */
    suspend fun getCategoryCounts(): Map<String, Int>

    /**
     * Get the total number of stored examples.
     */
    suspend fun getTotalCount(): Int

    /**
     * Clear all stored examples.
     */
    suspend fun clear()

    /**
     * Store a correction event when user changes a category.
     */
    suspend fun storeCorrectionEvent(event: CorrectionEvent)

    /**
     * Get recent correction events for analysis.
     */
    suspend fun getRecentCorrections(limit: Int = 100): List<CorrectionEvent>

    /**
     * Get merchant-category preferences derived from user corrections.
     */
    suspend fun getMerchantPreferences(): Map<String, MerchantCategoryPreference>

    /**
     * Update merchant-category preference after a correction.
     */
    suspend fun updateMerchantPreference(
        merchantNormalized: String,
        categoryId: String,
        confidence: Float
    )
}

/**
 * A labeled transaction example for training.
 */
data class LabeledExample(
    val id: String,
    val features: TransactionFeatures,
    val categoryId: String,
    val source: LabelSource,
    val timestamp: Instant,
    val transactionId: String? = null
)

/**
 * Source of the category label.
 */
enum class LabelSource {
    /** User manually assigned the category */
    USER_ASSIGNED,
    /** User corrected an auto-assigned category */
    USER_CORRECTION,
    /** System assigned with high confidence, user confirmed */
    AUTO_CONFIRMED,
    /** Imported from training data set */
    IMPORTED
}

/**
 * Record of a user correcting a category assignment.
 */
data class CorrectionEvent(
    val id: String,
    val transactionId: String,
    val previousCategoryId: String,
    val newCategoryId: String,
    val timestamp: Instant,
    val featuresSnapshot: TransactionFeatures
)

/**
 * Merchant-category preference learned from user behavior.
 */
data class MerchantCategoryPreference(
    val merchantNormalized: String,
    val categoryScores: Map<String, Float>,
    val totalObservations: Int,
    val lastUpdated: Instant
) {
    /**
     * Get the most likely category for this merchant.
     */
    fun getMostLikelyCategory(): String? {
        return categoryScores.maxByOrNull { it.value }?.key
    }

    /**
     * Get confidence for a specific category.
     */
    fun getConfidence(categoryId: String): Float {
        return categoryScores[categoryId] ?: 0f
    }
}

/**
 * In-memory implementation of TrainingDataStore for testing and simple use cases.
 */
class InMemoryTrainingDataStore : TrainingDataStore {
    private val examples = mutableListOf<LabeledExample>()
    private val corrections = mutableListOf<CorrectionEvent>()
    private val merchantPreferences = mutableMapOf<String, MerchantCategoryPreference>()

    override suspend fun storeLabeledExample(example: LabeledExample) {
        examples.add(example)
    }

    override suspend fun getAllExamples(): List<LabeledExample> = examples.toList()

    override suspend fun getExamplesForCategory(categoryId: String): List<LabeledExample> {
        return examples.filter { it.categoryId == categoryId }
    }

    override suspend fun getExamplesForMerchant(merchantNormalized: String): List<LabeledExample> {
        return examples.filter { 
            it.features.merchantNormalized.equals(merchantNormalized, ignoreCase = true) 
        }
    }

    override suspend fun getCategoryCounts(): Map<String, Int> {
        return examples.groupingBy { it.categoryId }.eachCount()
    }

    override suspend fun getTotalCount(): Int = examples.size

    override suspend fun clear() {
        examples.clear()
        corrections.clear()
        merchantPreferences.clear()
    }

    override suspend fun storeCorrectionEvent(event: CorrectionEvent) {
        corrections.add(event)
    }

    override suspend fun getRecentCorrections(limit: Int): List<CorrectionEvent> {
        return corrections.sortedByDescending { it.timestamp }.take(limit)
    }

    override suspend fun getMerchantPreferences(): Map<String, MerchantCategoryPreference> {
        return merchantPreferences.toMap()
    }

    override suspend fun updateMerchantPreference(
        merchantNormalized: String,
        categoryId: String,
        confidence: Float
    ) {
        val existing = merchantPreferences[merchantNormalized]
        val newScores = (existing?.categoryScores?.toMutableMap() ?: mutableMapOf()).apply {
            this[categoryId] = confidence
        }
        // Normalize scores
        val total = newScores.values.sum()
        val normalizedScores = if (total > 0) {
            newScores.mapValues { it.value / total }
        } else {
            newScores
        }

        merchantPreferences[merchantNormalized] = MerchantCategoryPreference(
            merchantNormalized = merchantNormalized,
            categoryScores = normalizedScores,
            totalObservations = (existing?.totalObservations ?: 0) + 1,
            lastUpdated = kotlinx.datetime.Clock.System.now()
        )
    }
}
