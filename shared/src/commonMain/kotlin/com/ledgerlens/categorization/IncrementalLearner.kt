package com.ledgerlens.categorization

import kotlin.math.max
import kotlin.math.min

/**
 * Handles incremental learning from individual user corrections.
 */
class IncrementalLearner(
    private val classifier: NaiveBayesClassifier,
    private val config: LearningConfig = LearningConfig()
) {
    fun learnFromCorrection(correction: CategoryCorrection): LearningResult {
        if (!correction.isActualCorrection) return LearningResult.NoChange

        val features = correction.features
        val newCategory = correction.newCategoryId
        var weightChanges = 0

        val correctionWeight = calculateCorrectionWeight(correction.confidence)

        repeat(correctionWeight.toInt()) {
            classifier.train(features, newCategory)
            weightChanges++
        }

        val fractionalWeight = correctionWeight - correctionWeight.toInt()
        if (fractionalWeight > 0 && kotlin.random.Random.nextFloat() < fractionalWeight) {
            classifier.train(features, newCategory)
            weightChanges++
        }

        updateMerchantPrior(features.merchantNormalized, newCategory, correction.oldCategoryId)
        return LearningResult.Updated(weightChanges)
    }

    fun reinforcePrediction(features: TransactionFeatures, categoryId: String) {
        if (config.enableReinforcement) {
            classifier.train(features, categoryId)
        }
    }

    private fun calculateCorrectionWeight(originalConfidence: Float): Float {
        var weight = config.baseCorrectionWeight
        if (originalConfidence >= ClassificationResult.HIGH_CONFIDENCE_THRESHOLD) {
            weight *= config.highConfidenceMissMultiplier
        } else if (originalConfidence >= ClassificationResult.REVIEW_THRESHOLD) {
            weight *= config.mediumConfidenceMissMultiplier
        }
        return min(weight, config.maxCorrectionWeight)
    }

    private fun updateMerchantPrior(merchantNormalized: String, correctCategory: String, incorrectCategory: String) {
        val model = classifier.getModel()
        val currentPriors = model.merchantPriors[merchantNormalized]?.toMutableMap() ?: mutableMapOf()

        val currentCorrectPrior = currentPriors[correctCategory] ?: 0.1f
        currentPriors[correctCategory] = min(currentCorrectPrior + config.merchantPriorBoost, config.maxMerchantPrior)

        val currentIncorrectPrior = currentPriors[incorrectCategory] ?: 0.5f
        currentPriors[incorrectCategory] = max(currentIncorrectPrior - config.merchantPriorBoost, config.minMerchantPrior)

        val total = currentPriors.values.sum()
        if (total > 0) currentPriors.keys.forEach { k -> currentPriors[k] = currentPriors[k]!! / total }

        val newMerchantPriors = model.merchantPriors.toMutableMap()
        newMerchantPriors[merchantNormalized] = currentPriors
        classifier.loadModel(model.copy(merchantPriors = newMerchantPriors))
    }

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
}

data class LearningConfig(
    val baseCorrectionWeight: Float = 2.0f,
    val highConfidenceMissMultiplier: Float = 2.0f,
    val mediumConfidenceMissMultiplier: Float = 1.5f,
    val maxCorrectionWeight: Float = 5.0f,
    val enableReinforcement: Boolean = true,
    val merchantPriorBoost: Float = 0.15f,
    val maxMerchantPrior: Float = 0.95f,
    val minMerchantPrior: Float = 0.05f,
    val severeConfidenceReduction: Float = 0.5f,
    val moderateConfidenceReduction: Float = 0.7f,
    val mildConfidenceReduction: Float = 0.85f
)
