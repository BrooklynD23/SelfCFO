package com.ledgerlens.categorization.rules

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

class RuleBuilder(private val id: String) {
    private var name: String = id
    private var description: String? = null
    private var conditions = mutableListOf<RuleCondition>()
    private var conditionOperator: LogicalOperator = LogicalOperator.AND
    private var actions = mutableListOf<RuleAction>()
    private var priority: Int = CategoryRule.DEFAULT_PRIORITY
    private var enabled: Boolean = true
    private var source: RuleSource = RuleSource.USER

    fun name(name: String) = apply { this.name = name }
    fun description(description: String) = apply { this.description = description }
    fun combineWith(operator: LogicalOperator) = apply { this.conditionOperator = operator }
    fun withAndLogic() = combineWith(LogicalOperator.AND)
    fun withOrLogic() = combineWith(LogicalOperator.OR)

    fun whenMerchantContains(pattern: String, caseSensitive: Boolean = false) = apply { conditions.add(MerchantContains(pattern, caseSensitive)) }
    fun whenMerchantEquals(value: String, caseSensitive: Boolean = false) = apply { conditions.add(MerchantEquals(value, caseSensitive)) }
    fun andMerchantContains(pattern: String, caseSensitive: Boolean = false) = whenMerchantContains(pattern, caseSensitive)
    fun orMerchantContains(pattern: String, caseSensitive: Boolean = false) = apply { conditionOperator = LogicalOperator.OR; whenMerchantContains(pattern, caseSensitive) }

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

    fun whenDateBetween(startDate: LocalDate, endDate: LocalDate) = apply { conditions.add(DateRange(startDate, endDate)) }
    fun whenDateAfter(startDate: LocalDate) = apply { conditions.add(DateRange(startDate = startDate)) }
    fun whenDateBefore(endDate: LocalDate) = apply { conditions.add(DateRange(endDate = endDate)) }

    fun whenDayOfWeek(vararg days: Int) = apply { conditions.add(DayOfWeek(days.toSet())) }
    fun whenWeekday() = whenDayOfWeek(1, 2, 3, 4, 5)
    fun whenWeekend() = whenDayOfWeek(6, 7)
    fun whenDayOfMonth(vararg days: Int) = apply { conditions.add(DayOfMonth(days.toSet())) }

    fun whenIsDebit() = apply { conditions.add(TransactionType(isDebit = true)) }
    fun whenIsCredit() = apply { conditions.add(TransactionType(isDebit = false)) }
    fun whenAccountEquals(accountId: String) = apply { conditions.add(AccountEquals(accountId)) }

    fun whenAnyOf(vararg conds: RuleCondition) = apply { conditions.add(ConditionGroup(conds.toList(), LogicalOperator.OR)) }
    fun whenAllOf(vararg conds: RuleCondition) = apply { conditions.add(ConditionGroup(conds.toList(), LogicalOperator.AND)) }

    fun thenSetCategory(categoryId: String, confidence: Float = 0.9f) = apply { actions.add(SetCategory(categoryId, confidence)) }
    fun withTag(vararg tags: String) = apply { actions.add(AddTag(tags.toList())) }
    fun removeTag(vararg tags: String) = apply { actions.add(RemoveTag(tags.toList())) }
    fun setMerchant(merchantName: String) = apply { actions.add(SetMerchant(merchantName)) }
    fun setNote(note: String) = apply { actions.add(SetNote(note, appendMode = false)) }
    fun appendNote(note: String) = apply { actions.add(SetNote(note, appendMode = true)) }
    fun flagForReview(reason: String? = null) = apply { actions.add(FlagForReview(reason)) }
    fun excludeFromReports(fromBudget: Boolean = true, fromStats: Boolean = true) = apply { actions.add(ExcludeFromReports(fromBudget, fromStats)) }
    fun excludeFromBudget() = excludeFromReports(fromBudget = true, fromStats = false)
    fun splitTransaction(vararg splits: SplitPart) = apply { actions.add(Split(splits.toList())) }
    fun linkAs(linkType: String, criteria: Map<String, String> = emptyMap()) = apply { actions.add(LinkTransaction(linkType, criteria)) }

    fun priority(priority: Int) = apply { this.priority = priority.coerceIn(CategoryRule.MIN_PRIORITY, CategoryRule.MAX_PRIORITY) }
    fun enabled(enabled: Boolean) = apply { this.enabled = enabled }
    fun disabled() = enabled(false)
    fun source(source: RuleSource) = apply { this.source = source }
    fun systemRule() = source(RuleSource.SYSTEM)
    fun suggestedRule() = source(RuleSource.SUGGESTED)

    fun build(): CategoryRule {
        require(conditions.isNotEmpty()) { "Rule must have at least one condition" }
        require(actions.isNotEmpty()) { "Rule must have at least one action" }
        return CategoryRule(id = id, name = name, description = description, conditions = ConditionGroup(conditions.toList(), conditionOperator),
            actions = actions.toList(), priority = priority, enabled = enabled, createdAt = Clock.System.now(), source = source)
    }

    fun buildValidated(): CategoryRule {
        val rule = build()
        val validation = rule.validate()
        if (validation is RuleValidationResult.Invalid) throw IllegalStateException("Rule validation failed: ${validation.errors.joinToString("; ")}")
        return rule
    }

    companion object {
        fun create(name: String): RuleBuilder {
            val id = "rule-${name.lowercase().replace(Regex("[^a-z0-9]+"), "-")}-${Clock.System.now().toEpochMilliseconds()}"
            return RuleBuilder(id).name(name)
        }

        fun merchantRule(id: String, merchantPattern: String, categoryId: String, confidence: Float = 0.9f, priority: Int = 100): CategoryRule =
            RuleBuilder(id).name("$merchantPattern → $categoryId").whenMerchantContains(merchantPattern).thenSetCategory(categoryId, confidence).priority(priority).build()

        fun keywordRule(id: String, keywords: List<String>, categoryId: String, confidence: Float = 0.8f, priority: Int = 50): CategoryRule =
            RuleBuilder(id).name("Keywords [${keywords.take(3).joinToString(", ")}] → $categoryId").whenDescriptionContains(*keywords.toTypedArray()).thenSetCategory(categoryId, confidence).priority(priority).build()
    }
}
