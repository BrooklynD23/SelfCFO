package com.ledgerlens.categorization.pipeline

interface TransactionClassifier {
    fun classify(features: TransactionFeatures): ClassificationResult
    fun canClassify(features: TransactionFeatures): Boolean = true
    val name: String
    val priority: Int get() = 0
}

object FallbackClassifier : TransactionClassifier {
    override val name = "fallback"
    override val priority = Int.MIN_VALUE
    override fun classify(features: TransactionFeatures) = ClassificationResult.unknown()
}

class RuleBasedClassifier(private val rules: List<ClassificationRule> = emptyList()) : TransactionClassifier {
    override val name = "rule-based"
    override val priority = 100

    override fun classify(features: TransactionFeatures): ClassificationResult {
        for (rule in rules.filter { it.enabled }.sortedByDescending { it.priority }) {
            if (rule.matches(features)) {
                return ClassificationResult(rule.categoryId, rule.confidence, emptyList(),
                    ClassificationExplanation(name, "Matched rule: ${rule.name}", ruleMatched = rule.id, tokenMatches = rule.getMatchedTokens(features)))
            }
        }
        return ClassificationResult.unknown()
    }
    override fun canClassify(features: TransactionFeatures) = rules.isNotEmpty()
}

interface ClassificationRule {
    val id: String; val categoryId: String; val confidence: Float; val priority: Int; val enabled: Boolean
    val name: String get() = id
    fun matches(features: TransactionFeatures): Boolean
    fun getMatchedTokens(features: TransactionFeatures): List<String> = emptyList()
}

data class MerchantContainsRule(override val id: String, override val categoryId: String, val patterns: List<String>, override val confidence: Float = 0.9f, override val priority: Int = 100, override val enabled: Boolean = true) : ClassificationRule {
    override fun matches(features: TransactionFeatures) = patterns.any { features.merchantNormalized.lowercase().contains(it.lowercase()) }
    override fun getMatchedTokens(features: TransactionFeatures) = patterns.filter { features.merchantNormalized.lowercase().contains(it.lowercase()) }
}

data class DescriptionContainsRule(override val id: String, override val categoryId: String, val keywords: List<String>, override val confidence: Float = 0.8f, override val priority: Int = 50, override val enabled: Boolean = true) : ClassificationRule {
    override fun matches(features: TransactionFeatures) = keywords.any { features.descriptionRaw.lowercase().contains(it.lowercase()) }
    override fun getMatchedTokens(features: TransactionFeatures) = keywords.filter { features.descriptionRaw.lowercase().contains(it.lowercase()) }
}

data class AmountRangeRule(override val id: String, override val categoryId: String, val minCents: Long?, val maxCents: Long?, val additionalMatcher: ((TransactionFeatures) -> Boolean)? = null, override val confidence: Float = 0.7f, override val priority: Int = 30, override val enabled: Boolean = true) : ClassificationRule {
    override fun matches(features: TransactionFeatures): Boolean {
        val amt = kotlin.math.abs(features.amountCents)
        return (minCents == null || amt >= minCents) && (maxCents == null || amt <= maxCents) && (additionalMatcher?.invoke(features) ?: true)
    }
}
