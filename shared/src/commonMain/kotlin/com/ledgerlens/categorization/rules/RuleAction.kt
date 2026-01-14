package com.ledgerlens.categorization.rules

import kotlinx.serialization.Serializable

/**
 * Sealed class representing actions that can be performed when a rule matches.
 * Multiple actions can be applied from a single rule.
 */
@Serializable
sealed class RuleAction {
    /**
     * Unique identifier for this action type.
     */
    abstract val type: String

    /**
     * Human-readable description of this action.
     */
    abstract fun describe(): String
}

/**
 * Sets the category of the matched transaction.
 */
@Serializable
data class SetCategory(
    val categoryId: String,
    val confidence: Float = 0.9f
) : RuleAction() {
    override val type: String = "set_category"

    override fun describe(): String = "Set category to \"$categoryId\""

    init {
        require(confidence in 0f..1f) { "confidence must be between 0 and 1" }
    }
}

/**
 * Adds one or more tags to the matched transaction.
 */
@Serializable
data class AddTag(
    val tags: List<String>
) : RuleAction() {
    override val type: String = "add_tag"

    override fun describe(): String = "Add tags: ${tags.joinToString(", ") { "\"$it\"" }}"

    init {
        require(tags.isNotEmpty()) { "tags cannot be empty" }
    }
}

/**
 * Removes one or more tags from the matched transaction.
 */
@Serializable
data class RemoveTag(
    val tags: List<String>
) : RuleAction() {
    override val type: String = "remove_tag"

    override fun describe(): String = "Remove tags: ${tags.joinToString(", ") { "\"$it\"" }}"

    init {
        require(tags.isNotEmpty()) { "tags cannot be empty" }
    }
}

/**
 * Overrides the normalized merchant name.
 */
@Serializable
data class SetMerchant(
    val merchantName: String
) : RuleAction() {
    override val type: String = "set_merchant"

    override fun describe(): String = "Set merchant to \"$merchantName\""

    init {
        require(merchantName.isNotBlank()) { "merchantName cannot be blank" }
    }
}

/**
 * Adds a note or memo to the transaction.
 */
@Serializable
data class SetNote(
    val note: String,
    val appendMode: Boolean = false
) : RuleAction() {
    override val type: String = "set_note"

    override fun describe(): String = if (appendMode) {
        "Append note: \"$note\""
    } else {
        "Set note to: \"$note\""
    }
}

/**
 * Splits the transaction into multiple parts.
 * Each split has a category, amount, and optional description.
 */
@Serializable
data class Split(
    val splits: List<SplitPart>
) : RuleAction() {
    override val type: String = "split"

    override fun describe(): String = "Split into ${splits.size} parts"

    init {
        require(splits.size >= 2) { "splits must have at least 2 parts" }
    }
}

/**
 * A single part of a split transaction.
 */
@Serializable
data class SplitPart(
    val categoryId: String,
    val percentage: Float? = null,
    val fixedAmountCents: Long? = null,
    val description: String? = null
) {
    init {
        require(percentage != null || fixedAmountCents != null) {
            "Either percentage or fixedAmountCents must be specified"
        }
        require(percentage == null || percentage in 0f..1f) {
            "percentage must be between 0 and 1"
        }
    }

    fun describe(): String = when {
        percentage != null -> "${(percentage * 100).toInt()}% to \"$categoryId\""
        fixedAmountCents != null -> {
            val dollars = fixedAmountCents / 100
            val cents = fixedAmountCents % 100
            "\$${dollars}.${cents.toString().padStart(2, '0')} to \"$categoryId\""
        }
        else -> "to \"$categoryId\""
    }
}

/**
 * Marks the transaction for review, optionally with a reason.
 */
@Serializable
data class FlagForReview(
    val reason: String? = null
) : RuleAction() {
    override val type: String = "flag_for_review"

    override fun describe(): String = reason?.let { "Flag for review: $it" } ?: "Flag for review"
}

/**
 * Excludes the transaction from reports and budgets.
 * Useful for internal transfers, reimbursements, etc.
 */
@Serializable
data class ExcludeFromReports(
    val excludeFromBudget: Boolean = true,
    val excludeFromStats: Boolean = true
) : RuleAction() {
    override val type: String = "exclude_from_reports"

    override fun describe(): String = buildString {
        append("Exclude from")
        val exclusions = mutableListOf<String>()
        if (excludeFromBudget) exclusions.add("budget")
        if (excludeFromStats) exclusions.add("statistics")
        append(" ${exclusions.joinToString(" and ")}")
    }
}

/**
 * Links the transaction to another transaction (e.g., transfer matching).
 */
@Serializable
data class LinkTransaction(
    val linkType: String,
    val criteria: Map<String, String> = emptyMap()
) : RuleAction() {
    override val type: String = "link_transaction"

    override fun describe(): String = "Link as $linkType"
}
