package com.ledgerlens.categorization.rules

import com.ledgerlens.categorization.TransactionFeatures
import kotlinx.datetime.LocalDate

class RuleMatcher {
    fun matches(condition: RuleCondition, features: TransactionFeatures, transactionDate: LocalDate? = null): Boolean = when (condition) {
        is MerchantContains -> { val m = if (condition.caseSensitive) features.merchantNormalized else features.merchantNormalized.lowercase(); val p = if (condition.caseSensitive) condition.pattern else condition.pattern.lowercase(); m.contains(p) }
        is MerchantEquals -> if (condition.caseSensitive) features.merchantNormalized == condition.value else features.merchantNormalized.equals(condition.value, ignoreCase = true)
        is DescriptionMatches -> try { Regex(condition.pattern, if (condition.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)).containsMatchIn(features.descriptionRaw) } catch (e: Exception) { false }
        is DescriptionContains -> { val d = if (condition.caseSensitive) features.descriptionRaw else features.descriptionRaw.lowercase(); val k = if (condition.caseSensitive) condition.keywords else condition.keywords.map { it.lowercase() }; if (condition.matchAll) k.all { d.contains(it) } else k.any { d.contains(it) } }
        is AmountRange -> { val a = if (condition.absolute) kotlin.math.abs(features.amountCents) else features.amountCents; (condition.minCents == null || a >= condition.minCents) && (condition.maxCents == null || a <= condition.maxCents) }
        is AmountEquals -> { val a = if (condition.absolute) kotlin.math.abs(features.amountCents) else features.amountCents; a == condition.amountCents }
        is DateRange -> transactionDate == null || ((condition.startDate == null || transactionDate >= condition.startDate) && (condition.endDate == null || transactionDate <= condition.endDate))
        is DayOfWeek -> (features.dayOfWeek + 1) in condition.days
        is DayOfMonth -> features.dayOfMonth in condition.days
        is TransactionType -> features.isDebit == condition.isDebit
        is AccountEquals -> features.accountId == condition.accountId
        is ConditionGroup -> when (condition.operator) { LogicalOperator.AND -> condition.conditions.all { matches(it, features, transactionDate) }; LogicalOperator.OR -> condition.conditions.any { matches(it, features, transactionDate) } }
    }

    fun evaluate(rule: CategoryRule, features: TransactionFeatures, transactionDate: LocalDate? = null): RuleMatchResult {
        if (!rule.enabled) return RuleMatchResult.noMatch(rule)
        return if (matches(rule.conditions, features, transactionDate)) RuleMatchResult.match(rule, "Matched: ${rule.conditions.describe()}") else RuleMatchResult.noMatch(rule)
    }
}
