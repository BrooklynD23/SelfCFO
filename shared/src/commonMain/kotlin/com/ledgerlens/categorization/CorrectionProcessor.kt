package com.ledgerlens.categorization

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Processes user corrections and coordinates learning updates.
 *
 * This is the entry point for handling category corrections from the UI.
 * It validates corrections, stores them, and triggers appropriate learning updates.
 */
class CorrectionProcessor(
    private val correctionRepository: CorrectionRepository,
    private val statsRepository: PredictionStatsRepository,
    private val incrementalLearner: IncrementalLearner,
    private val clock: Clock = Clock.System,
    private val idGenerator: () -> String = { generateCorrectionId() }
) {
    /**
     * Process a user correction to a category prediction.
     *
     * @param transactionId The transaction being corrected
     * @param features The transaction features
     * @param oldCategoryId The predicted category that was rejected
     * @param newCategoryId The category the user selected
     * @param originalConfidence The confidence of the original prediction
     * @param classifierUsed Which classifier made the original prediction
     * @return The created correction record
     */
    suspend fun processCorrection(
        transactionId: String,
        features: TransactionFeatures,
        oldCategoryId: String,
        newCategoryId: String,
        originalConfidence: Float,
        classifierUsed: String
    ): CorrectionResult {
        // Create correction record
        val correction = CategoryCorrection(
            id = idGenerator(),
            transactionId = transactionId,
            oldCategoryId = oldCategoryId,
            newCategoryId = newCategoryId,
            timestamp = clock.now(),
            features = features,
            confidence = originalConfidence,
            classifierUsed = classifierUsed
        )

        // Store the correction
        correctionRepository.save(correction)

        // Update prediction stats
        if (statsRepository is InMemoryPredictionStatsRepository) {
            statsRepository.recordCorrection(
                features.merchantNormalized,
                oldCategoryId,
                newCategoryId
            )
        }

        // Perform incremental learning update
        val learningResult = if (correction.isActualCorrection) {
            incrementalLearner.learnFromCorrection(correction)
        } else {
            // User confirmed the prediction - still useful for reinforcement
            incrementalLearner.reinforcePrediction(features, newCategoryId)
            LearningResult.Reinforced
        }

        // Check if this merchant is now frequently corrected
        val merchantCorrectionCount = correctionRepository.countByMerchant(features.merchantNormalized)
        val shouldSuggestRule = merchantCorrectionCount >= RULE_SUGGESTION_THRESHOLD

        // Analyze for potential rule suggestions
        val suggestedRule = if (shouldSuggestRule && correction.isActualCorrection) {
            analyzeForRuleSuggestion(features.merchantNormalized)
        } else null

        return CorrectionResult(
            correction = correction,
            learningResult = learningResult,
            suggestedRule = suggestedRule,
            merchantCorrectionCount = merchantCorrectionCount
        )
    }

    /**
     * Process a batch of corrections (e.g., from import review).
     */
    suspend fun processBatch(corrections: List<PendingCorrection>): BatchCorrectionResult {
        val results = mutableListOf<CorrectionResult>()
        var successCount = 0
        var failureCount = 0

        for (pending in corrections) {
            try {
                val result = processCorrection(
                    transactionId = pending.transactionId,
                    features = pending.features,
                    oldCategoryId = pending.oldCategoryId,
                    newCategoryId = pending.newCategoryId,
                    originalConfidence = pending.originalConfidence,
                    classifierUsed = pending.classifierUsed
                )
                results.add(result)
                successCount++
            } catch (e: Exception) {
                failureCount++
            }
        }

        return BatchCorrectionResult(
            results = results,
            successCount = successCount,
            failureCount = failureCount
        )
    }

    /**
     * Analyze corrections for a merchant to suggest a new rule.
     */
    private suspend fun analyzeForRuleSuggestion(merchantNormalized: String): SuggestedRule? {
        val corrections = correctionRepository.getByMerchant(merchantNormalized)
            .filter { it.isActualCorrection }

        if (corrections.size < RULE_SUGGESTION_THRESHOLD) return null

        // Find the most common correction target
        val categoryFrequency = corrections
            .groupingBy { it.newCategoryId }
            .eachCount()

        val (topCategory, count) = categoryFrequency.maxByOrNull { it.value } ?: return null

        val confidence = count.toFloat() / corrections.size
        if (confidence < MIN_RULE_CONFIDENCE) return null

        return SuggestedRule(
            merchantPattern = merchantNormalized,
            suggestedCategoryId = topCategory,
            supportingCorrections = count,
            confidence = confidence,
            reason = "User corrected $count of ${corrections.size} transactions to '$topCategory'"
        )
    }

    /**
     * Get correction analysis for all tracked corrections.
     */
    suspend fun analyzeCorrections(): CorrectionAnalysis {
        val allCorrections = correctionRepository.getAll()
        val actualCorrections = allCorrections.filter { it.isActualCorrection }

        // Analyze by merchant
        val merchantGroups = actualCorrections.groupBy { it.features.merchantNormalized.lowercase() }
        val merchantStats = merchantGroups.mapValues { (_, corrections) ->
            val categoryFreq = corrections.groupingBy { it.newCategoryId }.eachCount()
            val mostCommon = categoryFreq.maxByOrNull { it.value }
            CorrectionStats(
                totalPredictions = corrections.size,
                totalCorrections = corrections.size,
                correctionsByCategory = categoryFreq,
                mostCommonCorrection = mostCommon?.let { (cat, _) ->
                    corrections.first { it.newCategoryId == cat }.oldCategoryId to cat
                }
            )
        }

        // Analyze by old category (what was predicted incorrectly)
        val categoryGroups = actualCorrections.groupBy { it.oldCategoryId }
        val categoryStats = categoryGroups.mapValues { (_, corrections) ->
            val targetFreq = corrections.groupingBy { it.newCategoryId }.eachCount()
            val mostCommon = targetFreq.maxByOrNull { it.value }
            CorrectionStats(
                totalPredictions = corrections.size,
                totalCorrections = corrections.size,
                correctionsByCategory = targetFreq,
                mostCommonCorrection = mostCommon?.let { (cat, _) ->
                    corrections.first().oldCategoryId to cat
                }
            )
        }

        // Generate suggested rules
        val suggestedRules = merchantStats
            .filter { it.value.totalCorrections >= RULE_SUGGESTION_THRESHOLD }
            .mapNotNull { (merchant, stats) ->
                val (_, targetCategory) = stats.mostCommonCorrection ?: return@mapNotNull null
                val confidence = (stats.correctionsByCategory[targetCategory] ?: 0).toFloat() / stats.totalCorrections
                if (confidence >= MIN_RULE_CONFIDENCE) {
                    SuggestedRule(
                        merchantPattern = merchant,
                        suggestedCategoryId = targetCategory,
                        supportingCorrections = stats.correctionsByCategory[targetCategory] ?: 0,
                        confidence = confidence,
                        reason = "Consistent corrections to '$targetCategory'"
                    )
                } else null
            }
            .sortedByDescending { it.supportingCorrections }

        return CorrectionAnalysis(
            merchantStats = merchantStats,
            categoryStats = categoryStats,
            suggestedRules = suggestedRules,
            totalCorrections = actualCorrections.size,
            averageCorrectionRate = if (allCorrections.isNotEmpty()) {
                actualCorrections.size.toFloat() / allCorrections.size
            } else 0f
        )
    }

    companion object {
        const val RULE_SUGGESTION_THRESHOLD = 3
        const val MIN_RULE_CONFIDENCE = 0.7f

        private var correctionCounter = 0L

        fun generateCorrectionId(): String {
            return "corr_${++correctionCounter}_${Clock.System.now().toEpochMilliseconds()}"
        }
    }
}

/**
 * Result of processing a single correction.
 */
data class CorrectionResult(
    val correction: CategoryCorrection,
    val learningResult: LearningResult,
    val suggestedRule: SuggestedRule?,
    val merchantCorrectionCount: Int
)

/**
 * Result of processing a batch of corrections.
 */
data class BatchCorrectionResult(
    val results: List<CorrectionResult>,
    val successCount: Int,
    val failureCount: Int
)

/**
 * Pending correction to be processed.
 */
data class PendingCorrection(
    val transactionId: String,
    val features: TransactionFeatures,
    val oldCategoryId: String,
    val newCategoryId: String,
    val originalConfidence: Float,
    val classifierUsed: String
)

/**
 * Result of a learning update.
 */
sealed class LearningResult {
    /** Model weights were updated based on the correction */
    data class Updated(val weightChanges: Int) : LearningResult()
    
    /** Prediction was reinforced (user confirmed it was correct) */
    data object Reinforced : LearningResult()
    
    /** No update needed (e.g., duplicate correction) */
    data object NoChange : LearningResult()
    
    /** Learning failed */
    data class Failed(val reason: String) : LearningResult()
}
