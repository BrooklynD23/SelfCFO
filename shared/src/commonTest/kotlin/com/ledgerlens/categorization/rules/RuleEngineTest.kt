package com.ledgerlens.categorization.rules

import com.ledgerlens.categorization.AmountBucket
import com.ledgerlens.categorization.TransactionFeatures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuleEngineTest {
    private val engine = RuleEngine()

    private fun createFeatures(
        merchant: String = "Test Merchant",
        description: String = "Test description",
        amountCents: Long = -1000,
        dayOfWeek: Int = 0,
        dayOfMonth: Int = 15,
        accountId: String? = null
    ) =
        TransactionFeatures(merchant, description, description.lowercase().split(" "), amountCents, AmountBucket.fromCents(amountCents), amountCents < 0, dayOfWeek, dayOfMonth, accountId)

    @Test fun evaluate_higherPriorityRuleWins() {
        val features = createFeatures(merchant = "STARBUCKS")
        val low = RuleBuilder("low").whenMerchantContains("starbucks").thenSetCategory("general").priority(50).build()
        val high = RuleBuilder("high").whenMerchantContains("starbucks").thenSetCategory("dining").priority(100).build()
        val result = engine.evaluate(listOf(low, high), features)
        assertEquals("dining", result.categoryId)
        assertEquals("high", result.primaryCategoryRule?.id)
    }

    @Test fun evaluate_multipleRulesCanMatch() {
        val features = createFeatures(merchant = "STARBUCKS", amountCents = -500)
        val merchantRule = RuleBuilder("merchant").whenMerchantContains("starbucks").thenSetCategory("dining").priority(100).build()
        val tagRule = RuleBuilder("tag").whenAmountLessThan(1000).withTag("small").priority(50).build()
        val result = engine.evaluate(listOf(merchantRule, tagRule), features)
        assertEquals(2, result.matchCount)
        assertTrue(result.matchedRuleIds.containsAll(listOf("merchant", "tag")))
    }

    @Test fun evaluate_detectsConflictingCategories() {
        val features = createFeatures(merchant = "STARBUCKS")
        val r1 = RuleBuilder("r1").whenMerchantContains("starbucks").thenSetCategory("dining").priority(100).build()
        val r2 = RuleBuilder("r2").whenMerchantContains("starbucks").thenSetCategory("entertainment").priority(50).build()
        val result = engine.evaluate(listOf(r1, r2), features)
        assertTrue(result.hasConflicts)
        assertEquals("dining", result.categoryId)
    }

    @Test fun evaluate_noMatchReturnsEmpty() {
        val result = engine.evaluate(listOf(RuleBuilder("r").whenMerchantContains("starbucks").thenSetCategory("dining").build()), createFeatures(merchant = "WALMART"))
        assertFalse(result.hasMatches)
        assertNull(result.categoryId)
    }

    @Test fun evaluate_disabledRulesSkipped() {
        val features = createFeatures(merchant = "STARBUCKS")
        val disabled = RuleBuilder("disabled").whenMerchantContains("starbucks").thenSetCategory("dining").priority(100).disabled().build()
        val enabled = RuleBuilder("enabled").whenMerchantContains("starbucks").thenSetCategory("coffee").priority(50).build()
        val result = engine.evaluate(listOf(disabled, enabled), features)
        assertEquals("coffee", result.categoryId)
        assertEquals(1, result.matchCount)
    }

    @Test fun toClassificationResult_convertsCorrectly() {
        val features = createFeatures(merchant = "STARBUCKS")
        val rule = RuleBuilder("test").name("Starbucks Rule").whenMerchantContains("starbucks").thenSetCategory("dining", 0.95f).build()
        val classificationResult = engine.toClassificationResult(engine.evaluate(listOf(rule), features))
        assertEquals("dining", classificationResult.categoryId)
        assertEquals(0.95f, classificationResult.confidence)
        assertEquals("rules-engine", classificationResult.explanation.classifierUsed)
    }

    @Test fun evaluate_mergesAllTags() {
        val features = createFeatures(merchant = "STARBUCKS")
        val r1 = RuleBuilder("r1").whenMerchantContains("starbucks").withTag("coffee", "morning").priority(100).build()
        val r2 = RuleBuilder("r2").whenMerchantContains("starbucks").withTag("frequent", "coffee").priority(50).build()
        val result = engine.evaluate(listOf(r1, r2), features)
        val tags = result.mergedActions.filterIsInstance<AddTag>().first().tags
        assertEquals(3, tags.size)
        assertTrue(tags.containsAll(listOf("coffee", "morning", "frequent")))
    }

    @Test fun findMatchingRules_returnsAllMatches() {
        val features = createFeatures(merchant = "STARBUCKS", amountCents = -500)
        val rules = listOf(
            RuleBuilder("r1").whenMerchantContains("starbucks").thenSetCategory("dining").build(),
            RuleBuilder("r2").whenAmountLessThan(1000).thenSetCategory("small").build(),
            RuleBuilder("r3").whenMerchantContains("walmart").thenSetCategory("shopping").build()
        )
        val matches = engine.findMatchingRules(rules, features)
        assertEquals(2, matches.size)
        assertTrue(matches.any { it.id == "r1" } && matches.any { it.id == "r2" })
    }
}
