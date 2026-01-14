package com.ledgerlens.categorization.rules

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * A user-defined categorization rule.
 *
 * Rules are evaluated in priority order (higher priority first).
 * When conditions match, all associated actions are applied.
 *
 * @property id Unique identifier for this rule
 * @property name Human-readable name for the rule
 * @property description Optional detailed description
 * @property conditions The root condition group (supports nested AND/OR logic)
 * @property actions Actions to perform when conditions match
 * @property priority Higher priority rules are evaluated first (default: 100)
 * @property enabled Whether this rule is active
 * @property createdAt When the rule was created
 * @property updatedAt When the rule was last modified
 * @property source How the rule was created (user, system, suggested)
 * @property matchCount Number of times this rule has matched
 * @property lastMatchedAt When this rule last matched a transaction
 */
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
    /**
     * Returns a human-readable description of what this rule does.
     */
    fun describe(): String = buildString {
        append("Rule: $name\n")
        append("When: ${conditions.describe()}\n")
        append("Then: ${actions.joinToString(", ") { it.describe() }}")
    }

    /**
     * Returns true if this rule has a SetCategory action.
     */
    val setsCategory: Boolean
        get() = actions.any { it is SetCategory }

    /**
     * Returns the category ID if this rule has a SetCategory action.
     */
    val categoryId: String?
        get() = actions.filterIsInstance<SetCategory>().firstOrNull()?.categoryId

    /**
     * Returns the confidence if this rule has a SetCategory action.
     */
    val confidence: Float
        get() = actions.filterIsInstance<SetCategory>().firstOrNull()?.confidence ?: 0f

    /**
     * Creates an updated copy with incremented match count.
     */
    fun withMatch(matchTime: Instant = Clock.System.now()): CategoryRule = copy(
        matchCount = matchCount + 1,
        lastMatchedAt = matchTime
    )

    /**
     * Creates an updated copy with new updated timestamp.
     */
    fun withUpdate(): CategoryRule = copy(updatedAt = Clock.System.now())

    companion object {
        const val DEFAULT_PRIORITY = 100
        const val MAX_PRIORITY = 1000
        const val MIN_PRIORITY = 0
    }
}

/**
 * How the rule was created.
 */
@Serializable
enum class RuleSource {
    /** Created manually by the user */
    USER,
    /** Built-in system rule */
    SYSTEM,
    /** Auto-generated from user correction patterns */
    SUGGESTED,
    /** Imported from another source */
    IMPORTED
}

/**
 * Validation result for rule operations.
 */
sealed class RuleValidationResult {
    data object Valid : RuleValidationResult()
    data class Invalid(val errors: List<String>) : RuleValidationResult()
}

/**
 * Validates a CategoryRule for correctness.
 */
fun CategoryRule.validate(): RuleValidationResult {
    val errors = mutableListOf<String>()

    if (id.isBlank()) {
        errors.add("Rule ID cannot be blank")
    }

    if (name.isBlank()) {
        errors.add("Rule name cannot be blank")
    }

    if (name.length > 200) {
        errors.add("Rule name cannot exceed 200 characters")
    }

    if (conditions.conditions.isEmpty()) {
        errors.add("Rule must have at least one condition")
    }

    if (actions.isEmpty()) {
        errors.add("Rule must have at least one action")
    }

    if (priority < CategoryRule.MIN_PRIORITY || priority > CategoryRule.MAX_PRIORITY) {
        errors.add("Priority must be between ${CategoryRule.MIN_PRIORITY} and ${CategoryRule.MAX_PRIORITY}")
    }

    // Validate split actions
    actions.filterIsInstance<Split>().forEach { split ->
        val percentageSum = split.splits.mapNotNull { it.percentage }.sum()
        if (percentageSum > 1.001f) { // Small tolerance for floating point
            errors.add("Split percentages cannot exceed 100%")
        }
    }

    return if (errors.isEmpty()) {
        RuleValidationResult.Valid
    } else {
        RuleValidationResult.Invalid(errors)
    }
}

/**
 * Result of applying a rule to a transaction.
 */
@Serializable
data class RuleMatchResult(
    val rule: CategoryRule,
    val matched: Boolean,
    val appliedActions: List<RuleAction> = emptyList(),
    val explanation: String? = null
) {
    companion object {
        fun noMatch(rule: CategoryRule): RuleMatchResult = RuleMatchResult(
            rule = rule,
            matched = false
        )

        fun match(rule: CategoryRule, explanation: String? = null): RuleMatchResult = RuleMatchResult(
            rule = rule,
            matched = true,
            appliedActions = rule.actions,
            explanation = explanation
        )
    }
}
