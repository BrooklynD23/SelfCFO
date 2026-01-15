package com.ledgerlens.categorization.rules

import com.ledgerlens.categorization.AmountBucket
import com.ledgerlens.categorization.TransactionFeatures
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuleMatcherTest {
    private val matcher = RuleMatcher()

    private fun createFeatures(merchant: String = "Test Merchant", description: String = "Test description", amountCents: Long = -1000, dayOfWeek: Int = 0, dayOfMonth: Int = 15, accountId: String? = null) = TransactionFeatures(merchant, description, description.lowercase().split(" "), amountCents, AmountBucket.fromCents(amountCents), amountCents < 0, dayOfWeek, dayOfMonth, accountId)

    @Test fun merchantContains_matchesWhenPatternFound() { assertTrue(matcher.matches(MerchantContains("starbucks"), createFeatures(merchant = "STARBUCKS COFFEE"))) }
    @Test fun merchantContains_noMatchWhenPatternNotFound() { assertFalse(matcher.matches(MerchantContains("starbucks"), createFeatures(merchant = "WALMART"))) }
    @Test fun merchantContains_caseInsensitiveByDefault() { assertTrue(matcher.matches(MerchantContains("StArBuCkS"), createFeatures(merchant = "STARBUCKS"))) }
    @Test fun descriptionContains_matchesAnyKeyword() { assertTrue(matcher.matches(DescriptionContains(listOf("coffee", "tea")), createFeatures(description = "Coffee shop"))) }
    @Test fun descriptionContains_matchAllRequired() { assertTrue(matcher.matches(DescriptionContains(listOf("coffee", "tea"), matchAll = true), createFeatures(description = "Coffee and tea shop"))) }
    @Test fun descriptionContains_matchAllFails() { assertFalse(matcher.matches(DescriptionContains(listOf("coffee", "tea"), matchAll = true), createFeatures(description = "Coffee shop"))) }
    @Test fun amountRange_matchesWithinRange() { assertTrue(matcher.matches(AmountRange(1000, 2000), createFeatures(amountCents = -1500))) }
    @Test fun amountRange_noMatchOutsideRange() { assertFalse(matcher.matches(AmountRange(1000, 2000), createFeatures(amountCents = -3000))) }
    @Test fun transactionType_matchesDebit() { assertTrue(matcher.matches(TransactionType(true), createFeatures(amountCents = -1000))) }
    @Test fun transactionType_matchesCredit() { assertTrue(matcher.matches(TransactionType(false), createFeatures(amountCents = 1000))) }
    @Test fun conditionGroup_andMatchesWhenAllMatch() { assertTrue(matcher.matches(ConditionGroup(listOf(MerchantContains("starbucks"), AmountRange(maxCents = 1000)), LogicalOperator.AND), createFeatures(merchant = "STARBUCKS", amountCents = -500))) }
    @Test fun conditionGroup_andFailsWhenOneFails() { assertFalse(matcher.matches(ConditionGroup(listOf(MerchantContains("starbucks"), AmountRange(maxCents = 1000)), LogicalOperator.AND), createFeatures(merchant = "STARBUCKS", amountCents = -2000))) }
    @Test fun conditionGroup_orMatchesWhenOneMatches() { assertTrue(matcher.matches(ConditionGroup(listOf(MerchantContains("starbucks"), MerchantContains("peets")), LogicalOperator.OR), createFeatures(merchant = "STARBUCKS"))) }
    @Test fun evaluate_disabledRuleDoesNotMatch() { assertFalse(matcher.evaluate(RuleBuilder("test").whenMerchantContains("starbucks").thenSetCategory("dining").disabled().build(), createFeatures(merchant = "STARBUCKS")).matched) }
    @Test fun evaluate_enabledRuleMatches() { val result = matcher.evaluate(RuleBuilder("test").whenMerchantContains("starbucks").thenSetCategory("dining").build(), createFeatures(merchant = "STARBUCKS")); assertTrue(result.matched && result.appliedActions.isNotEmpty()) }
}
