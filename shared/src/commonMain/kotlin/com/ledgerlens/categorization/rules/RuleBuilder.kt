package com.ledgerlens.categorization.rules

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

class RuleBuilder(private val id: String) {
    private var name: String = id
    private var description: String? = null
    private val conditions = mutableListOf<RuleCondition>()
    private var conditionOperator = LogicalOperator.AND
    private val actions = mutableListOf<RuleAction>()
    private var priority = CategoryRule.DEFAULT_PRIORITY
    private var enabled = true
    private var source = RuleSource.USER

    fun name(name: String) = apply { this.name = name }
    fun description(desc: String) = apply { this.description = desc }
    fun withAndLogic() = apply { conditionOperator = LogicalOperator.AND }
    fun withOrLogic() = apply { conditionOperator = LogicalOperator.OR }

    fun whenMerchantContains(pattern: String, caseSensitive: Boolean = false) = apply { conditions.add(MerchantContains(pattern, caseSensitive)) }
    fun whenMerchantEquals(value: String, caseSensitive: Boolean = false) = apply { conditions.add(MerchantEquals(value, caseSensitive)) }
    fun andMerchantContains(pattern: String, caseSensitive: Boolean = false) = whenMerchantContains(pattern, caseSensitive)

    fun whenDescriptionContains(vararg keywords: String, matchAll: Boolean = false) = apply { conditions.add(DescriptionContains(keywords.toList(), matchAll)) }
    fun whenDescriptionMatches(pattern: String, caseSensitive: Boolean = false) = apply { conditions.add(DescriptionMatches(pattern, caseSensitive)) }
    fun andDescriptionContains(vararg keywords: String, matchAll: Boolean = false) = whenDescriptionContains(*keywords, matchAll = matchAll)

    fun whenAmountBetween(minCents: Long, maxCents: Long, absolute: Boolean = true) = apply { conditions.add(AmountRange(minCents, maxCents, absolute)) }
    fun whenAmountGreaterThan(minCents: Long, absolute: Boolean = true) = apply { conditions.add(AmountRange(minCents = minCents, absolute = absolute)) }
    fun whenAmountLessThan(maxCents: Long, absolute: Boolean = true) = apply { conditions.add(AmountRange(maxCents = maxCents, absolute = absolute)) }
    fun whenAmountEquals(amountCents: Long, absolute: Boolean = true) = apply { conditions.add(AmountEquals(amountCents, absolute)) }
    fun andAmountBetween(minCents: Long, maxCents: Long, absolute: Boolean = true) = whenAmountBetween(minCents, maxCents, absolute)
    fun andAmountGreaterThan(minCents: Long, absolute: Boolean = true) = whenAmountGreaterThan(minCents, absolute)
    fun andAmountLessThan(maxCents: Long, absolute: Boolean = true) = whenAmountLessThan(maxCents, absolute)

    fun whenDateBetween(start: LocalDate, end: LocalDate) = apply { conditions.add(DateRange(start, end)) }
    fun whenDateAfter(start: LocalDate) = apply { conditions.add(DateRange(startDate = start)) }
    fun whenDateBefore(end: LocalDate) = apply { conditions.add(DateRange(endDate = end)) }

    fun whenDayOfWeek(vararg days: Int) = apply { conditions.add(DayOfWeek(days.toSet())) }
    fun whenWeekday() = whenDayOfWeek(1, 2, 3, 4, 5)
    fun whenWeekend() = whenDayOfWeek(6, 7)
    fun whenDayOfMonth(vararg days: Int) = apply { conditions.add(DayOfMonth(days.toSet())) }

    fun whenIsDebit() = apply { conditions.add(TransactionType(true)) }
    fun whenIsCredit() = apply { conditions.add(TransactionType(false)) }
    fun whenAccountEquals(accountId: String) = apply { conditions.add(AccountEquals(accountId)) }

    fun whenAnyOf(vararg conds: RuleCondition) = apply { conditions.add(ConditionGroup(conds.toList(), LogicalOperator.OR)) }
    fun whenAllOf(vararg conds: RuleCondition) = apply { conditions.add(ConditionGroup(conds.toList(), LogicalOperator.AND)) }

    fun thenSetCategory(categoryId: String, confidence: Float = 0.9f) = apply { actions.add(SetCategory(categoryId, confidence)) }
    fun withTag(vararg tags: String) = apply { actions.add(AddTag(tags.toList())) }
    fun removeTag(vararg tags: String) = apply { actions.add(RemoveTag(tags.toList())) }
    fun setMerchant(merchantName: String) = apply { actions.add(SetMerchant(merchantName)) }
    fun setNote(note: String) = apply { actions.add(SetNote(note)) }
    fun appendNote(note: String) = apply { actions.add(SetNote(note, true)) }
    fun flagForReview(reason: String? = null) = apply { actions.add(FlagForReview(reason)) }
    fun excludeFromReports(fromBudget: Boolean = true, fromStats: Boolean = true) = apply { actions.add(ExcludeFromReports(fromBudget, fromStats)) }
    fun excludeFromBudget() = excludeFromReports(true, false)
    fun splitTransaction(vararg splits: SplitPart) = apply { actions.add(Split(splits.toList())) }
    fun linkAs(linkType: String, criteria: Map<String, String> = emptyMap()) = apply { actions.add(LinkTransaction(linkType, criteria)) }

    fun priority(p: Int) = apply { priority = p.coerceIn(CategoryRule.MIN_PRIORITY, CategoryRule.MAX_PRIORITY) }
    fun enabled(e: Boolean) = apply { enabled = e }
    fun disabled() = enabled(false)
    fun source(s: RuleSource) = apply { source = s }
    fun systemRule() = source(RuleSource.SYSTEM)
    fun suggestedRule() = source(RuleSource.SUGGESTED)

    fun build(): CategoryRule {
        require(conditions.isNotEmpty()) { "Rule must have at least one condition" }
        require(actions.isNotEmpty()) { "Rule must have at least one action" }
        return CategoryRule(id, name, description, ConditionGroup(conditions.toList(), conditionOperator), actions.toList(), priority, enabled, Clock.System.now(), Clock.System.now(), source)
    }

    fun buildValidated(): CategoryRule {
        val rule = build()
        val validation = rule.validate()
        if (validation is RuleValidationResult.Invalid) throw IllegalStateException("Rule validation failed: ${validation.errors.joinToString("; ")}")
        return rule
    }

    companion object {
        fun create(name: String) = RuleBuilder("rule-${name.lowercase().replace(Regex("[^a-z0-9]+"), "-")}-${Clock.System.now().toEpochMilliseconds()}").name(name)
        fun merchantRule(id: String, merchantPattern: String, categoryId: String, confidence: Float = 0.9f, priority: Int = 100) = RuleBuilder(id).name("$merchantPattern → $categoryId").whenMerchantContains(merchantPattern).thenSetCategory(categoryId, confidence).priority(priority).build()
        fun keywordRule(id: String, keywords: List<String>, categoryId: String, confidence: Float = 0.8f, priority: Int = 50) = RuleBuilder(id).name("Keywords [${keywords.take(3).joinToString(", ")}] → $categoryId").whenDescriptionContains(*keywords.toTypedArray()).thenSetCategory(categoryId, confidence).priority(priority).build()
    }
}
