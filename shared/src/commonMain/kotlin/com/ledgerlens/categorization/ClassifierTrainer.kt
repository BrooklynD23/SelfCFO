package com.ledgerlens.categorization

import kotlinx.datetime.Clock

/**
 * Handles incremental learning from user corrections and labeled data.
 *
 * Supports both immediate learning (merchant priors) and batch model updates.
 */
class ClassifierTrainer(
    private val dataStore: TrainingDataStore,
    private val featureExtractor: FeatureExtractor = FeatureExtractor()
) {
    private var naiveBayesClassifier: NaiveBayesClassifier? = null

    /**
     * Record a user correction and update the model incrementally.
     *
     * This is the primary entry point for learning from user feedback.
     */
    suspend fun recordCorrection(
        transactionId: String,
        features: TransactionFeatures,
        previousCategoryId: String,
        newCategoryId: String
    ) {
        val correctionId = generateId()
        val now = Clock.System.now()

        // Store the correction event
        val event = CorrectionEvent(
            id = correctionId,
            transactionId = transactionId,
            previousCategoryId = previousCategoryId,
            newCategoryId = newCategoryId,
            timestamp = now,
            featuresSnapshot = features
        )
        dataStore.storeCorrectionEvent(event)

        // Store as labeled example
        val example = LabeledExample(
            id = generateId(),
            features = features,
            categoryId = newCategoryId,
            source = LabelSource.USER_CORRECTION,
            timestamp = now,
            transactionId = transactionId
        )
        dataStore.storeLabeledExample(example)

        // Immediate learning: update merchant preference
        updateMerchantPreference(features.merchantNormalized, newCategoryId)

        // Incremental model update
        naiveBayesClassifier?.train(features, newCategoryId)
    }

    /**
     * Record a user-assigned category (not a correction).
     */
    suspend fun recordUserAssignment(
        transactionId: String,
        features: TransactionFeatures,
        categoryId: String
    ) {
        val now = Clock.System.now()

        val example = LabeledExample(
            id = generateId(),
            features = features,
            categoryId = categoryId,
            source = LabelSource.USER_ASSIGNED,
            timestamp = now,
            transactionId = transactionId
        )
        dataStore.storeLabeledExample(example)

        // Update merchant preference
        updateMerchantPreference(features.merchantNormalized, categoryId)

        // Incremental model update
        naiveBayesClassifier?.train(features, categoryId)
    }

    /**
     * Train or retrain the classifier from all stored examples.
     */
    suspend fun trainFromAllExamples(): NaiveBayesModel {
        val examples = dataStore.getAllExamples()
        var model = NaiveBayesModel()

        for (example in examples) {
            model = model.addExample(example.features, example.categoryId)
        }

        naiveBayesClassifier?.loadModel(model)
        return model
    }

    /**
     * Train from a batch of labeled examples.
     */
    suspend fun trainBatch(examples: List<Pair<TransactionFeatures, String>>): NaiveBayesModel {
        var model = naiveBayesClassifier?.getModel() ?: NaiveBayesModel()
        val now = Clock.System.now()

        for ((features, categoryId) in examples) {
            model = model.addExample(features, categoryId)

            val example = LabeledExample(
                id = generateId(),
                features = features,
                categoryId = categoryId,
                source = LabelSource.IMPORTED,
                timestamp = now
            )
            dataStore.storeLabeledExample(example)
        }

        naiveBayesClassifier?.loadModel(model)
        return model
    }

    /**
     * Get training statistics.
     */
    suspend fun getTrainingStats(): TrainingStats {
        val categoryCounts = dataStore.getCategoryCounts()
        val totalExamples = dataStore.getTotalCount()
        val recentCorrections = dataStore.getRecentCorrections(100)
        val merchantPrefs = dataStore.getMerchantPreferences()

        return TrainingStats(
            totalExamples = totalExamples,
            categoryCounts = categoryCounts,
            uniqueCategories = categoryCounts.keys.size,
            uniqueMerchants = merchantPrefs.keys.size,
            recentCorrectionCount = recentCorrections.size,
            modelReady = totalExamples >= MIN_EXAMPLES_FOR_MODEL
        )
    }

    /**
     * Set the classifier instance to update during training.
     */
    fun setClassifier(classifier: NaiveBayesClassifier) {
        naiveBayesClassifier = classifier
    }

    /**
     * Get the current trained model.
     */
    fun getCurrentModel(): NaiveBayesModel? = naiveBayesClassifier?.getModel()

    /**
     * Update merchant-category preference with exponential smoothing.
     */
    private suspend fun updateMerchantPreference(merchantNormalized: String, categoryId: String) {
        val existing = dataStore.getMerchantPreferences()[merchantNormalized]
        val currentScore = existing?.getConfidence(categoryId) ?: 0f

        // Exponential moving average for smooth updates
        val newScore = SMOOTHING_FACTOR * 1f + (1 - SMOOTHING_FACTOR) * currentScore

        dataStore.updateMerchantPreference(merchantNormalized, categoryId, newScore)
    }

    private fun generateId(): String {
        return "${Clock.System.now().toEpochMilliseconds()}-${(0..999999).random()}"
    }

    companion object {
        const val MIN_EXAMPLES_FOR_MODEL = 10
        const val SMOOTHING_FACTOR = 0.3f
    }
}

/**
 * Statistics about the training data and model.
 */
data class TrainingStats(
    val totalExamples: Int,
    val categoryCounts: Map<String, Int>,
    val uniqueCategories: Int,
    val uniqueMerchants: Int,
    val recentCorrectionCount: Int,
    val modelReady: Boolean
) {
    /**
     * Get categories with insufficient training examples.
     */
    fun getUnderrepresentedCategories(minExamples: Int = 5): List<String> {
        return categoryCounts.filter { it.value < minExamples }.keys.toList()
    }

    /**
     * Get the most common category.
     */
    fun getMostCommonCategory(): String? {
        return categoryCounts.maxByOrNull { it.value }?.key
    }
}
