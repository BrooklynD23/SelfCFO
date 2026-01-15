package com.ledgerlens.categorization

/**
 * Interface for transaction category classifiers.
 * Implementations can use different classification strategies
 * (rule-based, ML, merchant priors, etc.)
 */
interface TransactionClassifier {
    /**
     * Unique identifier for this classifier.
     */
    val name: String

    /**
     * Priority for classifier chaining (lower = higher priority).
     */
    val priority: Int get() = 100

    /**
     * Minimum confidence threshold for this classifier to return a result.
     */
    val minConfidence: Float get() = 0.0f

    /**
     * Classify a transaction based on its features.
     *
     * @param features Extracted transaction features
     * @return Classification result, or null if classifier cannot make a determination
     */
    suspend fun classify(features: TransactionFeatures): ClassificationResult?

    /**
     * Check if this classifier can handle the given features.
     * Used for early filtering in classifier chains.
     */
    fun canClassify(features: TransactionFeatures): Boolean = true

    /**
     * Train or update the classifier with a labeled example.
     * Not all classifiers support training (e.g., rule-based).
     *
     * @param features Transaction features
     * @param categoryId Correct category
     * @return true if the classifier was updated
     */
    suspend fun train(features: TransactionFeatures, categoryId: String): Boolean = false

    /**
     * Reset or clear the classifier's learned state.
     */
    suspend fun reset() {}
}

/**
 * Chains multiple classifiers together, using the first confident result.
 */
class ClassifierChain(
    private val classifiers: List<TransactionClassifier>,
    private val defaultCategoryId: String = Category.UNCATEGORIZED_ID
) : TransactionClassifier {
    
    override val name: String = "classifier-chain"
    override val priority: Int = 0

    private val sortedClassifiers = classifiers.sortedBy { it.priority }

    override suspend fun classify(features: TransactionFeatures): ClassificationResult {
        for (classifier in sortedClassifiers) {
            if (!classifier.canClassify(features)) continue

            val result = classifier.classify(features)
            if (result != null && result.confidence >= classifier.minConfidence) {
                return result
            }
        }

        return ClassificationResult(
            categoryId = defaultCategoryId,
            confidence = 0.0f,
            alternatives = emptyList(),
            explanation = ClassificationExplanation(
                classifierUsed = name,
                reason = "No classifier could determine a category"
            )
        )
    }

    override fun canClassify(features: TransactionFeatures): Boolean =
        classifiers.any { it.canClassify(features) }

    override suspend fun train(features: TransactionFeatures, categoryId: String): Boolean {
        var anyTrained = false
        for (classifier in classifiers) {
            if (classifier.train(features, categoryId)) {
                anyTrained = true
            }
        }
        return anyTrained
    }

    override suspend fun reset() {
        for (classifier in classifiers) {
            classifier.reset()
        }
    }

    fun getClassifier(name: String): TransactionClassifier? =
        classifiers.find { it.name == name }
}

/**
 * Classifier that uses merchant priors for prediction.
 */
class MerchantPriorClassifier(
    private val priorProvider: MerchantPriorProvider,
    override val minConfidence: Float = DEFAULT_MIN_CONFIDENCE
) : TransactionClassifier {
    
    override val name: String = "merchant-prior"
    override val priority: Int = 10 // High priority - check merchant history first

    override suspend fun classify(features: TransactionFeatures): ClassificationResult? {
        if (features.merchantNormalized.isBlank()) return null

        val distribution = priorProvider.getCategoryDistribution(features.merchantNormalized)
        if (!distribution.hasReliableHistory) return null

        val mostLikely = distribution.mostLikelyCategory ?: return null
        if (mostLikely.probability < minConfidence) return null

        val alternatives = distribution.probabilities
            .filter { it.categoryId != mostLikely.categoryId }
            .take(3)
            .map { CategoryScore(it.categoryId, it.probability) }

        return ClassificationResult(
            categoryId = mostLikely.categoryId,
            confidence = mostLikely.probability,
            alternatives = alternatives,
            explanation = ClassificationExplanation(
                classifierUsed = name,
                reason = "Based on ${distribution.totalObservations} previous transactions",
                merchantPrior = mostLikely.probability
            )
        )
    }

    override fun canClassify(features: TransactionFeatures): Boolean =
        features.merchantNormalized.isNotBlank()

    override suspend fun train(features: TransactionFeatures, categoryId: String): Boolean {
        if (features.merchantNormalized.isBlank()) return false
        priorProvider.recordAssignment(features.merchantNormalized, categoryId)
        return true
    }

    companion object {
        const val DEFAULT_MIN_CONFIDENCE = 0.6f
    }
}

/**
 * Combines results from multiple classifiers using weighted voting.
 */
class EnsembleClassifier(
    private val classifiers: List<TransactionClassifier>,
    private val weights: Map<String, Float> = emptyMap()
) : TransactionClassifier {
    
    override val name: String = "ensemble"
    override val priority: Int = 50

    override suspend fun classify(features: TransactionFeatures): ClassificationResult? {
        val results = mutableListOf<Pair<ClassificationResult, Float>>()

        for (classifier in classifiers) {
            if (!classifier.canClassify(features)) continue
            val result = classifier.classify(features) ?: continue
            val weight = weights[classifier.name] ?: 1.0f
            results.add(result to weight)
        }

        if (results.isEmpty()) return null

        // Aggregate scores by category
        val categoryScores = mutableMapOf<String, Float>()
        var totalWeight = 0f

        for ((result, weight) in results) {
            val weightedScore = result.confidence * weight
            categoryScores[result.categoryId] = 
                (categoryScores[result.categoryId] ?: 0f) + weightedScore
            totalWeight += weight

            for (alt in result.alternatives) {
                val altWeightedScore = alt.score * weight * 0.5f // Discount alternatives
                categoryScores[alt.categoryId] = 
                    (categoryScores[alt.categoryId] ?: 0f) + altWeightedScore
            }
        }

        if (totalWeight == 0f) return null

        // Normalize scores
        val normalizedScores = categoryScores.mapValues { it.value / totalWeight }
        val sorted = normalizedScores.entries.sortedByDescending { it.value }
        val best = sorted.first()

        val alternatives = sorted.drop(1).take(3).map { 
            CategoryScore(it.key, it.value) 
        }

        val classifiersUsed = results.map { it.first.explanation.classifierUsed }.distinct()

        return ClassificationResult(
            categoryId = best.key,
            confidence = best.value,
            alternatives = alternatives,
            explanation = ClassificationExplanation(
                classifierUsed = name,
                reason = "Combined from ${classifiersUsed.size} classifiers: ${classifiersUsed.joinToString()}"
            )
        )
    }

    override suspend fun train(features: TransactionFeatures, categoryId: String): Boolean {
        var anyTrained = false
        for (classifier in classifiers) {
            if (classifier.train(features, categoryId)) {
                anyTrained = true
            }
        }
        return anyTrained
    }
}
