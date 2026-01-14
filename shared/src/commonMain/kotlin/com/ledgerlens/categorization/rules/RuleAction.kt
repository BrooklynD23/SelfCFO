package com.ledgerlens.categorization.rules

import kotlinx.serialization.Serializable

@Serializable
sealed class RuleAction {
    abstract val type: String
    abstract fun describe(): String
}

@Serializable
data class SetCategory(val categoryId: String, val confidence: Float = 0.9f) : RuleAction() {
    override val type: String = "set_category"
    override fun describe(): String = "Set category to \"$categoryId\""
    init { require(confidence in 0f..1f) { "confidence must be between 0 and 1" } }
}

@Serializable
data class AddTag(val tags: List<String>) : RuleAction() {
    override val type: String = "add_tag"
    override fun describe(): String = "Add tags: ${tags.joinToString(", ") { "\"$it\"" }}"
    init { require(tags.isNotEmpty()) { "tags cannot be empty" } }
}

@Serializable
data class RemoveTag(val tags: List<String>) : RuleAction() {
    override val type: String = "remove_tag"
    override fun describe(): String = "Remove tags: ${tags.joinToString(", ") { "\"$it\"" }}"
    init { require(tags.isNotEmpty()) { "tags cannot be empty" } }
}

@Serializable
data class SetMerchant(val merchantName: String) : RuleAction() {
    override val type: String = "set_merchant"
    override fun describe(): String = "Set merchant to \"$merchantName\""
    init { require(merchantName.isNotBlank()) { "merchantName cannot be blank" } }
}

@Serializable
data class SetNote(val note: String, val appendMode: Boolean = false) : RuleAction() {
    override val type: String = "set_note"
    override fun describe(): String = if (appendMode) "Append note: \"$note\"" else "Set note to: \"$note\""
}

@Serializable
data class Split(val splits: List<SplitPart>) : RuleAction() {
    override val type: String = "split"
    override fun describe(): String = "Split into ${splits.size} parts"
    init { require(splits.size >= 2) { "splits must have at least 2 parts" } }
}

@Serializable
data class SplitPart(
    val categoryId: String,
    val percentage: Float? = null,
    val fixedAmountCents: Long? = null,
    val description: String? = null
) {
    init {
        require(percentage != null || fixedAmountCents != null) { "Either percentage or fixedAmountCents must be specified" }
        require(percentage == null || percentage in 0f..1f) { "percentage must be between 0 and 1" }
    }
}

@Serializable
data class FlagForReview(val reason: String? = null) : RuleAction() {
    override val type: String = "flag_for_review"
    override fun describe(): String = reason?.let { "Flag for review: $it" } ?: "Flag for review"
}

@Serializable
data class ExcludeFromReports(val excludeFromBudget: Boolean = true, val excludeFromStats: Boolean = true) : RuleAction() {
    override val type: String = "exclude_from_reports"
    override fun describe(): String = buildString {
        append("Exclude from")
        val exclusions = mutableListOf<String>()
        if (excludeFromBudget) exclusions.add("budget")
        if (excludeFromStats) exclusions.add("statistics")
        append(" ${exclusions.joinToString(" and ")}")
    }
}

@Serializable
data class LinkTransaction(val linkType: String, val criteria: Map<String, String> = emptyMap()) : RuleAction() {
    override val type: String = "link_transaction"
    override fun describe(): String = "Link as $linkType"
}
