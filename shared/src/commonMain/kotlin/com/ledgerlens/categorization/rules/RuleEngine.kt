package com.ledgerlens.categorization.rules

import com.ledgerlens.categorization.ClassificationExplanation
import com.ledgerlens.categorization.ClassificationResult
import com.ledgerlens.categorization.TransactionFeatures
import kotlinx.datetime.LocalDate

class RuleEngine(private val matcher: RuleMatcher = RuleMatcher()) {

    fun evaluate(
        rules: List<CategoryRule>,
        features: TransactionFeatures,
        transactionDate: LocalDate? = null,
        stopOnFirstCategory: Boolean = true
    ): RuleEngineResult {
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

        return RuleEngineResult(
            matchedRules = matchedRules,
            primaryCategoryRule = primaryCategoryResult?.rule,
            mergedActions = mergeActions(allActions),
            hasConflicts = detectConflicts(matchedRules)
        )
    }

    fun toClassificationResult(result: RuleEngineResult): ClassificationResult {
        val categoryRule = result.primaryCategoryRule ?: return ClassificationResult.unknown()
        val setCategoryAction = categoryRule.actions.filterIsInstance<SetCategory>().firstOrNull()
            ?: return ClassificationResult.unknown()

        return ClassificationResult(
            categoryId = setCategoryAction.categoryId,
            confidence = setCategoryAction.confidence,
            alternatives = emptyList(),
            explanation = ClassificationExplanation(
                classifierUsed = "rules-engine",
                reason = "Matched rule: ${categoryRule.name}",
                ruleMatched = categoryRule.id,
                tokenMatches = extractMatchedTokens(categoryRule)
            )
        )
    }

    fun findMatchingRules(rules: List<CategoryRule>, features: TransactionFeatures, transactionDate: LocalDate? = null): List<CategoryRule> {
        return rules.filter { it.enabled }.filter { matcher.evaluate(it, features, transactionDate).matched }.sortedByDescending { it.priority }
    }

    fun testRule(rule: CategoryRule, features: TransactionFeatures, transactionDate: LocalDate? = null): RuleMatchResult =
        matcher.evaluate(rule, features, transactionDate)

    private fun mergeActions(actions: List<RuleAction>): List<RuleAction> {
        val result = mutableListOf<RuleAction>()
        actions.filterIsInstance<SetCategory>().firstOrNull()?.let { result.add(it) }
        actions.filterIsInstance<SetMerchant>().firstOrNull()?.let { result.add(it) }
        val allAddTags = actions.filterIsInstance<AddTag>().flatMap { it.tags }.distinct()
        if (allAddTags.isNotEmpty()) result.add(AddTag(allAddTags))
        val allRemoveTags = actions.filterIsInstance<RemoveTag>().flatMap { it.tags }.distinct()
        if (allRemoveTags.isNotEmpty()) result.add(RemoveTag(allRemoveTags))
        val setNotes = actions.filterIsInstance<SetNote>()
        if (setNotes.isNotEmpty()) {
            val appendNotes = setNotes.filter { it.appendMode }
            val replaceNote = setNotes.firstOrNull { !it.appendMode }
            if (replaceNote != null) {
                val combinedNote = buildString { append(replaceNote.note); appendNotes.forEach { append("\n${it.note}") } }
                result.add(SetNote(combinedNote))
            } else if (appendNotes.isNotEmpty()) result.add(SetNote(appendNotes.joinToString("\n") { it.note }, appendMode = true))
        }
        actions.filterIsInstance<FlagForReview>().firstOrNull()?.let { result.add(it) }
        val excludes = actions.filterIsInstance<ExcludeFromReports>()
        if (excludes.isNotEmpty()) result.add(ExcludeFromReports(excludes.any { it.excludeFromBudget }, excludes.any { it.excludeFromStats }))
        actions.filterIsInstance<Split>().firstOrNull()?.let { result.add(it) }
        result.addAll(actions.filterIsInstance<LinkTransaction>())
        return result
    }

    private fun detectConflicts(matchedRules: List<RuleMatchResult>): Boolean {
        val categorySettingRules = matchedRules.filter { it.rule.setsCategory }
        if (categorySettingRules.size <= 1) return false
        return categorySettingRules.mapNotNull { it.rule.categoryId }.distinct().size > 1
    }

    private fun extractMatchedTokens(rule: CategoryRule): List<String> {
        val tokens = mutableListOf<String>()
        fun extract(condition: RuleCondition) {
            when (condition) {
                is MerchantContains -> tokens.add(condition.pattern)
                is MerchantEquals -> tokens.add(condition.value)
                is DescriptionContains -> tokens.addAll(condition.keywords)
                is DescriptionMatches -> tokens.add(condition.pattern)
                is ConditionGroup -> condition.conditions.forEach { extract(it) }
                else -> {}
            }
        }
        extract(rule.conditions)
        return tokens.distinct()
    }
}

data class RuleEngineResult(
    val matchedRules: List<RuleMatchResult>,
    val primaryCategoryRule: CategoryRule?,
    val mergedActions: List<RuleAction>,
    val hasConflicts: Boolean
) {
    val hasMatches: Boolean get() = matchedRules.isNotEmpty()
    val matchCount: Int get() = matchedRules.size
    val categoryId: String? get() = primaryCategoryRule?.categoryId
    val confidence: Float get() = primaryCategoryRule?.confidence ?: 0f
    val matchedRuleIds: List<String> get() = matchedRules.map { it.rule.id }
    companion object { val EMPTY = RuleEngineResult(emptyList(), null, emptyList(), false) }
}
