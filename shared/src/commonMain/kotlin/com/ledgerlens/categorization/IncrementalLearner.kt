package com.ledgerlens.categorization

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Handles incremental learning from individual user corrections.
 *
 * Updates the Naive Bayes classifier weights without requiring a full retrain.
 * Uses online learning techniques to adjust probabilities based on corrections.
 */
class IncrementalLearner(
    private val classifier: NaiveBayesClassifier,
    private val config: LearningConfig = LearningConfig()
) {
    /**
     * Learn from a single user correction.
     *
     * This applies a "correction boost" to the model:
     * 1. Increases probability of the correct category for matching features
     * 2. Decreases probability of the incorrect category
     * 3. Updates merchant priors
     */
    fun learnFromCorrection(correction: CategoryCorrection): LearningResult {
        if (!correction.isActualCorrection) {
            return LearningResult.NoChange
        }

        val features = correction.features
        val oldCategory = correction.oldCategoryId
        val newCategory = correction.newCategoryId
        
        var weightChanges = 0

        // Get current model
        val currentModel = classifier.getModel()
        
        // Calculate update weights based on confidence of wrong prediction
        // Higher confidence mistakes get larger corrections
        val correctionWeight = calculateCorrectionWeight(correction.confidence)

        // Train on the correct category (positive example)
        for (i in 0 until correctionWeight.toInt()) {
            classifier.train(features, newCategory)
            weightChanges++
        }

        // Apply fractional training for partial weight
        val fractionalWeight = correctionWeight - correctionWeight.toInt()
        if (fractionalWeight > 0 && kotlin.random.Random.nextFloat() < fractionalWeight) {
            classifier.train(features, newCategory)
            weightChanges++
        }

        // Update merchant prior with boosted weight
        updateMerchantPrior(features.merchantNormalized, newCategory, oldCategory)

        return LearningResult.Updated(weightChanges)
    }

    /**
     * Reinforce a correct prediction (user confirmed it).
     *
     * This is less aggressive than correction learning but still valuable.
     */
    fun reinforcePrediction(features: TransactionFeatures, categoryId: String) {
        if (config.enableReinforcement) {
            classifier.train(features, categoryId)
        }
    }

    /**
     * Calculate the correction weight based on the original prediction confidence.
     *
     * Higher confidence mistakes are weighted more heavily to counter
     * the model's previous strong belief.
     */
    private fun calculateCorrectionWeight(originalConfidence: Float): Float {
        // Base weight
        var weight = config.baseCorrectionWeight

        // Scale up for high-confidence mistakes
        if (originalConfidence >= ClassificationResult.HIGH_CONFIDENCE_THRESHOLD) {
            weight *= config.highConfidenceMissMultiplier
        } else if (originalConfidence >= ClassificationResult.REVIEW_THRESHOLD) {
            weight *= config.mediumConfidenceMissMultiplier
        }

        return min(weight, config.maxCorrectionWeight)
    }

    /**
     * Update merchant priors based on the correction.
     */
    private fun updateMerchantPrior(
        merchantNormalized: String,
        correctCategory: String,
        incorrectCategory: String
    ) {
        val model = classifier.getModel()
        val currentPriors = model.merchantPriors[merchantNormalized]?.toMutableMap() ?: mutableMapOf()

        // Boost correct category
        val currentCorrectPrior = currentPriors[correctCategory] ?: 0.1f
        currentPriors[correctCategory] = min(
            currentCorrectPrior + config.merchantPriorBoost,
            config.maxMerchantPrior
        )

        // Reduce incorrect category (but don't eliminate it)
        val currentIncorrectPrior = currentPriors[incorrectCategory] ?: 0.5f
        currentPriors[incorrectCategory] = max(
            currentIncorrectPrior - config.merchantPriorBoost,
            config.minMerchantPrior
        )

        // Renormalize
        val total = currentPriors.values.sum()
        if (total > 0) {
            currentPriors.keys.forEach { k ->
                currentPriors[k] = currentPriors[k]!! / total
            }
        }

        // Update model with new priors
        val newMerchantPriors = model.merchantPriors.toMutableMap()
        newMerchantPriors[merchantNormalized] = currentPriors

        classifier.loadModel(model.copy(merchantPriors = newMerchantPriors))
    }

    /**
     * Get confidence adjustment factor for a merchant based on correction history.
     *
     * This can be used to reduce confidence for merchants that are frequently corrected.
     */
    fun getConfidenceAdjustment(merchantNormalized: String, stats: CorrectionStats?): Float {
        if (stats == null) return 1.0f

        val correctionRate = stats.correctionRate
        return when {
            correctionRate >= 0.5f -> config.severeConfidenceReduction
            correctionRate >= 0.3f -> config.moderateConfidenceReduction
            correctionRate >= 0.15f -> config.mildConfidenceReduction
            else -> 1.0f
        }
    }

    /**
     * Apply decay to old learnings to prevent overfitting to old patterns.
     */
    fun applyDecay() {
        // This would be implemented with a time-weighted model update
        // For now, this is a placeholder for future enhancement
    }
}

/**
 * Configuration for incremental learning behavior.
 */
data class LearningConfig(
    /** Base number of training iterations for a correction */
    val baseCorrectionWeight: Float = 2.0f,

    /** Multiplier for high-confidence misses */
    val highConfidenceMissMultiplier: Float = 2.0f,

    /** Multiplier for medium-confidence misses */
    val mediumConfidenceMissMultiplier: Float = 1.5f,

    /** Maximum correction weight to prevent over-correction */
    val maxCorrectionWeight: Float = 5.0f,

    /** Whether to reinforce confirmed predictions */
    val enableReinforcement: Boolean = true,

    /** Boost to merchant prior for correct category */
    val merchantPriorBoost: Float = 0.15f,

    /** Maximum merchant prior value */
    val maxMerchantPrior: Float = 0.95f,

    /** Minimum merchant prior value */
    val minMerchantPrior: Float = 0.05f,

    /** Confidence reduction for severely corrected merchants (50%+ correction rate) */
    val severeConfidenceReduction: Float = 0.5f,

    /** Confidence reduction for moderately corrected merchants (30-50% rate) */
    val moderateConfidenceReduction: Float = 0.7f,

    /** Confidence reduction for mildly corrected merchants (15-30% rate) */
    val mildConfidenceReduction: Float = 0.85f
)

/**
 * Wrapper that applies confidence adjustment based on correction history.
 */
class ConfidenceAdjustedClassifier(
    private val baseClassifier: TransactionClassifier,
    private val incrementalLearner: IncrementalLearner,
    private val statsRepository: PredictionStatsRepository
) : TransactionClassifier {

    override val name: String = "confidence-adjusted-${baseClassifier.name}"
    override val priority: Int = baseClassifier.priority

    override fun classify(features: TransactionFeatures): ClassificationResult {
        val baseResult = baseClassifier.classify(features)

        // This would need to be made suspend in a real implementation
        // For now, we return the base result
        // In production, you'd cache stats or use a synchronous lookup
        return baseResult
    }

    /**
     * Classify with stats-based confidence adjustment.
     */
    suspend fun classifyWithAdjustment(features: TransactionFeatures): ClassificationResult {
        val baseResult = baseClassifier.classify(features)

        val stats = statsRepository.getMerchantStats(features.merchantNormalized)
        val adjustment = incrementalLearner.getConfidenceAdjustment(features.merchantNormalized, stats)

        if (adjustment >= 1.0f) return baseResult

        return baseResult.copy(
            confidence = baseResult.confidence * adjustment,
            explanation = baseResult.explanation.copy(
                reason = "${baseResult.explanation.reason} (confidence adjusted due to correction history)"
            )
        )
    }

    override fun canClassify(features: TransactionFeatures): Boolean {
        return baseClassifier.canClassify(features)
    }
}
