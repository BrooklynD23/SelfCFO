package com.ledgerlens.categorization

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Handles periodic full retraining of the classifier from correction history.
 *
 * While incremental learning handles immediate updates, batch retraining
 * periodically rebuilds the model to ensure optimal performance and
 * incorporate patterns that emerge over time.
 */
class BatchRetrainer(
    private val correctionRepository: CorrectionRepository,
    private val classifier: NaiveBayesClassifier,
    private val config: RetrainerConfig = RetrainerConfig(),
    private val clock: Clock = Clock.System
) {
    private var lastRetrainTime: Instant? = null
    private var correctionsSinceLastRetrain: Int = 0

    /**
     * Check if retraining is needed based on configured thresholds.
     */
    suspend fun shouldRetrain(): RetrainDecision {
        val timeSinceRetrain = lastRetrainTime?.let { clock.now() - it }
        val correctionCount = correctionRepository.count()

        // Check time-based threshold
        if (timeSinceRetrain == null || timeSinceRetrain >= config.maxTimeBetweenRetrains) {
            return RetrainDecision.TimeThresholdExceeded(timeSinceRetrain ?: Duration.INFINITE)
        }

        // Check correction count threshold
        if (correctionsSinceLastRetrain >= config.correctionCountThreshold) {
            return RetrainDecision.CorrectionThresholdExceeded(correctionsSinceLastRetrain)
        }

        // Check if recent correction rate is high
        val recentCorrections = correctionRepository.getInTimeRange(
            clock.now() - config.recentCorrectionWindow,
            clock.now()
        )
        if (recentCorrections.size >= config.recentCorrectionThreshold) {
            return RetrainDecision.HighRecentCorrectionRate(recentCorrections.size)
        }

        return RetrainDecision.NotNeeded
    }

    /**
     * Perform a full retrain of the classifier from correction history.
     */
    suspend fun retrain(): RetrainResult {
        val startTime = clock.now()
        val corrections = correctionRepository.getAll()
            .filter { it.isActualCorrection }

        if (corrections.isEmpty()) {
            return RetrainResult.NoData
        }

        // Filter to relevant corrections based on config
        val relevantCorrections = if (config.useTimeWeighting) {
            filterByTimeRelevance(corrections)
        } else {
            corrections
        }

        // Build training dataset from corrections
        val trainingData = buildTrainingDataset(relevantCorrections)

        // Create fresh model
        var newModel = NaiveBayesModel()

        // Train on all examples
        for ((features, categoryId) in trainingData) {
            newModel = newModel.addExample(features, categoryId)
        }

        // Merge with existing model if configured
        val finalModel = if (config.mergeWithExisting) {
            mergeModels(classifier.getModel(), newModel, config.existingModelWeight)
        } else {
            newModel
        }

        // Load the new model
        classifier.loadModel(finalModel)

        // Update tracking
        lastRetrainTime = clock.now()
        correctionsSinceLastRetrain = 0

        val duration = clock.now() - startTime

        return RetrainResult.Success(
            correctionsUsed = relevantCorrections.size,
            trainingExamples = trainingData.size,
            durationMs = duration.inWholeMilliseconds,
            modelCategories = finalModel.categories.size,
            vocabularySize = finalModel.vocabularySize
        )
    }

    /**
     * Record that a correction was processed (for threshold tracking).
     */
    fun recordCorrection() {
        correctionsSinceLastRetrain++
    }

    /**
     * Build training dataset from corrections.
     * Each correction contributes one or more training examples.
     */
    private fun buildTrainingDataset(corrections: List<CategoryCorrection>): List<Pair<TransactionFeatures, String>> {
        val dataset = mutableListOf<Pair<TransactionFeatures, String>>()

        for (correction in corrections) {
            // Add the correct category (what user selected)
            val weight = calculateCorrectionWeight(correction)
            repeat(weight) {
                dataset.add(correction.features to correction.newCategoryId)
            }
        }

        return dataset
    }

    /**
     * Calculate training weight for a correction.
     */
    private fun calculateCorrectionWeight(correction: CategoryCorrection): Int {
        var weight = 1

        // High-confidence mistakes get more weight
        if (correction.wasHighConfidenceMiss) {
            weight += 2
        }

        // Recent corrections get more weight if time-weighting enabled
        if (config.useTimeWeighting) {
            val age = clock.now() - correction.timestamp
            if (age < 1.days) {
                weight += 1
            }
        }

        return weight.coerceIn(1, config.maxCorrectionWeight)
    }

    /**
     * Filter corrections by time relevance for time-weighted training.
     */
    private fun filterByTimeRelevance(corrections: List<CategoryCorrection>): List<CategoryCorrection> {
        val cutoff = clock.now() - config.maxCorrectionAge
        return corrections.filter { it.timestamp >= cutoff }
    }

    /**
     * Merge two models with weighted combination.
     */
    private fun mergeModels(
        existing: NaiveBayesModel,
        newModel: NaiveBayesModel,
        existingWeight: Float
    ): NaiveBayesModel {
        if (existing.isEmpty()) return newModel
        if (newModel.isEmpty()) return existing

        val newWeight = 1.0f - existingWeight

        // Merge categories
        val mergedCategories = existing.categories + newModel.categories

        // Merge category counts (weighted)
        val mergedCategoryCounts = mutableMapOf<String, Int>()
        for (cat in mergedCategories) {
            val existingCount = existing.categoryCounts[cat] ?: 0
            val newCount = newModel.categoryCounts[cat] ?: 0
            mergedCategoryCounts[cat] = (existingCount * existingWeight + newCount * newWeight).toInt()
        }

        // Merge token frequencies
        val mergedTokenFreqs = mutableMapOf<String, Map<String, Int>>()
        val allTokens = existing.tokenCategoryFrequencies.keys + newModel.tokenCategoryFrequencies.keys
        for (token in allTokens) {
            val existingFreqs = existing.tokenCategoryFrequencies[token] ?: emptyMap()
            val newFreqs = newModel.tokenCategoryFrequencies[token] ?: emptyMap()
            val mergedFreqs = mutableMapOf<String, Int>()
            for (cat in existingFreqs.keys + newFreqs.keys) {
                val existingF = existingFreqs[cat] ?: 0
                val newF = newFreqs[cat] ?: 0
                mergedFreqs[cat] = (existingF * existingWeight + newF * newWeight).toInt()
            }
            mergedTokenFreqs[token] = mergedFreqs
        }

        // Merge merchant priors
        val mergedMerchantPriors = mutableMapOf<String, Map<String, Float>>()
        val allMerchants = existing.merchantPriors.keys + newModel.merchantPriors.keys
        for (merchant in allMerchants) {
            val existingPriors = existing.merchantPriors[merchant] ?: emptyMap()
            val newPriors = newModel.merchantPriors[merchant] ?: emptyMap()
            val merged = mutableMapOf<String, Float>()
            for (cat in existingPriors.keys + newPriors.keys) {
                val ep = existingPriors[cat] ?: 0f
                val np = newPriors[cat] ?: 0f
                merged[cat] = ep * existingWeight + np * newWeight
            }
            // Renormalize
            val total = merged.values.sum()
            if (total > 0) {
                merged.keys.forEach { k -> merged[k] = merged[k]!! / total }
            }
            mergedMerchantPriors[merchant] = merged
        }

        return NaiveBayesModel(
            categories = mergedCategories,
            categoryCounts = mergedCategoryCounts,
            totalExamples = (existing.totalExamples * existingWeight + newModel.totalExamples * newWeight).toInt(),
            tokenCategoryFrequencies = mergedTokenFreqs,
            categoryTokenCounts = existing.categoryTokenCounts, // Simplified - would need proper merge
            vocabularySize = mergedTokenFreqs.size,
            merchantPriors = mergedMerchantPriors,
            amountBucketCounts = existing.amountBucketCounts // Simplified
        )
    }
}

/**
 * Configuration for batch retraining behavior.
 */
data class RetrainerConfig(
    /** Maximum time between retrains */
    val maxTimeBetweenRetrains: Duration = 7.days,

    /** Number of corrections that trigger a retrain */
    val correctionCountThreshold: Int = 50,

    /** Time window to consider for recent corrections */
    val recentCorrectionWindow: Duration = 24.hours,

    /** Number of recent corrections that triggers urgent retrain */
    val recentCorrectionThreshold: Int = 10,

    /** Whether to use time-based weighting for training */
    val useTimeWeighting: Boolean = true,

    /** Maximum age of corrections to include in training */
    val maxCorrectionAge: Duration = 90.days,

    /** Maximum weight for a single correction */
    val maxCorrectionWeight: Int = 5,

    /** Whether to merge with existing model or replace it */
    val mergeWithExisting: Boolean = true,

    /** Weight given to existing model when merging (0-1) */
    val existingModelWeight: Float = 0.3f
)

/**
 * Decision about whether retraining is needed.
 */
sealed class RetrainDecision {
    data object NotNeeded : RetrainDecision()
    data class TimeThresholdExceeded(val timeSinceRetrain: Duration) : RetrainDecision()
    data class CorrectionThresholdExceeded(val correctionCount: Int) : RetrainDecision()
    data class HighRecentCorrectionRate(val recentCount: Int) : RetrainDecision()

    val shouldRetrain: Boolean get() = this !is NotNeeded
}

/**
 * Result of a batch retrain operation.
 */
sealed class RetrainResult {
    data object NoData : RetrainResult()

    data class Success(
        val correctionsUsed: Int,
        val trainingExamples: Int,
        val durationMs: Long,
        val modelCategories: Int,
        val vocabularySize: Int
    ) : RetrainResult()

    data class Failed(val reason: String) : RetrainResult()
}
