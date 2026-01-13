# 07: Categorization Pipeline

## Overview

Wire up the full categorization pipeline with proper priority ordering: Rule > Merchant Prior > ML Model.

---

## Implementation Steps

### Step 1: Categorization Service

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/categorization/CategorizationService.kt
package com.ledgerlens.categorization

class CategorizationService(
    private val rulesEngine: RulesEngine,
    private val merchantMatcher: MerchantMatcher,
    private val merchantPriorRepository: MerchantPriorRepository,
    private val categorizationModel: CategorizationModel,
    private val featureExtractor: FeatureExtractor,
    private val explanationBuilder: ExplanationBuilder
) {

    companion object {
        const val LOW_CONFIDENCE_THRESHOLD = 0.7f
    }

    /**
     * Categorize a transaction.
     * Priority: Rule > Merchant Prior > ML Model
     */
    suspend fun categorize(
        transaction: TransactionForCategorization
    ): CategorizationResult {
        // 1. Check rules first
        val ruleMatch = rulesEngine.findMatchingRule(transaction.toMatchingFormat())
        if (ruleMatch != null) {
            return CategorizationResult(
                categoryId = ruleMatch.categoryId,
                confidence = 1.0f,
                explanation = explanationBuilder.forRule(ruleMatch.rule),
                source = CategorizationSource.RULE,
                needsReview = false
            )
        }

        // 2. Check merchant prior
        val merchantMatch = merchantMatcher.match(transaction.descriptionRaw)
        if (merchantMatch != null) {
            val prior = merchantPriorRepository.getCategoryForMerchant(merchantMatch.merchant.id)
            if (prior != null && prior.confidence >= 0.6f) {
                return CategorizationResult(
                    categoryId = prior.categoryId,
                    confidence = prior.confidence * merchantMatch.confidence,
                    explanation = explanationBuilder.forMerchantPrior(
                        merchant = merchantMatch.merchant,
                        observations = prior.observationCount,
                        confidence = prior.confidence
                    ),
                    source = CategorizationSource.MERCHANT_PRIOR,
                    needsReview = false,
                    merchantId = merchantMatch.merchant.id
                )
            }
        }

        // 3. Fall back to ML model
        val features = featureExtractor.extract(
            merchantNormalized = transaction.merchantNormalized,
            descriptionRaw = transaction.descriptionRaw,
            amount = transaction.amount,
            date = transaction.postedDate
        )

        val predictions = categorizationModel.predict(features)
        val topPrediction = predictions.firstOrNull()
            ?: return defaultResult()

        val needsReview = topPrediction.confidence < LOW_CONFIDENCE_THRESHOLD

        return CategorizationResult(
            categoryId = topPrediction.categoryId,
            confidence = topPrediction.confidence,
            explanation = explanationBuilder.forModelPrediction(
                predictions = predictions,
                modelInfo = categorizationModel.getModelInfo(),
                topTokens = features.merchantTokens.take(3)
            ),
            source = CategorizationSource.ML_MODEL,
            needsReview = needsReview,
            merchantId = merchantMatch?.merchant?.id
        )
    }

    /**
     * Batch categorize multiple transactions.
     */
    suspend fun categorizeBatch(
        transactions: List<TransactionForCategorization>
    ): List<CategorizationResult> {
        return transactions.map { categorize(it) }
    }

    private fun defaultResult(): CategorizationResult {
        return CategorizationResult(
            categoryId = "uncategorized",
            confidence = 0f,
            explanation = CategoryExplanation(
                source = ExplanationSource.DEFAULT,
                confidence = 0f
            ),
            source = CategorizationSource.DEFAULT,
            needsReview = true
        )
    }
}

data class TransactionForCategorization(
    val id: String,
    val merchantNormalized: String,
    val descriptionRaw: String,
    val amount: Money,
    val postedDate: LocalDate,
    val accountId: String?
) {
    fun toMatchingFormat() = TransactionForMatching(
        merchantNormalized = merchantNormalized,
        descriptionRaw = descriptionRaw,
        amount = amount,
        accountId = accountId
    )
}

data class CategorizationResult(
    val categoryId: String,
    val confidence: Float,
    val explanation: CategoryExplanation,
    val source: CategorizationSource,
    val needsReview: Boolean,
    val merchantId: String? = null
)

enum class CategorizationSource {
    RULE,
    MERCHANT_PRIOR,
    ML_MODEL,
    DEFAULT
}
```

### Step 2: Review Inbox Service

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/categorization/ReviewInboxService.kt
package com.ledgerlens.categorization

class ReviewInboxService(
    private val transactionRepository: TransactionRepository,
    private val importJobRepository: ImportJobRepository
) {

    /**
     * Get transactions that need review.
     */
    suspend fun getReviewItems(
        filter: ReviewFilter = ReviewFilter.ALL
    ): List<ReviewItem> {
        val transactions = transactionRepository.getNeedsReview()

        return transactions
            .filter { matchesFilter(it, filter) }
            .map { tx ->
                ReviewItem(
                    transactionId = tx.id,
                    transaction = tx,
                    issueType = determineIssueType(tx),
                    suggestedCategory = tx.categoryIdAuto,
                    confidence = tx.categoryConfidence ?: 0f
                )
            }
            .sortedBy { it.issueType.priority }
    }

    /**
     * Group review items by issue type.
     */
    suspend fun getGroupedReviewItems(): Map<ReviewIssueType, List<ReviewItem>> {
        return getReviewItems().groupBy { it.issueType }
    }

    /**
     * Count items by issue type.
     */
    suspend fun getReviewCounts(): Map<ReviewIssueType, Int> {
        val items = getReviewItems()
        return ReviewIssueType.values().associateWith { type ->
            items.count { it.issueType == type }
        }
    }

    private fun determineIssueType(transaction: ImportedTransaction): ReviewIssueType {
        return when {
            transaction.parseWarnings?.isNotEmpty() == true ->
                ReviewIssueType.PARSE_ERROR
            (transaction.categoryConfidence ?: 0f) < 0.5f ->
                ReviewIssueType.LOW_CONFIDENCE
            hasDuplicateFlag(transaction) ->
                ReviewIssueType.POSSIBLE_DUPLICATE
            else ->
                ReviewIssueType.LOW_CONFIDENCE
        }
    }

    private fun matchesFilter(
        transaction: ImportedTransaction,
        filter: ReviewFilter
    ): Boolean {
        return when (filter) {
            ReviewFilter.ALL -> true
            ReviewFilter.PARSE_ERRORS -> transaction.parseWarnings?.isNotEmpty() == true
            ReviewFilter.LOW_CONFIDENCE -> (transaction.categoryConfidence ?: 0f) < 0.7f
            ReviewFilter.DUPLICATES -> hasDuplicateFlag(transaction)
        }
    }
}

data class ReviewItem(
    val transactionId: String,
    val transaction: ImportedTransaction,
    val issueType: ReviewIssueType,
    val suggestedCategory: String?,
    val confidence: Float
)

enum class ReviewIssueType(val priority: Int) {
    PARSE_ERROR(1),
    POSSIBLE_DUPLICATE(2),
    LOW_CONFIDENCE(3)
}

enum class ReviewFilter {
    ALL,
    PARSE_ERRORS,
    LOW_CONFIDENCE,
    DUPLICATES
}
```

### Step 3: Bulk Actions Service

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/categorization/BulkActionsService.kt
package com.ledgerlens.categorization

class BulkActionsService(
    private val transactionOverrideRepository: TransactionOverrideRepository,
    private val correctionHandler: CorrectionHandler,
    private val ruleService: RuleService
) {

    /**
     * Apply category to multiple transactions.
     */
    suspend fun bulkCategorize(
        transactionIds: List<String>,
        categoryId: String
    ): BulkActionResult {
        var successCount = 0
        var errorCount = 0
        val errors = mutableListOf<BulkActionError>()

        for (id in transactionIds) {
            try {
                val override = transactionOverrideRepository.get(id)
                    ?: TransactionOverride(id)

                transactionOverrideRepository.upsert(
                    override.copy(
                        categoryId = categoryId,
                        isReviewed = true,
                        reviewedAt = Clock.System.now(),
                        updatedAt = Clock.System.now()
                    )
                )
                successCount++
            } catch (e: Exception) {
                errorCount++
                errors.add(BulkActionError(id, e.message ?: "Unknown error"))
            }
        }

        return BulkActionResult(
            totalRequested = transactionIds.size,
            successCount = successCount,
            errorCount = errorCount,
            errors = errors
        )
    }

    /**
     * Create rule from selection.
     */
    suspend fun createRuleFromSelection(
        transactionIds: List<String>,
        categoryId: String,
        ruleType: String,
        pattern: String
    ): Rule {
        val rule = ruleService.createRule(
            ruleType = ruleType,
            matchExpression = pattern,
            targetCategoryId = categoryId
        )

        // Apply to selected transactions
        bulkCategorize(transactionIds, categoryId)

        return rule
    }

    /**
     * Mark transactions as reviewed (keep current category).
     */
    suspend fun bulkMarkReviewed(transactionIds: List<String>): BulkActionResult {
        var successCount = 0

        for (id in transactionIds) {
            val override = transactionOverrideRepository.get(id)
                ?: TransactionOverride(id)

            transactionOverrideRepository.upsert(
                override.copy(
                    isReviewed = true,
                    reviewedAt = Clock.System.now(),
                    updatedAt = Clock.System.now()
                )
            )
            successCount++
        }

        return BulkActionResult(
            totalRequested = transactionIds.size,
            successCount = successCount,
            errorCount = 0,
            errors = emptyList()
        )
    }
}

data class BulkActionResult(
    val totalRequested: Int,
    val successCount: Int,
    val errorCount: Int,
    val errors: List<BulkActionError>
)

data class BulkActionError(
    val transactionId: String,
    val message: String
)
```

---

## Acceptance Criteria

- [ ] Categorization respects priority (Rule > Prior > Model)
- [ ] Low confidence routes to review inbox
- [ ] Review inbox groups by issue type
- [ ] Bulk categorization works
- [ ] Rule creation from selection works
- [ ] Batch categorization efficient

---

## Estimated Complexity

**Medium** - Integration of multiple components.
