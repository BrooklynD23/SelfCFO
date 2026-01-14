package com.ledgerlens.categorization

/**
 * Interface for transaction category classification.
 *
 * Implementations may use rules, ML models, or hybrid approaches.
 */
interface TransactionClassifier {
    /**
     * Classify a transaction based on its extracted features.
     *
     * @param features The extracted features from the transaction
     * @return Classification result with category, confidence, and explanation
     */
    fun classify(features: TransactionFeatures): ClassificationResult

    /**
     * Check if this classifier can handle the given features.
     * Used by ClassifierChain to determine fallback behavior.
     *
     * @param features The transaction features to check
     * @return true if this classifier should attempt classification
     */
    fun canClassify(features: TransactionFeatures): Boolean = true

    /**
     * The name of this classifier for logging and explanation.
     */
    val name: String

    /**
     * Priority of this classifier (higher = tried first in chain).
     */
    val priority: Int get() = 0
}

/**
 * A classifier that always returns unknown - used as final fallback.
 */
object FallbackClassifier : TransactionClassifier {
    override val name: String = "fallback"
    override val priority: Int = Int.MIN_VALUE

    override fun classify(features: TransactionFeatures): ClassificationResult {
        return ClassificationResult.unknown()
    }
}
