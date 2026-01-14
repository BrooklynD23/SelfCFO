package com.ledgerlens.categorization

/**
 * Generates human-readable explanations for category classifications.
 * Combines multiple factors into coherent explanations.
 */
class ExplanationGenerator {

    /**
     * Generate a CategoryExplanation from classification factors.
     *
     * @param factors The factors that contributed to the classification
     * @param classifierSource The name of the classifier that produced the result
     * @return A complete CategoryExplanation
     */
    fun generate(
        factors: List<ExplanationFactor>,
        classifierSource: String
    ): CategoryExplanation {
        if (factors.isEmpty()) {
            return CategoryExplanation.unknown(classifierSource)
        }

        val collection = FactorCollection(factors).normalize()
        val primaryFactor = collection.primaryFactor
            ?: return CategoryExplanation.unknown(classifierSource)

        val primaryReason = determinePrimaryReason(collection)
        val confidence = calculateOverallConfidence(collection)
        val summary = generateSummary(collection, primaryReason)

        return CategoryExplanation(
            primaryReason = primaryReason,
            factors = collection,
            overallConfidence = confidence,
            classifierSource = classifierSource,
            humanReadableSummary = summary
        )
    }

    /**
     * Generate explanation for a merchant match.
     */
    fun forMerchantMatch(
        merchantName: String,
        confidence: Float,
        matchType: MerchantMatchType = MerchantMatchType.EXACT,
        classifierSource: String = "merchant_classifier"
    ): CategoryExplanation {
        val factor = ExplanationFactor.merchantMatch(
            merchantName = merchantName,
            weight = 1.0f,
            matchType = matchType.name.lowercase()
        )

        return CategoryExplanation(
            primaryReason = ExplanationReason.MERCHANT_MATCH,
            factors = FactorCollection.of(factor),
            overallConfidence = confidence,
            classifierSource = classifierSource,
            humanReadableSummary = generateMerchantSummary(merchantName, matchType)
        )
    }

    /**
     * Generate explanation for keyword matches.
     */
    fun forKeywordMatches(
        keywords: List<KeywordMatch>,
        confidence: Float,
        classifierSource: String = "keyword_classifier"
    ): CategoryExplanation {
        if (keywords.isEmpty()) {
            return CategoryExplanation.unknown(classifierSource)
        }

        val totalWeight = keywords.sumOf { it.weight.toDouble() }.toFloat()
        val factors = keywords.map { kw ->
            ExplanationFactor.keywordMatch(
                keyword = kw.keyword,
                weight = if (totalWeight > 0) kw.weight / totalWeight else 1.0f / keywords.size,
                position = kw.position
            )
        }

        return CategoryExplanation(
            primaryReason = ExplanationReason.KEYWORD_MATCH,
            factors = FactorCollection(factors),
            overallConfidence = confidence,
            classifierSource = classifierSource,
            humanReadableSummary = generateKeywordSummary(keywords)
        )
    }

    /**
     * Generate explanation for user history match.
     */
    fun forUserHistory(
        pattern: String,
        occurrences: Int,
        confidence: Float,
        classifierSource: String = "history_classifier"
    ): CategoryExplanation {
        val factor = ExplanationFactor.userHistory(
            description = pattern,
            weight = 1.0f,
            occurrences = occurrences
        )

        return CategoryExplanation(
            primaryReason = ExplanationReason.USER_HISTORY,
            factors = FactorCollection.of(factor),
            overallConfidence = confidence,
            classifierSource = classifierSource,
            humanReadableSummary = "You've categorized similar transactions $occurrences times before"
        )
    }

    /**
     * Generate explanation for a rule match.
     */
    fun forRuleMatch(
        ruleId: String,
        ruleName: String,
        classifierSource: String = "rule_engine"
    ): CategoryExplanation = CategoryExplanation.fromRule(ruleId, ruleName, classifierSource)

    /**
     * Generate explanation combining multiple factor types.
     */
    fun forCombinedFactors(
        merchantFactor: ExplanationFactor? = null,
        keywordFactors: List<ExplanationFactor> = emptyList(),
        amountFactor: ExplanationFactor? = null,
        historyFactor: ExplanationFactor? = null,
        confidence: Float,
        classifierSource: String = "combined_classifier"
    ): CategoryExplanation {
        val allFactors = buildList {
            merchantFactor?.let { add(it) }
            addAll(keywordFactors)
            amountFactor?.let { add(it) }
            historyFactor?.let { add(it) }
        }

        if (allFactors.isEmpty()) {
            return CategoryExplanation.unknown(classifierSource)
        }

        val collection = FactorCollection(allFactors).normalize()
        val primaryReason = if (allFactors.size > 1) {
            ExplanationReason.COMBINED_FACTORS
        } else {
            collection.primaryFactor?.reason ?: ExplanationReason.DEFAULT_FALLBACK
        }

        return CategoryExplanation(
            primaryReason = primaryReason,
            factors = collection,
            overallConfidence = confidence,
            classifierSource = classifierSource,
            humanReadableSummary = generateCombinedSummary(collection)
        )
    }

    private fun determinePrimaryReason(factors: FactorCollection): ExplanationReason {
        val significantFactors = factors.significantFactors
        return when {
            significantFactors.isEmpty() -> ExplanationReason.DEFAULT_FALLBACK
            significantFactors.size == 1 -> significantFactors.first().reason
            else -> {
                val primaryFactor = factors.primaryFactor
                if (primaryFactor != null && primaryFactor.weight >= 0.5f) {
                    primaryFactor.reason
                } else {
                    ExplanationReason.COMBINED_FACTORS
                }
            }
        }
    }

    private fun calculateOverallConfidence(factors: FactorCollection): Float {
        if (factors.factors.isEmpty()) return 0.0f

        val weightedSum = factors.factors.sumOf {
            (it.weight * (it.rawScore ?: it.weight)).toDouble()
        }.toFloat()

        return weightedSum.coerceIn(0.0f, 1.0f)
    }

    private fun generateSummary(
        factors: FactorCollection,
        primaryReason: ExplanationReason
    ): String {
        val topFactors = factors.sortedByWeight.take(2)
        if (topFactors.isEmpty()) {
            return ExplanationReason.defaultDescription(ExplanationReason.DEFAULT_FALLBACK)
        }

        return when (primaryReason) {
            ExplanationReason.COMBINED_FACTORS -> generateCombinedSummary(factors)
            else -> {
                val primary = topFactors.first()
                "${ExplanationReason.defaultDescription(primary.reason)}: '${primary.value}'"
            }
        }
    }

    private fun generateMerchantSummary(
        merchantName: String,
        matchType: MerchantMatchType
    ): String = when (matchType) {
        MerchantMatchType.EXACT -> "Merchant '$merchantName' recognized"
        MerchantMatchType.NORMALIZED -> "Merchant matched as '$merchantName'"
        MerchantMatchType.ALIAS -> "Merchant identified as '$merchantName'"
        MerchantMatchType.FUZZY -> "Merchant similar to '$merchantName'"
    }

    private fun generateKeywordSummary(keywords: List<KeywordMatch>): String {
        val topKeywords = keywords.sortedByDescending { it.weight }.take(3)
        return when (topKeywords.size) {
            1 -> "Keyword '${topKeywords[0].keyword}' matched"
            2 -> "Keywords '${topKeywords[0].keyword}' and '${topKeywords[1].keyword}' matched"
            else -> "Keywords '${topKeywords[0].keyword}', '${topKeywords[1].keyword}', and more matched"
        }
    }

    private fun generateCombinedSummary(factors: FactorCollection): String {
        val topTwo = factors.sortedByWeight.take(2)
        if (topTwo.isEmpty()) return "No clear categorization signal"
        if (topTwo.size == 1) {
            val f = topTwo[0]
            return "${ExplanationReason.defaultDescription(f.reason)}: '${f.value}'"
        }

        val f1 = topTwo[0]
        val f2 = topTwo[1]
        return "${f1.value} (${f1.weightPercent}%) + ${f2.value} (${f2.weightPercent}%)"
    }
}

/**
 * Type of merchant name match.
 */
enum class MerchantMatchType {
    EXACT,
    NORMALIZED,
    ALIAS,
    FUZZY
}

/**
 * A keyword match with its weight and position.
 */
data class KeywordMatch(
    val keyword: String,
    val weight: Float,
    val position: Int? = null
)
