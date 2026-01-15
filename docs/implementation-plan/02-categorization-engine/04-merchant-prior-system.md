# 04: Merchant Prior System

## Overview

Implement merchant-to-category mapping with immediate learning from corrections.

---

## Implementation Steps

### Step 1: Merchant Prior Repository

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/data/MerchantPriorRepository.kt
package com.ledgerlens.data

interface MerchantPriorRepository {
    /**
     * Get category for a merchant.
     */
    suspend fun getCategoryForMerchant(merchantId: String): MerchantPrior?

    /**
     * Update merchant's category (called on user correction).
     */
    suspend fun updateMerchantCategory(
        merchantId: String,
        categoryId: String,
        isCorrection: Boolean
    )

    /**
     * Get or create merchant by canonical name.
     */
    suspend fun getOrCreateMerchant(
        canonicalName: String,
        displayName: String? = null
    ): Merchant

    /**
     * Lookup merchant by alias pattern.
     */
    suspend fun findByAlias(rawDescription: String): Merchant?
}

data class MerchantPrior(
    val merchantId: String,
    val categoryId: String,
    val confidence: Float,
    val observationCount: Int,
    val lastUpdated: Instant
)
```

### Step 2: Recency-Weighted Learning

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ml/MerchantPriorLearner.kt
package com.ledgerlens.ml

class MerchantPriorLearner(
    private val merchantRepository: MerchantRepository,
    private val transactionRepository: TransactionRepository
) {

    companion object {
        const val DECAY_FACTOR = 0.9f  // Older observations worth less
        const val MIN_OBSERVATIONS_FOR_HIGH_CONFIDENCE = 3
    }

    /**
     * Learn category from correction.
     * This is called immediately when user corrects a category.
     */
    suspend fun learnFromCorrection(
        merchantId: String,
        newCategoryId: String
    ) {
        val merchant = merchantRepository.getById(merchantId)
            ?: return

        // Get historical observations for this merchant
        val history = transactionRepository.getCategoryHistoryForMerchant(merchantId)

        // Calculate new confidence with recency weighting
        val weightedCounts = mutableMapOf<String, Float>()
        history.forEachIndexed { index, (categoryId, _) ->
            val weight = DECAY_FACTOR.pow(index)  // More recent = higher weight
            weightedCounts[categoryId] = (weightedCounts[categoryId] ?: 0f) + weight
        }

        // Add the new correction with high weight
        weightedCounts[newCategoryId] = (weightedCounts[newCategoryId] ?: 0f) + 2f

        // Find best category
        val bestCategory = weightedCounts.maxByOrNull { it.value }
        val totalWeight = weightedCounts.values.sum()
        val confidence = (bestCategory?.value ?: 0f) / totalWeight

        // Update merchant
        merchantRepository.update(
            merchant.copy(
                categoryId = bestCategory?.key,
                transactionCount = history.size + 1
            )
        )
    }

    /**
     * Calculate confidence for a merchant prior.
     */
    suspend fun calculateConfidence(merchantId: String): Float {
        val history = transactionRepository.getCategoryHistoryForMerchant(merchantId)

        if (history.isEmpty()) return 0f
        if (history.size < MIN_OBSERVATIONS_FOR_HIGH_CONFIDENCE) {
            return 0.5f + (history.size * 0.1f)  // Lower confidence for few observations
        }

        // Calculate consistency
        val categoryCounts = history.groupingBy { it.first }.eachCount()
        val maxCount = categoryCounts.values.maxOrNull() ?: 0
        val consistency = maxCount.toFloat() / history.size

        return (0.7f + (consistency * 0.3f)).coerceAtMost(0.95f)
    }
}
```

### Step 3: Merchant Alias Matching

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ml/MerchantMatcher.kt
package com.ledgerlens.ml

class MerchantMatcher(
    private val merchantAliasRepository: MerchantAliasRepository
) {

    /**
     * Find merchant by raw description.
     * Checks aliases in order: exact, contains, regex
     */
    suspend fun match(rawDescription: String): MerchantMatch? {
        val normalized = rawDescription.uppercase().trim()

        // 1. Exact match
        val exactMatch = merchantAliasRepository.findExactMatch(normalized)
        if (exactMatch != null) {
            return MerchantMatch(
                merchant = exactMatch,
                matchType = MatchType.EXACT,
                confidence = 1.0f
            )
        }

        // 2. Contains match
        val containsMatches = merchantAliasRepository.findContainsMatches(normalized)
        if (containsMatches.isNotEmpty()) {
            // Take best match (longest pattern)
            val best = containsMatches.maxByOrNull { it.aliasPattern.length }!!
            return MerchantMatch(
                merchant = best.merchant,
                matchType = MatchType.CONTAINS,
                confidence = 0.9f
            )
        }

        // 3. Regex match (more expensive)
        val regexMatches = merchantAliasRepository.findRegexMatches(normalized)
        if (regexMatches.isNotEmpty()) {
            return MerchantMatch(
                merchant = regexMatches.first().merchant,
                matchType = MatchType.REGEX,
                confidence = 0.85f
            )
        }

        return null
    }

    /**
     * Create alias when user confirms a merchant.
     */
    suspend fun createAlias(
        merchant: Merchant,
        rawDescription: String,
        source: AliasSource
    ) {
        // Extract pattern from description
        val pattern = extractPattern(rawDescription)

        merchantAliasRepository.create(
            MerchantAlias(
                id = generateId(),
                merchantId = merchant.id,
                aliasPattern = pattern,
                matchType = "contains",
                source = source.name.lowercase()
            )
        )
    }

    private fun extractPattern(description: String): String {
        // Take first meaningful segment (before location/numbers)
        val cleaned = description.uppercase()
            .replace(Regex("""\d{4,}"""), "")  // Remove long numbers
            .replace(Regex("""\b[A-Z]{2}\s*\d{5}\b"""), "")  // Remove state+ZIP
            .trim()

        // Take up to first delimiter
        val delimiters = listOf(" - ", " * ", "  ")
        var result = cleaned
        for (delimiter in delimiters) {
            val idx = result.indexOf(delimiter)
            if (idx > 5) {
                result = result.substring(0, idx)
                break
            }
        }

        return result.take(30).trim()
    }
}

data class MerchantMatch(
    val merchant: Merchant,
    val matchType: MatchType,
    val confidence: Float
)

enum class AliasSource {
    PARSER,
    USER,
    LEARNED
}
```

---

## Acceptance Criteria

- [ ] Merchant lookup by canonical name works
- [ ] Alias matching (exact, contains, regex) works
- [ ] Corrections update merchant category immediately
- [ ] Recency weighting applied to observations
- [ ] Confidence calculation considers observation count
- [ ] New aliases created from user confirmations

---

## Estimated Complexity

**Medium** - Learning algorithm with recency weighting.
