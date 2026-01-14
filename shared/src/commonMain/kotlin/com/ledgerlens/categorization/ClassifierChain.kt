package com.ledgerlens.categorization

/**
 * Composite classifier that chains multiple classifiers together.
 *
 * Classifiers are tried in priority order. If a classifier returns a result
 * above the confidence threshold, it's used. Otherwise, the next classifier
 * in the chain is tried.
 */
class ClassifierChain(
    private val classifiers: List<TransactionClassifier>,
    private val confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD
) : TransactionClassifier {

    override val name: String = "classifier-chain"
    override val priority: Int = Int.MAX_VALUE

    private val sortedClassifiers = classifiers.sortedByDescending { it.priority }

    override fun classify(features: TransactionFeatures): ClassificationResult {
        val results = mutableListOf<ClassificationResult>()

        for (classifier in sortedClassifiers) {
            if (!classifier.canClassify(features)) {
                continue
            }

            val result = classifier.classify(features)
            results.add(result)

            // If we get a high-confidence result, use it immediately
            if (result.confidence >= confidenceThreshold &&
                result.categoryId != ClassificationResult.UNKNOWN_CATEGORY_ID) {
                return result
            }
        }

        // If no classifier met the threshold, return the best result we found
        val bestResult = results
            .filter { it.categoryId != ClassificationResult.UNKNOWN_CATEGORY_ID }
            .maxByOrNull { it.confidence }

        return bestResult ?: ClassificationResult.unknown()
    }

    /**
     * Classify and return results from all classifiers for comparison.
     */
    fun classifyWithAllResults(features: TransactionFeatures): ChainClassificationResult {
        val results = mutableMapOf<String, ClassificationResult>()

        for (classifier in sortedClassifiers) {
            if (classifier.canClassify(features)) {
                results[classifier.name] = classifier.classify(features)
            }
        }

        val finalResult = classify(features)
        return ChainClassificationResult(
            finalResult = finalResult,
            classifierResults = results
        )
    }

    /**
     * Get all classifiers in the chain.
     */
    fun getClassifiers(): List<TransactionClassifier> = sortedClassifiers.toList()

    companion object {
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.7f

        /**
         * Create a default classifier chain with rule-based and naive bayes classifiers.
         */
        fun createDefault(
            rules: List<ClassificationRule> = RuleBasedClassifier.defaultRules(),
            model: NaiveBayesModel = NaiveBayesModel()
        ): ClassifierChain {
            return ClassifierChain(
                classifiers = listOf(
                    RuleBasedClassifier(rules),
                    NaiveBayesClassifier(model),
                    FallbackClassifier
                )
            )
        }
    }
}

/**
 * Result from classifier chain including all individual classifier results.
 */
data class ChainClassificationResult(
    val finalResult: ClassificationResult,
    val classifierResults: Map<String, ClassificationResult>
) {
    /**
     * Get the result from a specific classifier by name.
     */
    fun getResultFrom(classifierName: String): ClassificationResult? {
        return classifierResults[classifierName]
    }

    /**
     * Check if classifiers agreed on the category.
     */
    fun isConsensus(): Boolean {
        val nonUnknown = classifierResults.values
            .filter { it.categoryId != ClassificationResult.UNKNOWN_CATEGORY_ID }
        if (nonUnknown.size < 2) return true
        return nonUnknown.map { it.categoryId }.distinct().size == 1
    }

    /**
     * Get the average confidence across classifiers that returned the same category.
     */
    fun getAverageConfidenceForCategory(categoryId: String): Float {
        val matching = classifierResults.values.filter { it.categoryId == categoryId }
        if (matching.isEmpty()) return 0f
        return matching.map { it.confidence }.average().toFloat()
    }
}
