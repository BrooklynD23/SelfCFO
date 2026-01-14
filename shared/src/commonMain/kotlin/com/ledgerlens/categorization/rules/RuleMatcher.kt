package com.ledgerlens.categorization.rules

import com.ledgerlens.categorization.TransactionFeatures
import kotlinx.datetime.LocalDate

class RuleMatcher {

    fun matches(condition: RuleCondition, features: TransactionFeatures, transactionDate: LocalDate? = null): Boolean {
        return when (condition) {
            is MerchantContains -> matchesMerchantContains(condition, features)
            is MerchantEquals -> matchesMerchantEquals(condition, features)
            is DescriptionMatches -> matchesDescriptionMatches(condition, features)
            is DescriptionContains -> matchesDescriptionContains(condition, features)
            is AmountRange -> matchesAmountRange(condition, features)
            is AmountEquals -> matchesAmountEquals(condition, features)
            is DateRange -> matchesDateRange(condition, transactionDate)
            is DayOfWeek -> matchesDayOfWeek(condition, features)
            is DayOfMonth -> matchesDayOfMonth(condition, features)
            is TransactionType -> matchesTransactionType(condition, features)
            is AccountEquals -> matchesAccountEquals(condition, features)
            is ConditionGroup -> matchesConditionGroup(condition, features, transactionDate)
        }
    }

    fun evaluate(rule: CategoryRule, features: TransactionFeatures, transactionDate: LocalDate? = null): RuleMatchResult {
        if (!rule.enabled) return RuleMatchResult.noMatch(rule)
        val matched = matches(rule.conditions, features, transactionDate)
        return if (matched) RuleMatchResult.match(rule, "Matched: ${rule.conditions.describe()}") 
               else RuleMatchResult.noMatch(rule)
    }

    private fun matchesMerchantContains(condition: MerchantContains, features: TransactionFeatures): Boolean {
        val merchant = if (condition.caseSensitive) features.merchantNormalized else features.merchantNormalized.lowercase()
        val pattern = if (condition.caseSensitive) condition.pattern else condition.pattern.lowercase()
        return merchant.contains(pattern)
    }

    private fun matchesMerchantEquals(condition: MerchantEquals, features: TransactionFeatures): Boolean {
        return if (condition.caseSensitive) features.merchantNormalized == condition.value
               else features.merchantNormalized.equals(condition.value, ignoreCase = true)
    }

    private fun matchesDescriptionMatches(condition: DescriptionMatches, features: TransactionFeatures): Boolean {
        val options = if (condition.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        return try { Regex(condition.pattern, options).containsMatchIn(features.descriptionRaw) } catch (e: Exception) { false }
    }

    private fun matchesDescriptionContains(condition: DescriptionContains, features: TransactionFeatures): Boolean {
        val description = if (condition.caseSensitive) features.descriptionRaw else features.descriptionRaw.lowercase()
        val keywords = if (condition.caseSensitive) condition.keywords else condition.keywords.map { it.lowercase() }
        return if (condition.matchAll) keywords.all { description.contains(it) } else keywords.any { description.contains(it) }
    }

    private fun matchesAmountRange(condition: AmountRange, features: TransactionFeatures): Boolean {
        val amount = if (condition.absolute) kotlin.math.abs(features.amountCents) else features.amountCents
        val minOk = condition.minCents == null || amount >= condition.minCents
        val maxOk = condition.maxCents == null || amount <= condition.maxCents
        return minOk && maxOk
    }

    private fun matchesAmountEquals(condition: AmountEquals, features: TransactionFeatures): Boolean {
        val amount = if (condition.absolute) kotlin.math.abs(features.amountCents) else features.amountCents
        return amount == condition.amountCents
    }

    private fun matchesDateRange(condition: DateRange, transactionDate: LocalDate?): Boolean {
        if (transactionDate == null) return true
        val startOk = condition.startDate == null || transactionDate >= condition.startDate
        val endOk = condition.endDate == null || transactionDate <= condition.endDate
        return startOk && endOk
    }

    private fun matchesDayOfWeek(condition: DayOfWeek, features: TransactionFeatures): Boolean {
        return (features.dayOfWeek + 1) in condition.days
    }

    private fun matchesDayOfMonth(condition: DayOfMonth, features: TransactionFeatures): Boolean {
        return features.dayOfMonth in condition.days
    }

    private fun matchesTransactionType(condition: TransactionType, features: TransactionFeatures): Boolean {
        return features.isDebit == condition.isDebit
    }

    private fun matchesAccountEquals(condition: AccountEquals, features: TransactionFeatures): Boolean {
        return features.accountId == condition.accountId
    }

    private fun matchesConditionGroup(group: ConditionGroup, features: TransactionFeatures, transactionDate: LocalDate?): Boolean {
        return when (group.operator) {
            LogicalOperator.AND -> group.conditions.all { matches(it, features, transactionDate) }
            LogicalOperator.OR -> group.conditions.any { matches(it, features, transactionDate) }
        }
    }
}
