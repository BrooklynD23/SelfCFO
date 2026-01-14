package com.ledgerlens.categorization.rules

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class CategoryRule(
    val id: String,
    val name: String,
    val description: String? = null,
    val conditions: ConditionGroup,
    val actions: List<RuleAction>,
    val priority: Int = DEFAULT_PRIORITY,
    val enabled: Boolean = true,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = createdAt,
    val source: RuleSource = RuleSource.USER,
    val matchCount: Long = 0,
    val lastMatchedAt: Instant? = null
) {
    fun describe(): String = buildString {
        append("Rule: $name\n")
        append("When: ${conditions.describe()}\n")
        append("Then: ${actions.joinToString(", ") { it.describe() }}")
    }

    val setsCategory: Boolean get() = actions.any { it is SetCategory }
    val categoryId: String? get() = actions.filterIsInstance<SetCategory>().firstOrNull()?.categoryId
    val confidence: Float get() = actions.filterIsInstance<SetCategory>().firstOrNull()?.confidence ?: 0f

    fun withMatch(matchTime: Instant = Clock.System.now()): CategoryRule = copy(
        matchCount = matchCount + 1, lastMatchedAt = matchTime
    )
    fun withUpdate(): CategoryRule = copy(updatedAt = Clock.System.now())

    companion object {
        const val DEFAULT_PRIORITY = 100
        const val MAX_PRIORITY = 1000
        const val MIN_PRIORITY = 0
    }
}

@Serializable
enum class RuleSource { USER, SYSTEM, SUGGESTED, IMPORTED }

sealed class RuleValidationResult {
    data object Valid : RuleValidationResult()
    data class Invalid(val errors: List<String>) : RuleValidationResult()
}

fun CategoryRule.validate(): RuleValidationResult {
    val errors = mutableListOf<String>()
    if (id.isBlank()) errors.add("Rule ID cannot be blank")
    if (name.isBlank()) errors.add("Rule name cannot be blank")
    if (name.length > 200) errors.add("Rule name cannot exceed 200 characters")
    if (conditions.conditions.isEmpty()) errors.add("Rule must have at least one condition")
    if (actions.isEmpty()) errors.add("Rule must have at least one action")
    if (priority < CategoryRule.MIN_PRIORITY || priority > CategoryRule.MAX_PRIORITY) {
        errors.add("Priority must be between ${CategoryRule.MIN_PRIORITY} and ${CategoryRule.MAX_PRIORITY}")
    }
    actions.filterIsInstance<Split>().forEach { split ->
        val percentageSum = split.splits.mapNotNull { it.percentage }.sum()
        if (percentageSum > 1.001f) errors.add("Split percentages cannot exceed 100%")
    }
    return if (errors.isEmpty()) RuleValidationResult.Valid else RuleValidationResult.Invalid(errors)
}

@Serializable
data class RuleMatchResult(
    val rule: CategoryRule,
    val matched: Boolean,
    val appliedActions: List<RuleAction> = emptyList(),
    val explanation: String? = null
) {
    companion object {
        fun noMatch(rule: CategoryRule) = RuleMatchResult(rule = rule, matched = false)
        fun match(rule: CategoryRule, explanation: String? = null) = RuleMatchResult(
            rule = rule, matched = true, appliedActions = rule.actions, explanation = explanation
        )
    }
}
