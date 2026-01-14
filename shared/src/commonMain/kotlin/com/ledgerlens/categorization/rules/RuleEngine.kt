package com.ledgerlens.categorization.rules

import com.ledgerlens.categorization.ClassificationExplanation
import com.ledgerlens.categorization.ClassificationResult
import com.ledgerlens.categorization.TransactionFeatures
import kotlinx.datetime.LocalDate

class RuleEngine(private val matcher: RuleMatcher = RuleMatcher()) {
    fun evaluate(rules: List<CategoryRule>, features: TransactionFeatures, transactionDate: LocalDate? = null): RuleEngineResult {
        val sortedRules = rules.filter { it.enabled }.sortedByDescending { it.priority }
        val matchedRules = mutableListOf<RuleMatchResult>()
        var primaryCategoryResult: RuleMatchResult? = null
        val allActions = mutableListOf<RuleAction>()
        for (rule in sortedRules) {
            val result = matcher.evaluate(rule, features, transactionDate)
            if (result.matched) {
                matchedRules.add(result)
                allActions.addAll(result.appliedActions)
                if (primaryCategoryResult == null && rule.setsCategory) primaryCategoryResult = result
            }
        }
        return RuleEngineResult(matchedRules, primaryCategoryResult?.rule, mergeActions(allActions), detectConflicts(matchedRules))
    }

    fun toClassificationResult(result: RuleEngineResult): ClassificationResult {
        val categoryRule = result.primaryCategoryRule ?: return ClassificationResult.unknown()
        val action = categoryRule.actions.filterIsInstance<SetCategory>().firstOrNull() ?: return ClassificationResult.unknown()
        return ClassificationResult(action.categoryId, action.confidence, emptyList(), ClassificationExplanation("rules-engine", "Matched rule: ${categoryRule.name}", categoryRule.id, null, extractMatchedTokens(categoryRule)))
    }

    fun findMatchingRules(rules: List<CategoryRule>, features: TransactionFeatures, transactionDate: LocalDate? = null) =
        rules.filter { it.enabled && matcher.evaluate(it, features, transactionDate).matched }.sortedByDescending { it.priority }

    fun testRule(rule: CategoryRule, features: TransactionFeatures, transactionDate: LocalDate? = null) = matcher.evaluate(rule, features, transactionDate)

    private fun mergeActions(actions: List<RuleAction>): List<RuleAction> {
        val result = mutableListOf<RuleAction>()
        actions.filterIsInstance<SetCategory>().firstOrNull()?.let { result.add(it) }
        actions.filterIsInstance<SetMerchant>().firstOrNull()?.let { result.add(it) }
        actions.filterIsInstance<AddTag>().flatMap { it.tags }.distinct().takeIf { it.isNotEmpty() }?.let { result.add(AddTag(it)) }
        actions.filterIsInstance<RemoveTag>().flatMap { it.tags }.distinct().takeIf { it.isNotEmpty() }?.let { result.add(RemoveTag(it)) }
        actions.filterIsInstance<SetNote>().let { notes -> notes.firstOrNull { !it.appendMode }?.let { r -> val app = notes.filter { it.appendMode }; result.add(SetNote(buildString { append(r.note); app.forEach { append("\n${it.note}") } })) } ?: notes.filter { it.appendMode }.takeIf { it.isNotEmpty() }?.let { result.add(SetNote(it.joinToString("\n") { n -> n.note }, true)) } }
        actions.filterIsInstance<FlagForReview>().firstOrNull()?.let { result.add(it) }
        actions.filterIsInstance<ExcludeFromReports>().takeIf { it.isNotEmpty() }?.let { result.add(ExcludeFromReports(it.any { e -> e.excludeFromBudget }, it.any { e -> e.excludeFromStats })) }
        actions.filterIsInstance<Split>().firstOrNull()?.let { result.add(it) }
        result.addAll(actions.filterIsInstance<LinkTransaction>())
        return result
    }

    private fun detectConflicts(matchedRules: List<RuleMatchResult>) = matchedRules.filter { it.rule.setsCategory }.mapNotNull { it.rule.categoryId }.distinct().size > 1

    private fun extractMatchedTokens(rule: CategoryRule): List<String> {
        val tokens = mutableListOf<String>()
        fun extract(c: RuleCondition) { when (c) { is MerchantContains -> tokens.add(c.pattern); is MerchantEquals -> tokens.add(c.value); is DescriptionContains -> tokens.addAll(c.keywords); is DescriptionMatches -> tokens.add(c.pattern); is ConditionGroup -> c.conditions.forEach { extract(it) }; else -> {} } }
        extract(rule.conditions)
        return tokens.distinct()
    }
}

data class RuleEngineResult(val matchedRules: List<RuleMatchResult>, val primaryCategoryRule: CategoryRule?, val mergedActions: List<RuleAction>, val hasConflicts: Boolean) {
    val hasMatches get() = matchedRules.isNotEmpty()
    val matchCount get() = matchedRules.size
    val categoryId get() = primaryCategoryRule?.categoryId
    val confidence get() = primaryCategoryRule?.confidence ?: 0f
    val matchedRuleIds get() = matchedRules.map { it.rule.id }
    companion object { val EMPTY = RuleEngineResult(emptyList(), null, emptyList(), false) }
}
