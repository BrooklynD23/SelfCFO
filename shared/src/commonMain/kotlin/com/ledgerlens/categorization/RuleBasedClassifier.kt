package com.ledgerlens.categorization

/**
 * Rule-based classifier using deterministic keyword and merchant matching.
 *
 * This serves as a high-confidence fallback when ML models are uncertain,
 * and provides predictable, explainable categorization for common cases.
 */
class RuleBasedClassifier(
    private val rules: List<ClassificationRule> = defaultRules()
) : TransactionClassifier {

    override val name: String = "rule-based"
    override val priority: Int = 100

    override fun classify(features: TransactionFeatures): ClassificationResult {
        val sortedRules = rules.filter { it.enabled }.sortedByDescending { it.priority }

        for (rule in sortedRules) {
            if (rule.matches(features)) {
                return ClassificationResult(
                    categoryId = rule.categoryId,
                    confidence = rule.confidence,
                    alternatives = emptyList(),
                    explanation = ClassificationExplanation(
                        classifierUsed = name,
                        reason = "Matched rule: ${rule.name}",
                        ruleMatched = rule.id,
                        tokenMatches = rule.getMatchedTokens(features)
                    )
                )
            }
        }

        return ClassificationResult.unknown()
    }

    override fun canClassify(features: TransactionFeatures): Boolean = rules.isNotEmpty()

    companion object {
        fun defaultRules(): List<ClassificationRule> = listOf(
            // Groceries
            MerchantContainsRule("grocery-walmart", "Groceries", listOf("walmart", "wal-mart"), 0.9f, 100),
            MerchantContainsRule("grocery-costco", "Groceries", listOf("costco"), 0.85f, 99),
            MerchantContainsRule("grocery-target", "Groceries", listOf("target"), 0.7f, 50),
            MerchantContainsRule("grocery-kroger", "Groceries", listOf("kroger", "fred meyer", "ralphs", "king soopers"), 0.9f, 100),
            MerchantContainsRule("grocery-safeway", "Groceries", listOf("safeway", "albertsons", "vons"), 0.9f, 100),
            MerchantContainsRule("grocery-whole-foods", "Groceries", listOf("whole foods", "wholefoods"), 0.9f, 100),
            MerchantContainsRule("grocery-trader-joes", "Groceries", listOf("trader joe"), 0.95f, 100),

            // Restaurants / Dining
            MerchantContainsRule("dining-mcdonalds", "Dining", listOf("mcdonald"), 0.95f, 100),
            MerchantContainsRule("dining-starbucks", "Dining", listOf("starbucks", "sbux"), 0.95f, 100),
            MerchantContainsRule("dining-chipotle", "Dining", listOf("chipotle"), 0.95f, 100),
            MerchantContainsRule("dining-chickfila", "Dining", listOf("chick-fil-a", "chickfila"), 0.95f, 100),
            MerchantContainsRule("dining-subway", "Dining", listOf("subway"), 0.9f, 90),
            MerchantContainsRule("dining-doordash", "Dining", listOf("doordash"), 0.9f, 100),
            MerchantContainsRule("dining-ubereats", "Dining", listOf("uber eats", "ubereats"), 0.9f, 100),
            MerchantContainsRule("dining-grubhub", "Dining", listOf("grubhub"), 0.9f, 100),
            DescriptionContainsRule("dining-restaurant", "Dining", listOf("restaurant", "cafe", "bistro", "grill", "diner"), 0.7f, 50),

            // Transportation
            MerchantContainsRule("transport-uber", "Transportation", listOf("uber trip", "uber   trip"), 0.95f, 100),
            MerchantContainsRule("transport-lyft", "Transportation", listOf("lyft"), 0.95f, 100),
            MerchantContainsRule("transport-gas-chevron", "Transportation", listOf("chevron"), 0.9f, 100),
            MerchantContainsRule("transport-gas-shell", "Transportation", listOf("shell"), 0.85f, 90),
            MerchantContainsRule("transport-gas-exxon", "Transportation", listOf("exxon", "mobil"), 0.9f, 100),
            MerchantContainsRule("transport-gas-bp", "Transportation", listOf("bp "), 0.85f, 90),
            DescriptionContainsRule("transport-parking", "Transportation", listOf("parking", "garage"), 0.8f, 80),

            // Subscriptions / Entertainment
            MerchantContainsRule("subscription-netflix", "Entertainment", listOf("netflix"), 0.95f, 100),
            MerchantContainsRule("subscription-spotify", "Entertainment", listOf("spotify"), 0.95f, 100),
            MerchantContainsRule("subscription-hulu", "Entertainment", listOf("hulu"), 0.95f, 100),
            MerchantContainsRule("subscription-disney", "Entertainment", listOf("disney+", "disney plus"), 0.95f, 100),
            MerchantContainsRule("subscription-hbo", "Entertainment", listOf("hbo", "max.com"), 0.9f, 100),
            MerchantContainsRule("subscription-youtube", "Entertainment", listOf("youtube"), 0.9f, 100),
            MerchantContainsRule("subscription-apple", "Entertainment", listOf("apple.com/bill", "apple music"), 0.85f, 90),

            // Shopping
            MerchantContainsRule("shopping-amazon", "Shopping", listOf("amazon", "amzn"), 0.8f, 80),
            MerchantContainsRule("shopping-ebay", "Shopping", listOf("ebay"), 0.85f, 90),
            MerchantContainsRule("shopping-etsy", "Shopping", listOf("etsy"), 0.9f, 100),

            // Utilities
            DescriptionContainsRule("utility-electric", "Utilities", listOf("electric", "power", "energy"), 0.75f, 70),
            DescriptionContainsRule("utility-water", "Utilities", listOf("water utility", "water dept"), 0.8f, 80),
            DescriptionContainsRule("utility-gas", "Utilities", listOf("gas company", "natural gas"), 0.8f, 80),
            DescriptionContainsRule("utility-internet", "Utilities", listOf("comcast", "xfinity", "spectrum", "at&t", "verizon fios"), 0.85f, 90),

            // Healthcare
            MerchantContainsRule("health-cvs", "Healthcare", listOf("cvs"), 0.7f, 60),
            MerchantContainsRule("health-walgreens", "Healthcare", listOf("walgreens"), 0.7f, 60),
            DescriptionContainsRule("health-pharmacy", "Healthcare", listOf("pharmacy", "rx", "prescription"), 0.8f, 80),
            DescriptionContainsRule("health-doctor", "Healthcare", listOf("medical", "clinic", "hospital", "doctor", "physician"), 0.85f, 90),

            // Transfers
            MerchantContainsRule("transfer-venmo", "Transfer", listOf("venmo"), 0.95f, 100),
            MerchantContainsRule("transfer-zelle", "Transfer", listOf("zelle"), 0.95f, 100),
            MerchantContainsRule("transfer-paypal", "Transfer", listOf("paypal"), 0.8f, 80),
            DescriptionContainsRule("transfer-wire", "Transfer", listOf("wire transfer", "ach transfer"), 0.85f, 90),

            // Income
            DescriptionContainsRule("income-payroll", "Income", listOf("payroll", "direct dep", "salary", "wages"), 0.9f, 100),
            DescriptionContainsRule("income-interest", "Income", listOf("interest payment", "interest earned"), 0.9f, 100),
            DescriptionContainsRule("income-dividend", "Income", listOf("dividend"), 0.9f, 100),

            // Fees
            DescriptionContainsRule("fee-atm", "Fees", listOf("atm fee", "atm surcharge"), 0.95f, 100),
            DescriptionContainsRule("fee-overdraft", "Fees", listOf("overdraft", "nsf fee"), 0.95f, 100),
            DescriptionContainsRule("fee-monthly", "Fees", listOf("monthly fee", "service fee", "maintenance fee"), 0.9f, 100),
        )
    }
}

/**
 * Base interface for classification rules.
 */
interface ClassificationRule {
    val id: String
    val categoryId: String
    val confidence: Float
    val priority: Int
    val enabled: Boolean
    val name: String get() = id

    fun matches(features: TransactionFeatures): Boolean
    fun getMatchedTokens(features: TransactionFeatures): List<String> = emptyList()
}

/**
 * Rule that matches if merchant name contains any of the patterns.
 */
data class MerchantContainsRule(
    override val id: String,
    override val categoryId: String,
    val patterns: List<String>,
    override val confidence: Float = 0.9f,
    override val priority: Int = 100,
    override val enabled: Boolean = true
) : ClassificationRule {
    override fun matches(features: TransactionFeatures): Boolean {
        val merchant = features.merchantNormalized.lowercase()
        return patterns.any { pattern -> merchant.contains(pattern.lowercase()) }
    }

    override fun getMatchedTokens(features: TransactionFeatures): List<String> {
        val merchant = features.merchantNormalized.lowercase()
        return patterns.filter { merchant.contains(it.lowercase()) }
    }
}

/**
 * Rule that matches if description contains any of the keywords.
 */
data class DescriptionContainsRule(
    override val id: String,
    override val categoryId: String,
    val keywords: List<String>,
    override val confidence: Float = 0.8f,
    override val priority: Int = 50,
    override val enabled: Boolean = true
) : ClassificationRule {
    override fun matches(features: TransactionFeatures): Boolean {
        val description = features.descriptionRaw.lowercase()
        return keywords.any { keyword -> description.contains(keyword.lowercase()) }
    }

    override fun getMatchedTokens(features: TransactionFeatures): List<String> {
        val description = features.descriptionRaw.lowercase()
        return keywords.filter { description.contains(it.lowercase()) }
    }
}

/**
 * Rule that matches transactions within an amount range.
 */
data class AmountRangeRule(
    override val id: String,
    override val categoryId: String,
    val minCents: Long?,
    val maxCents: Long?,
    val additionalMatcher: ((TransactionFeatures) -> Boolean)? = null,
    override val confidence: Float = 0.7f,
    override val priority: Int = 30,
    override val enabled: Boolean = true
) : ClassificationRule {
    override fun matches(features: TransactionFeatures): Boolean {
        val amount = kotlin.math.abs(features.amountCents)
        val inRange = (minCents == null || amount >= minCents) &&
                (maxCents == null || amount <= maxCents)
        return inRange && (additionalMatcher?.invoke(features) ?: true)
    }
}

/**
 * Composite rule that requires all sub-rules to match.
 */
data class CompositeAndRule(
    override val id: String,
    override val categoryId: String,
    val rules: List<ClassificationRule>,
    override val confidence: Float = 0.85f,
    override val priority: Int = 80,
    override val enabled: Boolean = true
) : ClassificationRule {
    override fun matches(features: TransactionFeatures): Boolean {
        return rules.all { it.matches(features) }
    }

    override fun getMatchedTokens(features: TransactionFeatures): List<String> {
        return rules.flatMap { it.getMatchedTokens(features) }.distinct()
    }
}
