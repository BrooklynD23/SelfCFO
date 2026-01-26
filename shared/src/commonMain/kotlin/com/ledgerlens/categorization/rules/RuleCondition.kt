package com.ledgerlens.categorization.rules

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
sealed class RuleCondition {
    abstract val type: String
    abstract fun describe(): String
}

@Serializable
data class MerchantContains(val pattern: String, val caseSensitive: Boolean = false) : RuleCondition() {
    override val type: String = "merchant_contains"
    override fun describe(): String = "Merchant contains \"$pattern\""
}

@Serializable
data class MerchantEquals(val value: String, val caseSensitive: Boolean = false) : RuleCondition() {
    override val type: String = "merchant_equals"
    override fun describe(): String = "Merchant equals \"$value\""
}

@Serializable
data class DescriptionMatches(val pattern: String, val caseSensitive: Boolean = false) : RuleCondition() {
    override val type: String = "description_matches"
    override fun describe(): String = "Description matches \"$pattern\""
}

@Serializable
data class DescriptionContains(
    val keywords: List<String>,
    val matchAll: Boolean = false,
    val caseSensitive: Boolean = false
) : RuleCondition() {
    override val type: String = "description_contains"
    override fun describe(): String =
        if (matchAll) "Description contains all of: ${keywords.joinToString(", ") { "\"$it\"" }}" else "Description contains any of: ${keywords.joinToString(", ") { "\"$it\"" }}"
}

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
        val d = cents / 100
        val r = cents % 100
        return "\$$d.${r.toString().padStart(2, '0')}"
    }
    init {
        require(minCents == null || maxCents == null || minCents <= maxCents) { "minCents must be <= maxCents" }
    }
}

@Serializable
data class AmountEquals(val amountCents: Long, val absolute: Boolean = true) : RuleCondition() {
    override val type: String = "amount_equals"
    override fun describe(): String {
        val d = amountCents / 100
        val r = amountCents % 100
        return "${if (absolute) "Absolute amount" else "Amount"} equals \$$d.${r.toString().padStart(2, '0')}"
    }
}

@Serializable
data class DateRange(val startDate: LocalDate? = null, val endDate: LocalDate? = null) : RuleCondition() {
    override val type: String = "date_range"
    override fun describe(): String = when {
        startDate != null && endDate != null -> "Date between $startDate and $endDate"
        startDate != null -> "Date on or after $startDate"
        endDate != null -> "Date on or before $endDate"
        else -> "Date (any)"
    }
    init {
        require(startDate == null || endDate == null || startDate <= endDate) { "startDate must be <= endDate" }
    }
}

@Serializable
data class DayOfWeek(val days: Set<Int>) : RuleCondition() {
    override val type: String = "day_of_week"
    override fun describe(): String = "Day of week is ${days.sorted().map {
        when (it) {
            1 -> "Mon"
            2 -> "Tue"
            3 -> "Wed"
            4 -> "Thu"
            5 -> "Fri"
            6 -> "Sat"
            7 -> "Sun"
            else -> "Day $it"
        }
    }.joinToString(" or ")}"
    init {
        require(days.isNotEmpty() && days.all { it in 1..7 }) { "days must be non-empty and in range 1-7" }
    }
}

@Serializable
data class TransactionType(val isDebit: Boolean) : RuleCondition() {
    override val type: String = "transaction_type"
    override fun describe(): String = if (isDebit) "Is a debit" else "Is a credit"
}

@Serializable
data class AccountEquals(val accountId: String) : RuleCondition() {
    override val type: String = "account_equals"
    override fun describe(): String = "Account is \"$accountId\""
}

@Serializable
data class DayOfMonth(val days: Set<Int>) : RuleCondition() {
    override val type: String = "day_of_month"
    override fun describe(): String = "Day of month is ${days.sorted().joinToString(" or ")}"
    init {
        require(days.isNotEmpty() && days.all { it in 1..31 }) { "days must be non-empty and in range 1-31" }
    }
}

@Serializable
enum class LogicalOperator { AND, OR }

@Serializable
data class ConditionGroup(
    val conditions: List<RuleCondition>,
    val operator: LogicalOperator = LogicalOperator.AND
) : RuleCondition() {
    override val type: String = "condition_group"
    override fun describe(): String =
        "(${conditions.joinToString(if (operator == LogicalOperator.AND) " AND " else " OR ") { it.describe() }})"
    init {
        require(conditions.isNotEmpty()) { "conditions cannot be empty" }
    }
}
