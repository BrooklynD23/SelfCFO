package com.ledgerlens.categorization

import com.ledgerlens.categorization.pipeline.ClassificationRule
import com.ledgerlens.categorization.pipeline.MerchantContainsRule
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Orchestrates the correction → learning → model update feedback loop.
 */
class FeedbackLoop(
    private val correctionProcessor: CorrectionProcessor,
    private val batchRetrainer: BatchRetrainer,
    private val ruleManager: RuleManager? = null,
    private val config: FeedbackLoopConfig = FeedbackLoopConfig(),
    private val clock: Clock = Clock.System
) {
    private val mutex = Mutex()
    private var totalCorrectionsProcessed: Long = 0
    private var lastAnalysisTime = clock.now()

    suspend fun submitCorrection(
        transactionId: String,
        features: TransactionFeatures,
        oldCategoryId: String,
        newCategoryId: String,
        originalConfidence: Float,
        classifierUsed: String
    ): FeedbackResult = mutex.withLock {
        val correctionResult = correctionProcessor.processCorrection(
            transactionId, features, oldCategoryId, newCategoryId, originalConfidence, classifierUsed
        )

        totalCorrectionsProcessed++
        batchRetrainer.recordCorrection()

        val retrainResult = if (config.enableAutoRetrain) checkAndRetrain() else null
        val ruleAction = correctionResult.suggestedRule?.let { handleSuggestedRule(it) }
        val analysis = if (shouldRunAnalysis()) runAnalysis() else null

        FeedbackResult(correctionResult, retrainResult, ruleAction, analysis)
    }

    suspend fun submitBatch(corrections: List<PendingCorrection>): BatchFeedbackResult = mutex.withLock {
        val batchResult = correctionProcessor.processBatch(corrections)
        totalCorrectionsProcessed += batchResult.successCount
        repeat(batchResult.successCount) { batchRetrainer.recordCorrection() }

        val retrainResult = if (config.enableAutoRetrain) checkAndRetrain() else null
        val analysis = runAnalysis()

        BatchFeedbackResult(batchResult, retrainResult, analysis, analysis.suggestedRules)
    }

    suspend fun triggerRetrain(): RetrainResult = mutex.withLock { batchRetrainer.retrain() }

    suspend fun getAnalysis(): CorrectionAnalysis = mutex.withLock { correctionProcessor.analyzeCorrections() }

    suspend fun getStats(): FeedbackLoopStats = mutex.withLock {
        val retrainDecision = batchRetrainer.shouldRetrain()
        FeedbackLoopStats(
            totalCorrectionsProcessed, retrainDecision.shouldRetrain,
            when (retrainDecision) {
                is RetrainDecision.NotNeeded -> null
                is RetrainDecision.TimeThresholdExceeded -> "Time threshold exceeded"
                is RetrainDecision.CorrectionThresholdExceeded -> "Correction count threshold exceeded"
                is RetrainDecision.HighRecentCorrectionRate -> "High recent correction rate"
            }
        )
    }

    private suspend fun checkAndRetrain(): RetrainResult? {
        val decision = batchRetrainer.shouldRetrain()
        return if (decision.shouldRetrain) batchRetrainer.retrain() else null
    }

    private fun handleSuggestedRule(suggested: SuggestedRule): RuleAction {
        if (ruleManager == null) return RuleAction.Suggested(suggested)
        if (config.enableAutoRuleCreation && suggested.confidence >= config.autoRuleConfidenceThreshold &&
            suggested.supportingCorrections >= config.autoRuleMinSupport
        ) {
            val rule = ruleManager.createRuleFromSuggestion(suggested)
            return if (rule != null) RuleAction.Created(rule, suggested) else RuleAction.Suggested(suggested)
        }
        return RuleAction.Suggested(suggested)
    }

    private fun shouldRunAnalysis(): Boolean = (clock.now() - lastAnalysisTime) >= config.analysisInterval

    private suspend fun runAnalysis(): CorrectionAnalysis {
        lastAnalysisTime = clock.now()
        return correctionProcessor.analyzeCorrections()
    }
}

data class FeedbackLoopConfig(
    val enableAutoRetrain: Boolean = true,
    val enableAutoRuleCreation: Boolean = false,
    val autoRuleConfidenceThreshold: Float = 0.9f,
    val autoRuleMinSupport: Int = 5,
    val analysisInterval: Duration = 1.hours
)

data class FeedbackResult(
    val correctionResult: CorrectionResult,
    val retrainResult: RetrainResult?,
    val ruleAction: RuleAction?,
    val analysis: CorrectionAnalysis?
)

data class BatchFeedbackResult(
    val batchResult: BatchCorrectionResult,
    val retrainResult: RetrainResult?,
    val analysis: CorrectionAnalysis,
    val suggestedRules: List<SuggestedRule>
)

data class FeedbackLoopStats(val totalCorrectionsProcessed: Long, val pendingRetrain: Boolean, val retrainReason: String?)

sealed class RuleAction {
    data class Suggested(val rule: SuggestedRule) : RuleAction()
    data class Created(val rule: ClassificationRule, val fromSuggestion: SuggestedRule) : RuleAction()
}

interface RuleManager {
    fun createRuleFromSuggestion(suggestion: SuggestedRule): ClassificationRule?
    fun getUserRules(): List<ClassificationRule>
    fun setRuleEnabled(ruleId: String, enabled: Boolean)
    fun deleteRule(ruleId: String): Boolean
}

class DefaultRuleManager : RuleManager {
    private val userRules = mutableMapOf<String, ClassificationRule>()
    private var ruleCounter = 0

    override fun createRuleFromSuggestion(suggestion: SuggestedRule): ClassificationRule {
        val ruleId = "user_rule_${++ruleCounter}"
        val rule = MerchantContainsRule(
            ruleId, suggestion.categoryId,
            listOf(suggestion.merchantPattern.lowercase()), suggestion.confidence.coerceAtMost(0.95f), 95, true
        )
        userRules[ruleId] = rule
        return rule
    }

    override fun getUserRules(): List<ClassificationRule> = userRules.values.toList()

    override fun setRuleEnabled(ruleId: String, enabled: Boolean) {
        val rule = userRules[ruleId] as? MerchantContainsRule ?: return
        userRules[ruleId] = rule.copy(enabled = enabled)
    }

    override fun deleteRule(ruleId: String): Boolean = userRules.remove(ruleId) != null
}

object FeedbackLoopFactory {
    fun create(
        classifier: NaiveBayesClassifier = NaiveBayesClassifier(),
        correctionRepository: CorrectionRepository = InMemoryCorrectionRepository(),
        statsRepository: PredictionStatsRepository = InMemoryPredictionStatsRepository(),
        ruleManager: RuleManager? = DefaultRuleManager(),
        config: FeedbackLoopConfig = FeedbackLoopConfig()
    ): FeedbackLoop {
        val incrementalLearner = IncrementalLearner(classifier)
        val correctionProcessor = CorrectionProcessor(correctionRepository, statsRepository, incrementalLearner)
        val batchRetrainer = BatchRetrainer(correctionRepository, classifier)
        return FeedbackLoop(correctionProcessor, batchRetrainer, ruleManager, config)
    }
}
