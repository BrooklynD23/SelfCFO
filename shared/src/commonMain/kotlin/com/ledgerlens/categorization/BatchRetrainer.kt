package com.ledgerlens.categorization

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Handles periodic full retraining of the classifier from correction history.
 */
class BatchRetrainer(
    private val correctionRepository: CorrectionRepository,
    private val classifier: NaiveBayesClassifier,
    private val config: RetrainerConfig = RetrainerConfig(),
    private val clock: Clock = Clock.System
) {
    private var lastRetrainTime: Instant? = null
    private var correctionsSinceLastRetrain: Int = 0

    suspend fun shouldRetrain(): RetrainDecision {
        val timeSinceRetrain = lastRetrainTime?.let { clock.now() - it }

        if (timeSinceRetrain == null || timeSinceRetrain >= config.maxTimeBetweenRetrains) {
            return RetrainDecision.TimeThresholdExceeded(timeSinceRetrain ?: Duration.INFINITE)
        }

        if (correctionsSinceLastRetrain >= config.correctionCountThreshold) {
            return RetrainDecision.CorrectionThresholdExceeded(correctionsSinceLastRetrain)
        }

        val recentCorrections = correctionRepository.getInTimeRange(
            clock.now() - config.recentCorrectionWindow, clock.now()
        )
        if (recentCorrections.size >= config.recentCorrectionThreshold) {
            return RetrainDecision.HighRecentCorrectionRate(recentCorrections.size)
        }

        return RetrainDecision.NotNeeded
    }

    suspend fun retrain(): RetrainResult {
        val startTime = clock.now()
        val corrections = correctionRepository.getAll().filter { it.isActualCorrection }

        if (corrections.isEmpty()) return RetrainResult.NoData

        val relevantCorrections = if (config.useTimeWeighting) {
            corrections.filter { it.timestamp >= clock.now() - config.maxCorrectionAge }
        } else {
            corrections
        }

        val trainingData = mutableListOf<Pair<TransactionFeatures, String>>()
        for (correction in relevantCorrections) {
            val weight = calculateCorrectionWeight(correction)
            repeat(weight) { trainingData.add(correction.features to correction.newCategoryId) }
        }

        var newModel = NaiveBayesModel()
        for ((features, categoryId) in trainingData) {
            newModel = newModel.addExample(features, categoryId)
        }

        val finalModel = if (config.mergeWithExisting && !classifier.getModel().isEmpty()) {
            mergeModels(classifier.getModel(), newModel, config.existingModelWeight)
        } else {
            newModel
        }

        classifier.loadModel(finalModel)
        lastRetrainTime = clock.now()
        correctionsSinceLastRetrain = 0

        return RetrainResult.Success(
            relevantCorrections.size, trainingData.size,
            (clock.now() - startTime).inWholeMilliseconds,
            finalModel.categories.size, finalModel.vocabularySize
        )
    }

    fun recordCorrection() {
        correctionsSinceLastRetrain++
    }

    private fun calculateCorrectionWeight(correction: CategoryCorrection): Int {
        var weight = 1
        if (correction.wasHighConfidenceMiss) weight += 2
        if (config.useTimeWeighting && (clock.now() - correction.timestamp) < 1.days) weight += 1
        return weight.coerceIn(1, config.maxCorrectionWeight)
    }

    private fun mergeModels(existing: NaiveBayesModel, newModel: NaiveBayesModel, existingWeight: Float): NaiveBayesModel {
        if (existing.isEmpty()) return newModel
        if (newModel.isEmpty()) return existing

        val newWeight = 1.0f - existingWeight
        val mergedCategories = existing.categories + newModel.categories

        val mergedCategoryCounts = mutableMapOf<String, Int>()
        for (cat in mergedCategories) {
            mergedCategoryCounts[cat] = (
                (existing.categoryCounts[cat] ?: 0) * existingWeight +
                    (newModel.categoryCounts[cat] ?: 0) * newWeight
                ).toInt()
        }

        val mergedMerchantPriors = mutableMapOf<String, Map<String, Float>>()
        for (merchant in existing.merchantPriors.keys + newModel.merchantPriors.keys) {
            val existingPriors = existing.merchantPriors[merchant] ?: emptyMap()
            val newPriors = newModel.merchantPriors[merchant] ?: emptyMap()
            val merged = mutableMapOf<String, Float>()
            for (cat in existingPriors.keys + newPriors.keys) {
                merged[cat] = (existingPriors[cat] ?: 0f) * existingWeight + (newPriors[cat] ?: 0f) * newWeight
            }
            val total = merged.values.sum()
            if (total > 0) merged.keys.forEach { k -> merged[k] = merged[k]!! / total }
            mergedMerchantPriors[merchant] = merged
        }

        return NaiveBayesModel(
            categories = mergedCategories,
            categoryCounts = mergedCategoryCounts,
            totalExamples = ((existing.totalExamples * existingWeight + newModel.totalExamples * newWeight).toInt()),
            tokenCategoryFrequencies = existing.tokenCategoryFrequencies,
            categoryTokenCounts = existing.categoryTokenCounts,
            vocabularySize = existing.vocabularySize,
            merchantPriors = mergedMerchantPriors,
            amountBucketCounts = existing.amountBucketCounts
        )
    }
}

data class RetrainerConfig(
    val maxTimeBetweenRetrains: Duration = 7.days,
    val correctionCountThreshold: Int = 50,
    val recentCorrectionWindow: Duration = 24.hours,
    val recentCorrectionThreshold: Int = 10,
    val useTimeWeighting: Boolean = true,
    val maxCorrectionAge: Duration = 90.days,
    val maxCorrectionWeight: Int = 5,
    val mergeWithExisting: Boolean = true,
    val existingModelWeight: Float = 0.3f
)

sealed class RetrainDecision {
    data object NotNeeded : RetrainDecision()
    data class TimeThresholdExceeded(val timeSinceRetrain: Duration) : RetrainDecision()
    data class CorrectionThresholdExceeded(val correctionCount: Int) : RetrainDecision()
    data class HighRecentCorrectionRate(val recentCount: Int) : RetrainDecision()
    val shouldRetrain: Boolean get() = this !is NotNeeded
}

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
