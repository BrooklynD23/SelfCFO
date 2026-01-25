package com.ledgerlens.categorization

/**
 * Stub implementations for FeedbackLoopTest.
 *
 * These classes are placeholders to allow FeedbackLoopTest to compile.
 * They do NOT implement real functionality - the tests are @Ignored.
 *
 * TODO: Remove these stubs and implement real classes in a dedicated sprint.
 * See BUILD_AUDIT_REPORT.md Issue T5 for details.
 */

/**
 * Configuration for the feedback loop system (stub).
 */
data class FeedbackLoopConfig(
    val enableAutoRetrain: Boolean = false
)

/**
 * Factory for creating FeedbackLoop instances (stub).
 */
object FeedbackLoopFactory {
    fun create(config: FeedbackLoopConfig = FeedbackLoopConfig()): FeedbackLoop {
        return FeedbackLoop(config)
    }
}

/**
 * The feedback loop system for processing user corrections (stub).
 */
class FeedbackLoop(private val config: FeedbackLoopConfig) {
    private val processor = CorrectionProcessor(
        InMemoryCorrectionRepository(),
        InMemoryPredictionStatsRepository(),
        IncrementalLearner(NaiveBayesClassifier())
    )

    suspend fun submitCorrection(
        transactionId: String,
        features: TransactionFeatures,
        oldCategoryId: String,
        newCategoryId: String,
        confidence: Float,
        classifierUsed: String
    ): FeedbackSubmissionResult {
        val result = processor.processCorrection(
            transactionId, features, oldCategoryId, newCategoryId, confidence, classifierUsed
        )
        return FeedbackSubmissionResult(result)
    }

    suspend fun submitBatch(corrections: List<PendingCorrection>): BatchFeedbackResult {
        val batchResult = processor.processBatch(corrections)
        val analysis = processor.analyzeCorrections()
        return BatchFeedbackResult(batchResult, analysis)
    }

    suspend fun getAnalysis(): CorrectionAnalysis = processor.analyzeCorrections()

    fun getStats(): FeedbackLoopStats = FeedbackLoopStats(0L)

    suspend fun triggerRetrain(): RetrainResult = RetrainResult.Success(0)
}

/**
 * Result of submitting a single correction.
 */
data class FeedbackSubmissionResult(
    val correctionResult: CorrectionResult
)

/**
 * Result of submitting a batch of corrections.
 */
data class BatchFeedbackResult(
    val batchResult: BatchCorrectionResult,
    val analysis: CorrectionAnalysis
)

/**
 * Statistics about the feedback loop.
 */
data class FeedbackLoopStats(
    val totalCorrectionsProcessed: Long
)

/**
 * Result of a retrain operation.
 */
sealed class RetrainResult {
    data class Success(val correctionsUsed: Int) : RetrainResult()
    data object Skipped : RetrainResult()
    data class Failed(val reason: String) : RetrainResult()
}

/**
 * Manager for user-created categorization rules (stub).
 */
class DefaultRuleManager {
    private val rules = mutableListOf<UserRule>()

    fun createRuleFromSuggestion(suggestion: SuggestedRule): UserRule? {
        val rule = UserRule(
            id = "rule_${rules.size + 1}",
            merchantPattern = suggestion.merchantPattern,
            categoryId = suggestion.categoryId,
            enabled = true
        )
        rules.add(rule)
        return rule
    }

    fun getUserRules(): List<UserRule> = rules.toList()

    fun setRuleEnabled(ruleId: String, enabled: Boolean) {
        rules.find { it.id == ruleId }?.let {
            val index = rules.indexOf(it)
            rules[index] = it.copy(enabled = enabled)
        }
    }

    fun deleteRule(ruleId: String): Boolean {
        return rules.removeAll { it.id == ruleId }
    }
}

/**
 * A user-created categorization rule (stub).
 */
data class UserRule(
    val id: String,
    val merchantPattern: String,
    val categoryId: String,
    val enabled: Boolean
)
