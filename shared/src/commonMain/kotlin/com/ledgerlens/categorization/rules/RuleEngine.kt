package com.ledgerlens.categorization.rules

import com.ledgerlens.categorization.ClassificationExplanation
import com.ledgerlens.categorization.ClassificationResult
import com.ledgerlens.categorization.TransactionFeatures
import kotlinx.datetime.LocalDate

/**
 * Orchestrates rule evaluation with priority ordering and conflict resolution.
 *
 * Rules are evaluated in priority order (highest first). The first matching rule
 * that sets a category "wins" for categorization purposes, but all matching
 * rules can contribute additional actions (tags, notes, etc.).
 */
class RuleEngine(
    private val matcher: RuleMatcher = RuleMatcher()
) {
    /**
     * Evaluates all rules against transaction features and returns the combined result.
     *
     * @param rules List of rules to evaluate
     * @param features Transaction features to match against
     * @param transactionDate Optional date for date-based conditions
     * @param stopOnFirstCategory If true, stops evaluating after first category-setting rule matches
     * @return Combined evaluation result
     */
    fun evaluate(
        rules: List<CategoryRule>,
        features: TransactionFeatures,
        transactionDate: LocalDate? = null,
        stopOnFirstCategory: Boolean = true
    ): RuleEngineResult {
        val sortedRules = rules
            .filter { it.enabled }
            .sortedByDescending { it.priority }

        val matchedRules = mutableListOf<RuleMatchResult>()
        var primaryCategoryResult: RuleMatchResult? = null
        val allActions = mutableListOf<RuleAction>()
        val matchedRuleIds = mutableListOf<String>()

        for (rule in sortedRules) {
            val result = matcher.evaluate(rule, features, transactionDate)

            if (result.matched) {
                matchedRules.add(result)
                matchedRuleIds.add(rule.id)
                allActions.addAll(result.appliedActions)

                // Track the first rule that sets a category
                if (primaryCategoryResult == null && rule.setsCategory) {
                    primaryCategoryResult = result
                    if (stopOnFirstCategory) {
                        // Continue to collect other non-category-setting rules
                        // but mark that we have our primary category
                    }
                }
            }
        }

        // Deduplicate and merge actions
        val mergedActions = mergeActions(allActions)

        return RuleEngineResult(
            matchedRules = matchedRules,
            primaryCategoryRule = primaryCategoryResult?.rule,
            mergedActions = mergedActions,
            hasConflicts = detectConflicts(matchedRules)
        )
    }

    /**
     * Converts the engine result to a ClassificationResult for integration
     * with the existing categorization system.
     */
    fun toClassificationResult(result: RuleEngineResult): ClassificationResult {
        val categoryRule = result.primaryCategoryRule
            ?: return ClassificationResult.unknown()

        val setCategoryAction = categoryRule.actions
            .filterIsInstance<SetCategory>()
            .firstOrNull()
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

    /**
     * Finds all rules that would match the given features (for debugging/preview).
     */
    fun findMatchingRules(
        rules: List<CategoryRule>,
        features: TransactionFeatures,
        transactionDate: LocalDate? = null
    ): List<CategoryRule> {
        return rules
            .filter { it.enabled }
            .filter { matcher.evaluate(it, features, transactionDate).matched }
            .sortedByDescending { it.priority }
    }

    /**
     * Tests a single rule against features without side effects.
     */
    fun testRule(
        rule: CategoryRule,
        features: TransactionFeatures,
        transactionDate: LocalDate? = null
    ): RuleMatchResult {
        return matcher.evaluate(rule, features, transactionDate)
    }

    private fun mergeActions(actions: List<RuleAction>): List<RuleAction> {
        val result = mutableListOf<RuleAction>()
        
        // Keep only the first SetCategory
        val firstSetCategory = actions.filterIsInstance<SetCategory>().firstOrNull()
        if (firstSetCategory != null) {
            result.add(firstSetCategory)
        }

        // Keep only the first SetMerchant
        val firstSetMerchant = actions.filterIsInstance<SetMerchant>().firstOrNull()
        if (firstSetMerchant != null) {
            result.add(firstSetMerchant)
        }

        // Merge all AddTag actions
        val allAddTags = actions.filterIsInstance<AddTag>().flatMap { it.tags }.distinct()
        if (allAddTags.isNotEmpty()) {
            result.add(AddTag(allAddTags))
        }

        // Merge all RemoveTag actions
        val allRemoveTags = actions.filterIsInstance<RemoveTag>().flatMap { it.tags }.distinct()
        if (allRemoveTags.isNotEmpty()) {
            result.add(RemoveTag(allRemoveTags))
        }

        // Keep the first SetNote (or merge in append mode)
        val setNotes = actions.filterIsInstance<SetNote>()
        if (setNotes.isNotEmpty()) {
            val appendNotes = setNotes.filter { it.appendMode }
            val replaceNote = setNotes.firstOrNull { !it.appendMode }
            
            if (replaceNote != null) {
                val combinedNote = buildString {
                    append(replaceNote.note)
                    appendNotes.forEach { append("\n${it.note}") }
                }
                result.add(SetNote(combinedNote))
            } else if (appendNotes.isNotEmpty()) {
                result.add(SetNote(appendNotes.joinToString("\n") { it.note }, appendMode = true))
            }
        }

        // Keep the first FlagForReview
        val firstFlag = actions.filterIsInstance<FlagForReview>().firstOrNull()
        if (firstFlag != null) {
            result.add(firstFlag)
        }

        // Keep the first ExcludeFromReports (with most restrictive settings)
        val excludes = actions.filterIsInstance<ExcludeFromReports>()
        if (excludes.isNotEmpty()) {
            result.add(ExcludeFromReports(
                excludeFromBudget = excludes.any { it.excludeFromBudget },
                excludeFromStats = excludes.any { it.excludeFromStats }
            ))
        }

        // Keep the first Split (splits don't merge)
        val firstSplit = actions.filterIsInstance<Split>().firstOrNull()
        if (firstSplit != null) {
            result.add(firstSplit)
        }

        // Keep all LinkTransaction actions (they're additive)
        result.addAll(actions.filterIsInstance<LinkTransaction>())

        return result
    }

    private fun detectConflicts(matchedRules: List<RuleMatchResult>): Boolean {
        val categorySettingRules = matchedRules.filter { it.rule.setsCategory }
        
        if (categorySettingRules.size <= 1) return false

        // Check if they set different categories
        val categories = categorySettingRules
            .mapNotNull { it.rule.categoryId }
            .distinct()

        return categories.size > 1
    }

    private fun extractMatchedTokens(rule: CategoryRule): List<String> {
        val tokens = mutableListOf<String>()
        
        fun extractFromCondition(condition: RuleCondition) {
            when (condition) {
                is MerchantContains -> tokens.add(condition.pattern)
                is MerchantEquals -> tokens.add(condition.value)
                is DescriptionContains -> tokens.addAll(condition.keywords)
                is DescriptionMatches -> tokens.add(condition.pattern)
                is ConditionGroup -> condition.conditions.forEach { extractFromCondition(it) }
                else -> { /* No tokens to extract */ }
            }
        }

        extractFromCondition(rule.conditions)
        return tokens.distinct()
    }
}

/**
 * Result of evaluating rules against a transaction.
 */
data class RuleEngineResult(
    val matchedRules: List<RuleMatchResult>,
    val primaryCategoryRule: CategoryRule?,
    val mergedActions: List<RuleAction>,
    val hasConflicts: Boolean
) {
    /**
     * Returns true if any rule matched.
     */
    val hasMatches: Boolean get() = matchedRules.isNotEmpty()

    /**
     * Returns the number of rules that matched.
     */
    val matchCount: Int get() = matchedRules.size

    /**
     * Returns the category ID from the primary rule, if any.
     */
    val categoryId: String? get() = primaryCategoryRule?.categoryId

    /**
     * Returns the confidence from the primary rule, if any.
     */
    val confidence: Float get() = primaryCategoryRule?.confidence ?: 0f

    /**
     * Returns all matched rule IDs.
     */
    val matchedRuleIds: List<String> get() = matchedRules.map { it.rule.id }

    companion object {
        val EMPTY = RuleEngineResult(
            matchedRules = emptyList(),
            primaryCategoryRule = null,
            mergedActions = emptyList(),
            hasConflicts = false
        )
    }
}
