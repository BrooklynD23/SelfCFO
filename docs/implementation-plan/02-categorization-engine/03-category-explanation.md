# 03: Category Explanation

## Overview

Implement the structured category explanation schema per [02a-data-model-addendum.md §5](../../PRDs/02a-data-model-addendum.md#5-category-explanation-schema).

---

## Implementation Steps

### Step 1: Define Explanation Types

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/domain/CategoryExplanation.kt
package com.ledgerlens.domain

import kotlinx.serialization.Serializable

@Serializable
data class CategoryExplanation(
    val schemaVersion: Int = 1,
    val source: ExplanationSource,
    val confidence: Float,
    val contributors: List<ExplanationContributor> = emptyList(),
    val alternatives: List<AlternativeCategory> = emptyList()
)

@Serializable
enum class ExplanationSource {
    USER_OVERRIDE,
    RULE,
    MERCHANT_PRIOR,
    ML_MODEL,
    DEFAULT
}

@Serializable
sealed class ExplanationContributor {
    abstract val type: String
    abstract val weight: Float

    @Serializable
    data class RuleMatch(
        override val weight: Float,
        val ruleId: String,
        val ruleName: String,
        val matchType: String,
        val pattern: String
    ) : ExplanationContributor() {
        override val type = "rule_match"
    }

    @Serializable
    data class MerchantPrior(
        override val weight: Float,
        val merchantId: String,
        val priorCategory: String,
        val observations: Int
    ) : ExplanationContributor() {
        override val type = "merchant_prior"
    }

    @Serializable
    data class TokenMatch(
        override val weight: Float,
        val tokens: List<String>,
        val matchedCategory: String
    ) : ExplanationContributor() {
        override val type = "token_match"
    }

    @Serializable
    data class AmountPattern(
        override val weight: Float,
        val amountBucket: String,
        val typicalForCategory: Boolean
    ) : ExplanationContributor() {
        override val type = "amount_pattern"
    }

    @Serializable
    data class HistorySimilarity(
        override val weight: Float,
        val similarTransactionId: String,
        val similarity: Float
    ) : ExplanationContributor() {
        override val type = "history_similarity"
    }

    @Serializable
    data class ModelPrediction(
        override val weight: Float,
        val modelVersion: String,
        val topTokens: List<String>
    ) : ExplanationContributor() {
        override val type = "model_prediction"
    }
}

@Serializable
data class AlternativeCategory(
    val categoryId: String,
    val confidence: Float
)
```

### Step 2: Explanation Builder

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/domain/ExplanationBuilder.kt
package com.ledgerlens.domain

class ExplanationBuilder {

    /**
     * Build explanation for a rule match.
     */
    fun forRule(
        rule: Rule,
        confidence: Float = 1.0f
    ): CategoryExplanation {
        return CategoryExplanation(
            source = ExplanationSource.RULE,
            confidence = confidence,
            contributors = listOf(
                ExplanationContributor.RuleMatch(
                    weight = 1.0f,
                    ruleId = rule.id,
                    ruleName = rule.name ?: "${rule.ruleType}: ${rule.matchExpression}",
                    matchType = rule.ruleType,
                    pattern = rule.matchExpression
                )
            )
        )
    }

    /**
     * Build explanation for a merchant prior.
     */
    fun forMerchantPrior(
        merchant: Merchant,
        observations: Int,
        confidence: Float
    ): CategoryExplanation {
        return CategoryExplanation(
            source = ExplanationSource.MERCHANT_PRIOR,
            confidence = confidence,
            contributors = listOf(
                ExplanationContributor.MerchantPrior(
                    weight = 1.0f,
                    merchantId = merchant.id,
                    priorCategory = merchant.categoryId ?: "uncategorized",
                    observations = observations
                )
            )
        )
    }

    /**
     * Build explanation for ML model prediction.
     */
    fun forModelPrediction(
        predictions: List<CategoryPrediction>,
        modelInfo: ModelInfo,
        topTokens: List<String>
    ): CategoryExplanation {
        val topPrediction = predictions.first()
        val alternatives = predictions.drop(1).take(2).map {
            AlternativeCategory(it.categoryId, it.confidence)
        }

        return CategoryExplanation(
            source = ExplanationSource.ML_MODEL,
            confidence = topPrediction.confidence,
            contributors = listOf(
                ExplanationContributor.ModelPrediction(
                    weight = 1.0f,
                    modelVersion = modelInfo.version,
                    topTokens = topTokens.take(5)
                )
            ),
            alternatives = alternatives
        )
    }

    /**
     * Build composite explanation with multiple contributors.
     */
    fun composite(
        source: ExplanationSource,
        contributors: List<ExplanationContributor>,
        alternatives: List<AlternativeCategory> = emptyList()
    ): CategoryExplanation {
        val totalWeight = contributors.sumOf { it.weight.toDouble() }
        val confidence = contributors
            .map { it.weight / totalWeight }
            .sum()
            .toFloat()
            .coerceIn(0f, 1f)

        return CategoryExplanation(
            source = source,
            confidence = confidence,
            contributors = contributors,
            alternatives = alternatives
        )
    }

    /**
     * Build explanation for user override.
     */
    fun forUserOverride(): CategoryExplanation {
        return CategoryExplanation(
            source = ExplanationSource.USER_OVERRIDE,
            confidence = 1.0f
        )
    }
}
```

### Step 3: Explanation Renderer (for UI)

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/domain/ExplanationRenderer.kt
package com.ledgerlens.domain

class ExplanationRenderer(
    private val categoryRepository: CategoryRepository
) {

    /**
     * Render explanation to human-readable text.
     */
    suspend fun render(
        explanation: CategoryExplanation,
        categoryName: String
    ): String {
        return when (explanation.source) {
            ExplanationSource.USER_OVERRIDE ->
                "You manually categorized this as $categoryName"

            ExplanationSource.RULE -> {
                val ruleMatch = explanation.contributors
                    .filterIsInstance<ExplanationContributor.RuleMatch>()
                    .firstOrNull()

                if (ruleMatch != null) {
                    "Matched rule: ${ruleMatch.ruleName}"
                } else {
                    "Matched a categorization rule"
                }
            }

            ExplanationSource.MERCHANT_PRIOR -> {
                val prior = explanation.contributors
                    .filterIsInstance<ExplanationContributor.MerchantPrior>()
                    .firstOrNull()

                if (prior != null) {
                    "Based on ${prior.observations} previous transactions with this merchant"
                } else {
                    "Based on this merchant's history"
                }
            }

            ExplanationSource.ML_MODEL -> {
                val conf = (explanation.confidence * 100).toInt()
                buildString {
                    append("AI predicted $categoryName ($conf% confidence)")
                    if (explanation.alternatives.isNotEmpty()) {
                        val alts = explanation.alternatives.map { alt ->
                            val altName = categoryRepository.getById(alt.categoryId)?.name
                                ?: alt.categoryId
                            "$altName (${(alt.confidence * 100).toInt()}%)"
                        }
                        append("\nAlternatives: ${alts.joinToString(", ")}")
                    }
                }
            }

            ExplanationSource.DEFAULT ->
                "Default category"
        }
    }
}
```

---

## Acceptance Criteria

- [ ] Explanation schema matches spec
- [ ] All contributor types implemented
- [ ] Serialization/deserialization works
- [ ] Explanation builder creates valid explanations
- [ ] Renderer produces readable text
- [ ] Alternatives included for ML predictions

---

## Estimated Complexity

**Medium** - Defined schema with serialization.
