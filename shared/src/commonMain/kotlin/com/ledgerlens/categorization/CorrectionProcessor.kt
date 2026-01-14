package com.ledgerlens.categorization

import kotlinx.datetime.Clock

/**
 * Processes user corrections and coordinates learning updates.
 */
class CorrectionProcessor(
    private val correctionRepository: CorrectionRepository,
    private val statsRepository: PredictionStatsRepository,
    private val incrementalLearner: IncrementalLearner,
    private val clock: Clock = Clock.System,
    private val idGenerator: () -> String = { generateCorrectionId() }
) {
    suspend fun processCorrection(
        transactionId: String,
        features: TransactionFeatures,
        oldCategoryId: String,
        newCategoryId: String,
        originalConfidence: Float,
        classifierUsed: String
    ): CorrectionResult {
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

        correctionRepository.save(correction)

        if (statsRepository is InMemoryPredictionStatsRepository) {
            statsRepository.recordCorrection(features.merchantNormalized, oldCategoryId, newCategoryId)
        }

        val learningResult = if (correction.isActualCorrection) {
            incrementalLearner.learnFromCorrection(correction)
        } else {
            incrementalLearner.reinforcePrediction(features, newCategoryId)
            LearningResult.Reinforced
        }

        val merchantCorrectionCount = correctionRepository.countByMerchant(features.merchantNormalized)
        val suggestedRule = if (merchantCorrectionCount >= RULE_SUGGESTION_THRESHOLD && correction.isActualCorrection) {
            analyzeForRuleSuggestion(features.merchantNormalized)
        } else null

        return CorrectionResult(correction, learningResult, suggestedRule, merchantCorrectionCount)
    }

    suspend fun processBatch(corrections: List<PendingCorrection>): BatchCorrectionResult {
        val results = mutableListOf<CorrectionResult>()
        var successCount = 0
        var failureCount = 0

        for (pending in corrections) {
            try {
                results.add(processCorrection(
                    pending.transactionId, pending.features, pending.oldCategoryId,
                    pending.newCategoryId, pending.originalConfidence, pending.classifierUsed
                ))
                successCount++
            } catch (e: Exception) { failureCount++ }
        }
        return BatchCorrectionResult(results, successCount, failureCount)
    }

    private suspend fun analyzeForRuleSuggestion(merchantNormalized: String): SuggestedRule? {
        val corrections = correctionRepository.getByMerchant(merchantNormalized).filter { it.isActualCorrection }
        if (corrections.size < RULE_SUGGESTION_THRESHOLD) return null

        val categoryFrequency = corrections.groupingBy { it.newCategoryId }.eachCount()
        val (topCategory, count) = categoryFrequency.maxByOrNull { it.value } ?: return null
        val confidence = count.toFloat() / corrections.size
        if (confidence < MIN_RULE_CONFIDENCE) return null

        return SuggestedRule(merchantNormalized, topCategory, count, confidence,
            "User corrected $count of ${corrections.size} transactions to '$topCategory'")
    }

    suspend fun analyzeCorrections(): CorrectionAnalysis {
        val allCorrections = correctionRepository.getAll()
        val actualCorrections = allCorrections.filter { it.isActualCorrection }

        val merchantGroups = actualCorrections.groupBy { it.features.merchantNormalized.lowercase() }
        val merchantStats = merchantGroups.mapValues { (_, corrections) ->
            val categoryFreq = corrections.groupingBy { it.newCategoryId }.eachCount()
            val mostCommon = categoryFreq.maxByOrNull { it.value }
            CorrectionStats(corrections.size, corrections.size, categoryFreq,
                mostCommon?.let { (cat, _) -> corrections.first { it.newCategoryId == cat }.oldCategoryId to cat })
        }

        val categoryGroups = actualCorrections.groupBy { it.oldCategoryId }
        val categoryStats = categoryGroups.mapValues { (_, corrections) ->
            val targetFreq = corrections.groupingBy { it.newCategoryId }.eachCount()
            val mostCommon = targetFreq.maxByOrNull { it.value }
            CorrectionStats(corrections.size, corrections.size, targetFreq,
                mostCommon?.let { (cat, _) -> corrections.first().oldCategoryId to cat })
        }

        val suggestedRules = merchantStats
            .filter { it.value.totalCorrections >= RULE_SUGGESTION_THRESHOLD }
            .mapNotNull { (merchant, stats) ->
                val (_, targetCategory) = stats.mostCommonCorrection ?: return@mapNotNull null
                val confidence = (stats.correctionsByCategory[targetCategory] ?: 0).toFloat() / stats.totalCorrections
                if (confidence >= MIN_RULE_CONFIDENCE) SuggestedRule(merchant, targetCategory,
                    stats.correctionsByCategory[targetCategory] ?: 0, confidence, "Consistent corrections")
                else null
            }.sortedByDescending { it.supportingCorrections }

        return CorrectionAnalysis(merchantStats, categoryStats, suggestedRules, actualCorrections.size,
            if (allCorrections.isNotEmpty()) actualCorrections.size.toFloat() / allCorrections.size else 0f)
    }

    companion object {
        const val RULE_SUGGESTION_THRESHOLD = 3
        const val MIN_RULE_CONFIDENCE = 0.7f
        private var correctionCounter = 0L
        fun generateCorrectionId(): String = "corr_${++correctionCounter}_${Clock.System.now().toEpochMilliseconds()}"
    }
}

data class CorrectionResult(
    val correction: CategoryCorrection,
    val learningResult: LearningResult,
    val suggestedRule: SuggestedRule?,
    val merchantCorrectionCount: Int
)

data class BatchCorrectionResult(val results: List<CorrectionResult>, val successCount: Int, val failureCount: Int)

data class PendingCorrection(
    val transactionId: String,
    val features: TransactionFeatures,
    val oldCategoryId: String,
    val newCategoryId: String,
    val originalConfidence: Float,
    val classifierUsed: String
)

sealed class LearningResult {
    data class Updated(val weightChanges: Int) : LearningResult()
    data object Reinforced : LearningResult()
    data object NoChange : LearningResult()
    data class Failed(val reason: String) : LearningResult()
}
