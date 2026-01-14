package com.ledgerlens.categorization
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
class BatchRetrainer(private val correctionRepository: CorrectionRepository, private val classifier: NaiveBayesClassifier, private val config: RetrainerConfig = RetrainerConfig(), private val clock: Clock = Clock.System) {
    private var lastRetrainTime: Instant? = null
    private var correctionsSinceLastRetrain: Int = 0
    suspend fun shouldRetrain(): RetrainDecision { val timeSince = lastRetrainTime?.let { clock.now() - it }; if (timeSince == null || timeSince >= config.maxTimeBetweenRetrains) return RetrainDecision.TimeThresholdExceeded(timeSince ?: Duration.INFINITE); if (correctionsSinceLastRetrain >= config.correctionCountThreshold) return RetrainDecision.CorrectionThresholdExceeded(correctionsSinceLastRetrain); val recent = correctionRepository.getInTimeRange(clock.now() - config.recentCorrectionWindow, clock.now()); if (recent.size >= config.recentCorrectionThreshold) return RetrainDecision.HighRecentCorrectionRate(recent.size); return RetrainDecision.NotNeeded }
    suspend fun retrain(): RetrainResult { val start = clock.now(); val corrs = correctionRepository.getAll().filter { it.isActualCorrection }; if (corrs.isEmpty()) return RetrainResult.NoData; val relevant = if (config.useTimeWeighting) corrs.filter { it.timestamp >= clock.now() - config.maxCorrectionAge } else corrs; val data = mutableListOf<Pair<TransactionFeatures, String>>(); for (c in relevant) { val w = if (c.wasHighConfidenceMiss) 3 else 1; repeat(w) { data.add(c.features to c.newCategoryId) } }; var newModel = NaiveBayesModel(); for ((f, cat) in data) newModel = newModel.addExample(f, cat); classifier.loadModel(newModel); lastRetrainTime = clock.now(); correctionsSinceLastRetrain = 0; return RetrainResult.Success(relevant.size, data.size, (clock.now() - start).inWholeMilliseconds, newModel.categories.size, newModel.vocabularySize) }
    fun recordCorrection() { correctionsSinceLastRetrain++ }
}
data class RetrainerConfig(val maxTimeBetweenRetrains: Duration = 7.days, val correctionCountThreshold: Int = 50, val recentCorrectionWindow: Duration = 24.hours, val recentCorrectionThreshold: Int = 10, val useTimeWeighting: Boolean = true, val maxCorrectionAge: Duration = 90.days)
sealed class RetrainDecision { data object NotNeeded : RetrainDecision(); data class TimeThresholdExceeded(val timeSinceRetrain: Duration) : RetrainDecision(); data class CorrectionThresholdExceeded(val correctionCount: Int) : RetrainDecision(); data class HighRecentCorrectionRate(val recentCount: Int) : RetrainDecision(); val shouldRetrain: Boolean get() = this !is NotNeeded }
sealed class RetrainResult { data object NoData : RetrainResult(); data class Success(val correctionsUsed: Int, val trainingExamples: Int, val durationMs: Long, val modelCategories: Int, val vocabularySize: Int) : RetrainResult(); data class Failed(val reason: String) : RetrainResult() }
