# 06: Correction Learning

## Overview

Implement the learning loop that captures user corrections and updates categorization.

---

## Implementation Steps

### Step 1: Correction Event Handler

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/learning/CorrectionHandler.kt
package com.ledgerlens.learning

class CorrectionHandler(
    private val correctionEventRepository: CorrectionEventRepository,
    private val transactionOverrideRepository: TransactionOverrideRepository,
    private val merchantPriorLearner: MerchantPriorLearner,
    private val featureExtractor: FeatureExtractor,
    private val ruleService: RuleService
) {

    /**
     * Handle a user correction.
     * This is called when user changes a transaction's category.
     */
    suspend fun handleCorrection(
        transactionId: String,
        previousCategoryId: String?,
        newCategoryId: String,
        transaction: ImportedTransaction
    ): CorrectionResult {
        // 1. Create correction event
        val correctionEvent = CorrectionEvent(
            id = generateId(),
            transactionId = transactionId,
            previousCategoryId = previousCategoryId,
            newCategoryId = newCategoryId,
            timestamp = Clock.System.now(),
            featuresSnapshot = createFeaturesSnapshot(transaction)
        )
        correctionEventRepository.create(correctionEvent)

        // 2. Update transaction override
        val override = transactionOverrideRepository.get(transactionId)
            ?: TransactionOverride(transactionId)
        transactionOverrideRepository.upsert(
            override.copy(
                categoryId = newCategoryId,
                isReviewed = true,
                reviewedAt = Clock.System.now(),
                updatedAt = Clock.System.now()
            )
        )

        // 3. Update merchant prior (immediate learning)
        transaction.merchantId?.let { merchantId ->
            merchantPriorLearner.learnFromCorrection(merchantId, newCategoryId)
        }

        // 4. Generate rule suggestion if pattern detected
        val suggestedRule = detectRulePattern(transaction, newCategoryId)

        return CorrectionResult(
            correctionEventId = correctionEvent.id,
            merchantPriorUpdated = transaction.merchantId != null,
            suggestedRule = suggestedRule
        )
    }

    private fun createFeaturesSnapshot(
        transaction: ImportedTransaction
    ): FeaturesSnapshot {
        val features = featureExtractor.extract(
            merchantNormalized = transaction.merchantNormalized,
            descriptionRaw = transaction.descriptionRaw,
            amount = Money(transaction.amountMinorUnits, transaction.currencyCode),
            date = transaction.postedDate
        )

        return FeaturesSnapshot(
            merchantNormalized = transaction.merchantNormalized,
            tokens = features.merchantTokens + features.descriptionTokens,
            amountBucket = features.amountBucket.name
        )
    }

    private suspend fun detectRulePattern(
        transaction: ImportedTransaction,
        categoryId: String
    ): SuggestedRule? {
        // Check if there are multiple similar transactions that could use a rule
        val similar = findSimilarUncategorized(transaction)
        if (similar.size >= 2) {
            return SuggestedRule(
                ruleType = "merchant_contains",
                pattern = extractCommonPattern(transaction, similar),
                categoryId = categoryId,
                affectedCount = similar.size + 1
            )
        }
        return null
    }
}

data class CorrectionResult(
    val correctionEventId: String,
    val merchantPriorUpdated: Boolean,
    val suggestedRule: SuggestedRule?
)

data class SuggestedRule(
    val ruleType: String,
    val pattern: String,
    val categoryId: String,
    val affectedCount: Int
)

@Serializable
data class FeaturesSnapshot(
    val merchantNormalized: String,
    val tokens: List<String>,
    val amountBucket: String
)
```

### Step 2: Batch Learning Job

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/learning/BatchLearningJob.kt
package com.ledgerlens.learning

/**
 * Background job for periodic learning updates.
 * Updates model training data and merchant patterns.
 */
class BatchLearningJob(
    private val correctionEventRepository: CorrectionEventRepository,
    private val merchantRepository: MerchantRepository,
    private val featureRepository: FeatureRepository
) {

    /**
     * Process corrections since last batch.
     * This runs periodically (e.g., daily) to update training data.
     */
    suspend fun processBatch(since: Instant) {
        val corrections = correctionEventRepository.getSince(since)

        // Group by merchant
        val byMerchant = corrections.groupBy { it.featuresSnapshot.merchantNormalized }

        for ((merchant, merchantCorrections) in byMerchant) {
            updateMerchantPatterns(merchant, merchantCorrections)
        }

        // Export features for model training (if user opted in)
        if (isTrainingDataDonationEnabled()) {
            exportTrainingFeatures(corrections)
        }
    }

    private suspend fun updateMerchantPatterns(
        merchantName: String,
        corrections: List<CorrectionEvent>
    ) {
        // Find most common correction category
        val categoryCounts = corrections.groupingBy { it.newCategoryId }.eachCount()
        val dominantCategory = categoryCounts.maxByOrNull { it.value }

        if (dominantCategory != null && dominantCategory.value >= corrections.size * 0.8) {
            // Strong signal - update merchant default
            merchantRepository.updateDefaultCategory(merchantName, dominantCategory.key)
        }
    }

    private suspend fun exportTrainingFeatures(corrections: List<CorrectionEvent>) {
        // Anonymize and export for model improvement
        val features = corrections.map { correction ->
            TrainingExample(
                tokens = hashTokens(correction.featuresSnapshot.tokens),
                amountBucket = correction.featuresSnapshot.amountBucket,
                categoryId = correction.newCategoryId
            )
        }

        featureRepository.queueForUpload(features)
    }

    private fun hashTokens(tokens: List<String>): List<String> {
        // Hash tokens for privacy
        return tokens.map { it.hashCode().toString(16) }
    }
}
```

### Step 3: Auto-Rule Suggester

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/learning/AutoRuleSuggester.kt
package com.ledgerlens.learning

class AutoRuleSuggester(
    private val correctionEventRepository: CorrectionEventRepository,
    private val transactionRepository: TransactionRepository,
    private val ruleRepository: RuleRepository
) {

    /**
     * Analyze corrections and suggest rules.
     */
    suspend fun suggestRules(): List<RuleSuggestion> {
        val recentCorrections = correctionEventRepository.getRecent(limit = 100)

        // Group by merchant pattern
        val byPattern = recentCorrections.groupBy {
            extractPattern(it.featuresSnapshot.merchantNormalized)
        }

        val suggestions = mutableListOf<RuleSuggestion>()

        for ((pattern, corrections) in byPattern) {
            if (corrections.size < 2) continue

            // Check if all corrections went to same category
            val categories = corrections.map { it.newCategoryId }.distinct()
            if (categories.size == 1) {
                // Check if rule already exists
                val existingRule = ruleRepository.findByPattern(pattern)
                if (existingRule == null) {
                    // Count how many future transactions would match
                    val potentialMatches = countPotentialMatches(pattern)

                    suggestions.add(
                        RuleSuggestion(
                            pattern = pattern,
                            ruleType = "merchant_contains",
                            categoryId = categories.first(),
                            correctionCount = corrections.size,
                            potentialMatches = potentialMatches,
                            confidence = calculateConfidence(corrections.size, potentialMatches)
                        )
                    )
                }
            }
        }

        return suggestions.sortedByDescending { it.confidence }
    }

    private fun extractPattern(merchant: String): String {
        val words = merchant.uppercase().split(" ")
        return words.firstOrNull { it.length >= 4 } ?: merchant.take(10)
    }

    private suspend fun countPotentialMatches(pattern: String): Int {
        return transactionRepository.countByMerchantPattern(pattern)
    }

    private fun calculateConfidence(
        correctionCount: Int,
        potentialMatches: Int
    ): Float {
        val baseConfidence = (correctionCount.toFloat() / 3).coerceAtMost(1f)
        val reachBonus = if (potentialMatches > 10) 0.2f else 0f
        return (baseConfidence + reachBonus).coerceAtMost(1f)
    }
}

data class RuleSuggestion(
    val pattern: String,
    val ruleType: String,
    val categoryId: String,
    val correctionCount: Int,
    val potentialMatches: Int,
    val confidence: Float
)
```

---

## Acceptance Criteria

- [ ] Corrections create CorrectionEvent records
- [ ] Transaction override updated immediately
- [ ] Merchant prior updated on correction
- [ ] Features snapshot saved for training
- [ ] Rule suggestions generated from patterns
- [ ] Batch learning job processes corrections
- [ ] Privacy-safe feature export (when opted in)

---

## Estimated Complexity

**Medium** - Event handling with learning algorithms.
