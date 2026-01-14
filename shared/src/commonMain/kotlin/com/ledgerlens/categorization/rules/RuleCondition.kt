package com.ledgerlens.categorization.rules

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Sealed class representing conditions that can be evaluated against a transaction.
 * Conditions can be combined using AND/OR logic via ConditionGroup.
 */
@Serializable
sealed class RuleCondition {
    /**
     * Unique identifier for this condition type.
     */
    abstract val type: String

    /**
     * Human-readable description of this condition.
     */
    abstract fun describe(): String
}

/**
 * Matches if the normalized merchant name contains the specified pattern.
 * Case-insensitive matching.
 */
@Serializable
data class MerchantContains(
    val pattern: String,
    val caseSensitive: Boolean = false
) : RuleCondition() {
    override val type: String = "merchant_contains"

    override fun describe(): String = "Merchant contains \"$pattern\""
}

/**
 * Matches if the merchant name exactly equals the specified value.
 */
@Serializable
data class MerchantEquals(
    val value: String,
    val caseSensitive: Boolean = false
) : RuleCondition() {
    override val type: String = "merchant_equals"

    override fun describe(): String = "Merchant equals \"$value\""
}

/**
 * Matches if the raw description matches the specified regex pattern.
 */
@Serializable
data class DescriptionMatches(
    val pattern: String,
    val caseSensitive: Boolean = false
) : RuleCondition() {
    override val type: String = "description_matches"

    override fun describe(): String = "Description matches \"$pattern\""
}

/**
 * Matches if the description contains any of the specified keywords.
 */
@Serializable
data class DescriptionContains(
    val keywords: List<String>,
    val matchAll: Boolean = false,
    val caseSensitive: Boolean = false
) : RuleCondition() {
    override val type: String = "description_contains"

    override fun describe(): String = if (matchAll) {
        "Description contains all of: ${keywords.joinToString(", ") { "\"$it\"" }}"
    } else {
        "Description contains any of: ${keywords.joinToString(", ") { "\"$it\"" }}"
    }
}

/**
 * Matches if the transaction amount (in minor units/cents) falls within the specified range.
 * Both min and max are inclusive. Null means unbounded.
 */
@Serializable
data class AmountRange(
    val minCents: Long? = null,
    val maxCents: Long? = null,
    val absolute: Boolean = true
) : RuleCondition() {
    override val type: String = "amount_range"

    override fun describe(): String {
        val prefix = if (absolute) "Absolute amount" else "Amount"
        return when {
            minCents != null && maxCents != null -> "$prefix between ${formatCents(minCents)} and ${formatCents(maxCents)}"
            minCents != null -> "$prefix >= ${formatCents(minCents)}"
            maxCents != null -> "$prefix <= ${formatCents(maxCents)}"
            else -> "$prefix (any)"
        }
    }

    private fun formatCents(cents: Long): String {
        val dollars = cents / 100
        val remainder = cents % 100
        return "\$${dollars}.${remainder.toString().padStart(2, '0')}"
    }

    init {
        require(minCents == null || maxCents == null || minCents <= maxCents) {
            "minCents ($minCents) must be <= maxCents ($maxCents)"
        }
    }
}

/**
 * Matches if the transaction amount exactly equals the specified value.
 */
@Serializable
data class AmountEquals(
    val amountCents: Long,
    val absolute: Boolean = true
) : RuleCondition() {
    override val type: String = "amount_equals"

    override fun describe(): String {
        val prefix = if (absolute) "Absolute amount" else "Amount"
        val dollars = amountCents / 100
        val remainder = amountCents % 100
        return "$prefix equals \$${dollars}.${remainder.toString().padStart(2, '0')}"
    }
}

/**
 * Matches if the transaction date falls within the specified range.
 */
@Serializable
data class DateRange(
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null
) : RuleCondition() {
    override val type: String = "date_range"

    override fun describe(): String = when {
        startDate != null && endDate != null -> "Date between $startDate and $endDate"
        startDate != null -> "Date on or after $startDate"
        endDate != null -> "Date on or before $endDate"
        else -> "Date (any)"
    }

    init {
        require(startDate == null || endDate == null || startDate <= endDate) {
            "startDate ($startDate) must be <= endDate ($endDate)"
        }
    }
}

/**
 * Matches if the transaction occurs on the specified day(s) of the week.
 * Days are 1-7 where 1 = Monday, 7 = Sunday (ISO-8601).
 */
@Serializable
data class DayOfWeek(
    val days: Set<Int>
) : RuleCondition() {
    override val type: String = "day_of_week"

    override fun describe(): String {
        val dayNames = days.sorted().map { dayName(it) }
        return "Day of week is ${dayNames.joinToString(" or ")}"
    }

    private fun dayName(day: Int): String = when (day) {
        1 -> "Monday"
        2 -> "Tuesday"
        3 -> "Wednesday"
        4 -> "Thursday"
        5 -> "Friday"
        6 -> "Saturday"
        7 -> "Sunday"
        else -> "Day $day"
    }

    init {
        require(days.isNotEmpty()) { "days cannot be empty" }
        require(days.all { it in 1..7 }) { "days must be in range 1-7" }
    }
}

/**
 * Matches if the transaction is a debit (negative) or credit (positive).
 */
@Serializable
data class TransactionType(
    val isDebit: Boolean
) : RuleCondition() {
    override val type: String = "transaction_type"

    override fun describe(): String = if (isDebit) "Is a debit" else "Is a credit"
}

/**
 * Matches if the transaction is associated with the specified account.
 */
@Serializable
data class AccountEquals(
    val accountId: String
) : RuleCondition() {
    override val type: String = "account_equals"

    override fun describe(): String = "Account is \"$accountId\""
}

/**
 * Matches if the transaction day of month is in the specified set.
 * Useful for matching recurring transactions (e.g., rent on the 1st).
 */
@Serializable
data class DayOfMonth(
    val days: Set<Int>
) : RuleCondition() {
    override val type: String = "day_of_month"

    override fun describe(): String = "Day of month is ${days.sorted().joinToString(" or ")}"

    init {
        require(days.isNotEmpty()) { "days cannot be empty" }
        require(days.all { it in 1..31 }) { "days must be in range 1-31" }
    }
}

/**
 * Logical operator for combining conditions.
 */
@Serializable
enum class LogicalOperator {
    AND,
    OR
}

/**
 * A group of conditions combined with a logical operator.
 * Supports nested groups for complex logic.
 */
@Serializable
data class ConditionGroup(
    val conditions: List<RuleCondition>,
    val operator: LogicalOperator = LogicalOperator.AND
) : RuleCondition() {
    override val type: String = "condition_group"

    override fun describe(): String {
        val separator = if (operator == LogicalOperator.AND) " AND " else " OR "
        return "(${conditions.joinToString(separator) { it.describe() }})"
    }

    init {
        require(conditions.isNotEmpty()) { "conditions cannot be empty" }
    }
}
