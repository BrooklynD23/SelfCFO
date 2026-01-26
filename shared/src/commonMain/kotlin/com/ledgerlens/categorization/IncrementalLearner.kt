package com.ledgerlens.categorization
import kotlin.math.max
import kotlin.math.min
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
        val fractional = correctionWeight - correctionWeight.toInt()
        if (fractional > 0 && kotlin.random.Random.nextFloat() < fractional) {
            classifier.train(features, newCategory)
            weightChanges++
        }
        updateMerchantPrior(features.merchantNormalized, newCategory, correction.oldCategoryId)
        return LearningResult.Updated(weightChanges)
    }
    fun reinforcePrediction(features: TransactionFeatures, categoryId: String) {
        if (config.enableReinforcement) classifier.train(features, categoryId)
    }
    private fun calculateCorrectionWeight(originalConfidence: Float): Float {
        var w = config.baseCorrectionWeight
        if (originalConfidence >= ClassificationResult.HIGH_CONFIDENCE_THRESHOLD) {
            w *= config.highConfidenceMissMultiplier
        } else if (originalConfidence >= ClassificationResult.REVIEW_THRESHOLD) {
            w *= config.mediumConfidenceMissMultiplier
        }
        return min(w, config.maxCorrectionWeight)
    }
    private fun updateMerchantPrior(merchantNormalized: String, correctCategory: String, incorrectCategory: String) {
        val model = classifier.getModel()
        val priors = model.merchantPriors[merchantNormalized]?.toMutableMap() ?: mutableMapOf()
        priors[correctCategory] = min((priors[correctCategory] ?: 0.1f) + config.merchantPriorBoost, config.maxMerchantPrior)
        priors[incorrectCategory] = max((priors[incorrectCategory] ?: 0.5f) - config.merchantPriorBoost, config.minMerchantPrior)
        val total = priors.values.sum()
        if (total > 0) priors.keys.forEach { priors[it] = priors[it]!! / total }
        val newPriors = model.merchantPriors.toMutableMap()
        newPriors[merchantNormalized] = priors
        classifier.loadModel(model.copy(merchantPriors = newPriors))
    }
    fun getConfidenceAdjustment(merchantNormalized: String, stats: CorrectionStats?): Float {
        if (stats == null) return 1.0f
        val rate = stats.correctionRate
        return when {
            rate >= 0.5f -> config.severeConfidenceReduction
            rate >= 0.3f -> config.moderateConfidenceReduction
            rate >= 0.15f -> config.mildConfidenceReduction
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
