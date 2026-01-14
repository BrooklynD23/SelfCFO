package com.ledgerlens.categorization

import kotlin.math.ln
import kotlin.math.exp

/**
 * Simple on-device Naive Bayes classifier for transaction categorization.
 *
 * Uses token frequencies and merchant priors to compute category probabilities.
 * Supports incremental learning from user corrections.
 */
class NaiveBayesClassifier(
    private var model: NaiveBayesModel = NaiveBayesModel()
) : TransactionClassifier {

    override val name: String = "naive-bayes"
    override val priority: Int = 50

    override fun classify(features: TransactionFeatures): ClassificationResult {
        if (model.isEmpty()) {
            return ClassificationResult.unknown()
        }

        val scores = computeCategoryScores(features)
        if (scores.isEmpty()) {
            return ClassificationResult.unknown()
        }

        val sortedScores = scores.entries.sortedByDescending { it.value }
        val topCategory = sortedScores.first()
        val topScore = topCategory.value

        val normalizedScores = normalizeScores(scores)
        val confidence = normalizedScores[topCategory.key] ?: 0f

        val alternatives = sortedScores.drop(1).take(3).map { (cat, _) ->
            CategoryScore(cat, normalizedScores[cat] ?: 0f)
        }

        val matchedTokens = features.descriptionTokens.filter { token ->
            model.tokenCategoryFrequencies[token]?.containsKey(topCategory.key) == true
        }

        return ClassificationResult(
            categoryId = topCategory.key,
            confidence = confidence,
            alternatives = alternatives,
            explanation = ClassificationExplanation(
                classifierUsed = name,
                reason = "Probabilistic classification based on ${matchedTokens.size} matching tokens",
                merchantPrior = model.merchantPriors[features.merchantNormalized]?.get(topCategory.key),
                tokenMatches = matchedTokens.take(5),
                amountPattern = features.amountBucket.name
            )
        )
    }

    override fun canClassify(features: TransactionFeatures): Boolean {
        return !model.isEmpty()
    }

    /**
     * Compute log-probability scores for each category.
     */
    private fun computeCategoryScores(features: TransactionFeatures): Map<String, Double> {
        val scores = mutableMapOf<String, Double>()

        for (category in model.categories) {
            var logProb = ln(model.getCategoryPrior(category))

            // Merchant prior
            val merchantPrior = model.merchantPriors[features.merchantNormalized]?.get(category)
            if (merchantPrior != null) {
                logProb += ln(merchantPrior.toDouble()) * MERCHANT_WEIGHT
            }

            // Token likelihoods
            for (token in features.descriptionTokens) {
                val tokenProb = model.getTokenProbability(token, category)
                logProb += ln(tokenProb)
            }

            // Amount bucket likelihood
            val amountProb = model.getAmountBucketProbability(features.amountBucket, category)
            logProb += ln(amountProb) * AMOUNT_WEIGHT

            scores[category] = logProb
        }

        return scores
    }

    /**
     * Convert log-probabilities to normalized probabilities using softmax.
     */
    private fun normalizeScores(logScores: Map<String, Double>): Map<String, Float> {
        if (logScores.isEmpty()) return emptyMap()

        val maxLog = logScores.values.maxOrNull() ?: 0.0
        val expScores = logScores.mapValues { (_, v) -> exp(v - maxLog) }
        val sumExp = expScores.values.sum()

        return expScores.mapValues { (_, v) -> (v / sumExp).toFloat() }
    }

    /**
     * Update the model with a training example.
     */
    fun train(features: TransactionFeatures, categoryId: String) {
        model = model.addExample(features, categoryId)
    }

    /**
     * Get the current model for serialization.
     */
    fun getModel(): NaiveBayesModel = model

    /**
     * Load a model from serialized state.
     */
    fun loadModel(newModel: NaiveBayesModel) {
        model = newModel
    }

    companion object {
        const val MERCHANT_WEIGHT = 2.0
        const val AMOUNT_WEIGHT = 0.5
    }
}

/**
 * Naive Bayes model containing learned probabilities.
 *
 * This is designed to be serializable for persistence.
 */
data class NaiveBayesModel(
    val categories: Set<String> = emptySet(),
    val categoryCounts: Map<String, Int> = emptyMap(),
    val totalExamples: Int = 0,
    val tokenCategoryFrequencies: Map<String, Map<String, Int>> = emptyMap(),
    val categoryTokenCounts: Map<String, Int> = emptyMap(),
    val vocabularySize: Int = 0,
    val merchantPriors: Map<String, Map<String, Float>> = emptyMap(),
    val amountBucketCounts: Map<AmountBucket, Map<String, Int>> = emptyMap()
) {
    fun isEmpty(): Boolean = totalExamples == 0

    fun getCategoryPrior(category: String): Double {
        val count = categoryCounts[category] ?: 0
        return (count + 1.0) / (totalExamples + categories.size)
    }

    fun getTokenProbability(token: String, category: String): Double {
        val tokenFreq = tokenCategoryFrequencies[token]?.get(category) ?: 0
        val categoryTokenCount = categoryTokenCounts[category] ?: 0
        return (tokenFreq + SMOOTHING_ALPHA) / (categoryTokenCount + vocabularySize * SMOOTHING_ALPHA)
    }

    fun getAmountBucketProbability(bucket: AmountBucket, category: String): Double {
        val bucketCount = amountBucketCounts[bucket]?.get(category) ?: 0
        val categoryCount = categoryCounts[category] ?: 0
        return (bucketCount + 1.0) / (categoryCount + AmountBucket.entries.size)
    }

    /**
     * Add a training example and return updated model.
     */
    fun addExample(features: TransactionFeatures, categoryId: String): NaiveBayesModel {
        val newCategories = categories + categoryId
        val newCategoryCounts = categoryCounts.toMutableMap().apply {
            this[categoryId] = (this[categoryId] ?: 0) + 1
        }

        val newTokenFreqs = tokenCategoryFrequencies.toMutableMap()
        val newCategoryTokenCounts = categoryTokenCounts.toMutableMap().apply {
            this[categoryId] = (this[categoryId] ?: 0) + features.descriptionTokens.size
        }

        var newVocabSize = vocabularySize
        for (token in features.descriptionTokens) {
            val tokenMap = newTokenFreqs.getOrPut(token) { mutableMapOf() }.toMutableMap()
            if (token !in tokenCategoryFrequencies) {
                newVocabSize++
            }
            tokenMap[categoryId] = (tokenMap[categoryId] ?: 0) + 1
            newTokenFreqs[token] = tokenMap
        }

        // Update merchant priors
        val newMerchantPriors = merchantPriors.toMutableMap()
        val merchantMap = newMerchantPriors.getOrPut(features.merchantNormalized) { mutableMapOf() }.toMutableMap()
        val currentCount = merchantMap.values.sum()
        val categoryMerchantCount = (merchantMap[categoryId] ?: 0f) + 1f
        merchantMap[categoryId] = categoryMerchantCount / (currentCount + 1)
        // Renormalize
        val totalMerchant = merchantMap.values.sum()
        merchantMap.keys.forEach { k -> merchantMap[k] = merchantMap[k]!! / totalMerchant }
        newMerchantPriors[features.merchantNormalized] = merchantMap

        // Update amount bucket counts
        val newAmountBucketCounts = amountBucketCounts.toMutableMap()
        val bucketMap = newAmountBucketCounts.getOrPut(features.amountBucket) { mutableMapOf() }.toMutableMap()
        bucketMap[categoryId] = (bucketMap[categoryId] ?: 0) + 1
        newAmountBucketCounts[features.amountBucket] = bucketMap

        return NaiveBayesModel(
            categories = newCategories,
            categoryCounts = newCategoryCounts,
            totalExamples = totalExamples + 1,
            tokenCategoryFrequencies = newTokenFreqs,
            categoryTokenCounts = newCategoryTokenCounts,
            vocabularySize = newVocabSize,
            merchantPriors = newMerchantPriors,
            amountBucketCounts = newAmountBucketCounts
        )
    }

    companion object {
        const val SMOOTHING_ALPHA = 0.1
    }
}
