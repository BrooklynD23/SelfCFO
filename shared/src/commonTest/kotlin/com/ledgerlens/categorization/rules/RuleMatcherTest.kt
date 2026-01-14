package com.ledgerlens.categorization.rules

import com.ledgerlens.categorization.AmountBucket
import com.ledgerlens.categorization.TransactionFeatures
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuleMatcherTest {
    private val matcher = RuleMatcher()

    private fun createFeatures(
        merchant: String = "Test Merchant",
        description: String = "Test transaction description",
        amountCents: Long = -1000,
        dayOfWeek: Int = 0,
        dayOfMonth: Int = 15,
        accountId: String? = null
    ) = TransactionFeatures(
        merchantNormalized = merchant,
        descriptionRaw = description,
        descriptionTokens = description.lowercase().split(" "),
        amountCents = amountCents,
        amountBucket = AmountBucket.fromCents(amountCents),
        isDebit = amountCents < 0,
        dayOfWeek = dayOfWeek,
        dayOfMonth = dayOfMonth,
        accountId = accountId
    )

    @Test
    fun merchantContains_matchesWhenPatternFound() {
        val features = createFeatures(merchant = "STARBUCKS COFFEE #1234")
        assertTrue(matcher.matches(MerchantContains("starbucks"), features))
    }

    @Test
    fun merchantContains_noMatchWhenPatternNotFound() {
        val features = createFeatures(merchant = "WALMART SUPERCENTER")
        assertFalse(matcher.matches(MerchantContains("starbucks"), features))
    }

    @Test
    fun merchantContains_caseInsensitiveByDefault() {
        val features = createFeatures(merchant = "STARBUCKS")
        assertTrue(matcher.matches(MerchantContains("StArBuCkS"), features))
    }

    @Test
    fun descriptionContains_matchesAnyKeyword() {
        val features = createFeatures(description = "Coffee shop purchase downtown")
        assertTrue(matcher.matches(DescriptionContains(listOf("coffee", "tea", "beverage")), features))
    }

    @Test
    fun descriptionContains_matchAllRequired() {
        val features = createFeatures(description = "Coffee shop with tea options")
        assertTrue(matcher.matches(DescriptionContains(listOf("coffee", "tea"), matchAll = true), features))
    }

    @Test
    fun descriptionContains_matchAllFails() {
        val features = createFeatures(description = "Coffee shop downtown")
        assertFalse(matcher.matches(DescriptionContains(listOf("coffee", "tea"), matchAll = true), features))
    }

    @Test
    fun amountRange_matchesWithinRange() {
        val features = createFeatures(amountCents = -1500)
        assertTrue(matcher.matches(AmountRange(minCents = 1000, maxCents = 2000), features))
    }

    @Test
    fun amountRange_noMatchOutsideRange() {
        val features = createFeatures(amountCents = -3000)
        assertFalse(matcher.matches(AmountRange(minCents = 1000, maxCents = 2000), features))
    }

    @Test
    fun transactionType_matchesDebit() {
        val features = createFeatures(amountCents = -1000)
        assertTrue(matcher.matches(TransactionType(isDebit = true), features))
    }

    @Test
    fun transactionType_matchesCredit() {
        val features = createFeatures(amountCents = 1000)
        assertTrue(matcher.matches(TransactionType(isDebit = false), features))
    }

    @Test
    fun conditionGroup_andMatchesWhenAllMatch() {
        val features = createFeatures(merchant = "STARBUCKS", amountCents = -500)
        val condition = ConditionGroup(listOf(MerchantContains("starbucks"), AmountRange(maxCents = 1000)), LogicalOperator.AND)
        assertTrue(matcher.matches(condition, features))
    }

    @Test
    fun conditionGroup_andFailsWhenOneFails() {
        val features = createFeatures(merchant = "STARBUCKS", amountCents = -2000)
        val condition = ConditionGroup(listOf(MerchantContains("starbucks"), AmountRange(maxCents = 1000)), LogicalOperator.AND)
        assertFalse(matcher.matches(condition, features))
    }

    @Test
    fun conditionGroup_orMatchesWhenOneMatches() {
        val features = createFeatures(merchant = "STARBUCKS")
        val condition = ConditionGroup(listOf(MerchantContains("starbucks"), MerchantContains("peets")), LogicalOperator.OR)
        assertTrue(matcher.matches(condition, features))
    }

    @Test
    fun evaluate_disabledRuleDoesNotMatch() {
        val features = createFeatures(merchant = "STARBUCKS")
        val rule = RuleBuilder("test-rule").name("Test").whenMerchantContains("starbucks").thenSetCategory("dining").disabled().build()
        assertFalse(matcher.evaluate(rule, features).matched)
    }

    @Test
    fun evaluate_enabledRuleMatches() {
        val features = createFeatures(merchant = "STARBUCKS")
        val rule = RuleBuilder("test-rule").name("Test").whenMerchantContains("starbucks").thenSetCategory("dining").build()
        val result = matcher.evaluate(rule, features)
        assertTrue(result.matched)
        assertTrue(result.appliedActions.isNotEmpty())
    }
}
