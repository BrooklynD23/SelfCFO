# 05: Rules Engine

## Overview

Implement user-defined categorization rules with pattern matching and priority ordering.

---

## Implementation Steps

### Step 1: Rule Matcher

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/rules/RuleMatcher.kt
package com.ledgerlens.rules

interface RuleMatcher {
    fun matches(transaction: TransactionForMatching, rule: Rule): Boolean
}

data class TransactionForMatching(
    val merchantNormalized: String,
    val descriptionRaw: String,
    val amount: Money,
    val accountId: String?
)

class CompositeRuleMatcher : RuleMatcher {
    private val matchers = mapOf(
        "merchant_contains" to MerchantContainsMatcher(),
        "merchant_equals" to MerchantEqualsMatcher(),
        "description_regex" to DescriptionRegexMatcher(),
        "amount_range" to AmountRangeMatcher(),
        "account" to AccountMatcher()
    )

    override fun matches(transaction: TransactionForMatching, rule: Rule): Boolean {
        val matcher = matchers[rule.ruleType]
            ?: throw IllegalArgumentException("Unknown rule type: ${rule.ruleType}")
        return matcher.matches(transaction, rule)
    }
}

class MerchantContainsMatcher : RuleMatcher {
    override fun matches(transaction: TransactionForMatching, rule: Rule): Boolean {
        return transaction.merchantNormalized
            .uppercase()
            .contains(rule.matchExpression.uppercase())
    }
}

class MerchantEqualsMatcher : RuleMatcher {
    override fun matches(transaction: TransactionForMatching, rule: Rule): Boolean {
        return transaction.merchantNormalized
            .equals(rule.matchExpression, ignoreCase = true)
    }
}

class DescriptionRegexMatcher : RuleMatcher {
    override fun matches(transaction: TransactionForMatching, rule: Rule): Boolean {
        return try {
            val regex = Regex(rule.matchExpression, RegexOption.IGNORE_CASE)
            regex.containsMatchIn(transaction.descriptionRaw)
        } catch (e: Exception) {
            false  // Invalid regex doesn't match
        }
    }
}

class AmountRangeMatcher : RuleMatcher {
    override fun matches(transaction: TransactionForMatching, rule: Rule): Boolean {
        // Expression format: "min:max" e.g., "0:50" or "100:"
        val parts = rule.matchExpression.split(":")
        if (parts.size != 2) return false

        val min = parts[0].toLongOrNull()?.let { Money(it * 100, transaction.amount.currencyCode) }
        val max = parts[1].toLongOrNull()?.let { Money(it * 100, transaction.amount.currencyCode) }

        val amount = transaction.amount.abs()

        return when {
            min != null && max != null -> amount >= min && amount <= max
            min != null -> amount >= min
            max != null -> amount <= max
            else -> false
        }
    }
}

class AccountMatcher : RuleMatcher {
    override fun matches(transaction: TransactionForMatching, rule: Rule): Boolean {
        return transaction.accountId == rule.matchExpression
    }
}
```

### Step 2: Rules Engine

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/rules/RulesEngine.kt
package com.ledgerlens.rules

class RulesEngine(
    private val ruleRepository: RuleRepository,
    private val matcher: RuleMatcher
) {

    /**
     * Find the first matching rule for a transaction.
     * Rules are evaluated in priority order (lower number = higher priority).
     */
    suspend fun findMatchingRule(
        transaction: TransactionForMatching
    ): RuleMatch? {
        val rules = ruleRepository.getEnabledRulesByPriority()

        for (rule in rules) {
            if (matcher.matches(transaction, rule)) {
                return RuleMatch(
                    rule = rule,
                    categoryId = rule.targetCategoryId
                )
            }
        }

        return null
    }

    /**
     * Test rules against historical transactions.
     * Returns count of matches per category.
     */
    suspend fun testRule(
        rule: Rule,
        transactions: List<TransactionForMatching>
    ): RuleTestResult {
        val matches = transactions.filter { matcher.matches(it, rule) }

        return RuleTestResult(
            totalTransactions = transactions.size,
            matchCount = matches.size,
            matchPercentage = matches.size.toFloat() / transactions.size,
            sampleMatches = matches.take(5)
        )
    }

    /**
     * Suggest a rule from a transaction.
     */
    fun suggestRule(
        transaction: TransactionForMatching,
        targetCategoryId: String
    ): Rule {
        // Default to merchant_contains with normalized merchant
        val pattern = extractMerchantPattern(transaction.merchantNormalized)

        return Rule(
            id = generateId(),
            ruleType = "merchant_contains",
            matchExpression = pattern,
            targetCategoryId = targetCategoryId,
            priority = 100,  // Default priority
            createdByUser = true,
            enabled = true,
            createdAt = Clock.System.now()
        )
    }

    private fun extractMerchantPattern(merchant: String): String {
        // Take first word or significant portion
        val words = merchant.split(" ")
        return if (words.first().length >= 4) {
            words.first()
        } else {
            words.take(2).joinToString(" ")
        }
    }
}

data class RuleMatch(
    val rule: Rule,
    val categoryId: String
)

data class RuleTestResult(
    val totalTransactions: Int,
    val matchCount: Int,
    val matchPercentage: Float,
    val sampleMatches: List<TransactionForMatching>
)
```

### Step 3: Rule CRUD Service

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/rules/RuleService.kt
package com.ledgerlens.rules

class RuleService(
    private val ruleRepository: RuleRepository,
    private val rulesEngine: RulesEngine,
    private val transactionRepository: TransactionRepository
) {

    suspend fun createRule(
        ruleType: String,
        matchExpression: String,
        targetCategoryId: String,
        priority: Int = 100
    ): Rule {
        validateRuleExpression(ruleType, matchExpression)

        val rule = Rule(
            id = generateId(),
            ruleType = ruleType,
            matchExpression = matchExpression,
            targetCategoryId = targetCategoryId,
            priority = priority,
            createdByUser = true,
            enabled = true,
            createdAt = Clock.System.now()
        )

        return ruleRepository.create(rule)
    }

    suspend fun updateRule(rule: Rule): Rule {
        validateRuleExpression(rule.ruleType, rule.matchExpression)
        return ruleRepository.update(rule)
    }

    suspend fun deleteRule(ruleId: String) {
        ruleRepository.delete(ruleId)
    }

    suspend fun reorderRules(ruleIds: List<String>) {
        ruleIds.forEachIndexed { index, id ->
            val rule = ruleRepository.getById(id) ?: return@forEachIndexed
            ruleRepository.update(rule.copy(priority = index))
        }
    }

    suspend fun testRuleOnHistory(rule: Rule): RuleTestResult {
        val recentTransactions = transactionRepository.getRecent(limit = 500)
        val forMatching = recentTransactions.map { it.toMatchingFormat() }
        return rulesEngine.testRule(rule, forMatching)
    }

    private fun validateRuleExpression(ruleType: String, expression: String) {
        when (ruleType) {
            "description_regex" -> {
                try {
                    Regex(expression)
                } catch (e: Exception) {
                    throw InvalidRuleException("Invalid regex: ${e.message}")
                }
            }
            "amount_range" -> {
                if (!expression.matches(Regex("""\d*:\d*"""))) {
                    throw InvalidRuleException("Amount range must be format 'min:max'")
                }
            }
        }
    }
}
```

---

## Acceptance Criteria

- [ ] All rule types implemented (merchant_contains, merchant_equals, description_regex, amount_range, account)
- [ ] Rules evaluated by priority order
- [ ] Rule testing on historical data works
- [ ] Rule suggestion from transaction works
- [ ] Rule CRUD operations work
- [ ] Invalid regex rejected
- [ ] Rule reordering works

---

## Estimated Complexity

**Medium** - Pattern matching with multiple rule types.
