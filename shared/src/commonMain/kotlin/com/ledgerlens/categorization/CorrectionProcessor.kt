package com.ledgerlens.categorization
import kotlinx.datetime.Clock
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
        val correction =
            CategoryCorrection(idGenerator(), transactionId, oldCategoryId, newCategoryId, clock.now(), features, originalConfidence, classifierUsed)
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
        val merchantCount = correctionRepository.countByMerchant(features.merchantNormalized)
        val suggestedRule = if (merchantCount >= RULE_SUGGESTION_THRESHOLD && correction.isActualCorrection) analyzeForRuleSuggestion(features.merchantNormalized) else null
        return CorrectionResult(correction, learningResult, suggestedRule, merchantCount)
    }
    suspend fun processBatch(corrections: List<PendingCorrection>): BatchCorrectionResult {
        val results = mutableListOf<CorrectionResult>()
        var success = 0
        var failure = 0
        for (p in corrections) {
            try {
                results.add(processCorrection(p.transactionId, p.features, p.oldCategoryId, p.newCategoryId, p.originalConfidence, p.classifierUsed))
                success++
            } catch (
                e: Exception
            ) {
                failure++
            }
        }
        return BatchCorrectionResult(results, success, failure)
    }
    private suspend fun analyzeForRuleSuggestion(merchantNormalized: String): SuggestedRule? {
        val corrs = correctionRepository.getByMerchant(merchantNormalized).filter { it.isActualCorrection }
        if (corrs.size < RULE_SUGGESTION_THRESHOLD) return null
        val freq = corrs.groupingBy { it.newCategoryId }.eachCount()
        val (top, count) = freq.maxByOrNull { it.value } ?: return null
        val conf = count.toFloat() / corrs.size
        return if (conf >= MIN_RULE_CONFIDENCE) SuggestedRule(merchantNormalized, top, count, conf, "User corrected $count of ${corrs.size} to '$top'") else null
    }
    suspend fun analyzeCorrections(): CorrectionAnalysis {
        val all = correctionRepository.getAll()
        val actual = all.filter { it.isActualCorrection }
        val merchantGroups = actual.groupBy { it.features.merchantNormalized.lowercase() }
        val merchantStats = merchantGroups.mapValues { (_, c) ->
            val f = c.groupingBy { it.newCategoryId }.eachCount()
            val m = f.maxByOrNull { it.value }
            CorrectionStats(c.size, c.size, f, m?.let { (cat, _) -> c.first { it.newCategoryId == cat }.oldCategoryId to cat })
        }
        val categoryGroups = actual.groupBy { it.oldCategoryId }
        val categoryStats = categoryGroups.mapValues { (_, c) ->
            val f = c.groupingBy { it.newCategoryId }.eachCount()
            val m = f.maxByOrNull { it.value }
            CorrectionStats(c.size, c.size, f, m?.let { (cat, _) -> c.first().oldCategoryId to cat })
        }
        val suggested = merchantStats.filter { it.value.totalCorrections >= RULE_SUGGESTION_THRESHOLD }.mapNotNull { (m, s) ->
            val (_, target) = s.mostCommonCorrection ?: return@mapNotNull null
            val conf = (s.correctionsByCategory[target] ?: 0).toFloat() / s.totalCorrections
            if (conf >= MIN_RULE_CONFIDENCE) SuggestedRule(m, target, s.correctionsByCategory[target] ?: 0, conf, "Consistent corrections") else null
        }.sortedByDescending { it.supportingCorrections }
        return CorrectionAnalysis(merchantStats, categoryStats, suggested, actual.size, if (all.isNotEmpty()) actual.size.toFloat() / all.size else 0f)
    }
    companion object {
        const val RULE_SUGGESTION_THRESHOLD = 3
        const val MIN_RULE_CONFIDENCE = 0.7f
        private var counter = 0L
        fun generateCorrectionId() = "corr_${++counter}_${Clock.System.now().toEpochMilliseconds()}"
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
    data class Updated(
        val weightChanges: Int
    ) : LearningResult()
    data object Reinforced : LearningResult()
    data object NoChange : LearningResult()
    data class Failed(
        val reason: String
    ) : LearningResult()
}
