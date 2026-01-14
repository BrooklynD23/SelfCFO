package com.ledgerlens.categorization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PriorCalculatorTest {

    private val calculator = PriorCalculator()

    @Test
    fun `calculateProbabilities returns empty for merchant with no history`() {
        val prior = MerchantPrior.empty("amazon")
        
        val distribution = calculator.calculateProbabilities(prior)
        
        assertEquals("amazon", distribution.merchantId)
        assertTrue(distribution.probabilities.isEmpty())
        assertEquals(0, distribution.totalObservations)
        assertFalse(distribution.isReliable)
    }

    @Test
    fun `calculateProbabilities returns correct frequencies for single category`() {
        val prior = MerchantPrior(
            merchantId = "starbucks",
            categoryCounts = mapOf("Dining" to 10),
            lastCategoryId = "Dining",
            totalTransactions = 10,
            lastUpdatedEpochMs = System.currentTimeMillis()
        )
        
        val distribution = calculator.calculateProbabilities(prior)
        
        assertEquals("starbucks", distribution.merchantId)
        assertEquals(1, distribution.probabilities.size)
        assertEquals(10, distribution.totalObservations)
        assertTrue(distribution.isReliable)
        
        val diningProb = distribution.probabilityFor("Dining")
        // With Laplace smoothing: (10 + 1) / (10 + 1*1) = 11/11 = 1.0
        assertEquals(1.0f, diningProb, 0.001f)
    }

    @Test
    fun `calculateProbabilities returns correct distribution for multiple categories`() {
        val prior = MerchantPrior(
            merchantId = "target",
            categoryCounts = mapOf(
                "Groceries" to 6,
                "Shopping" to 3,
                "Home" to 1
            ),
            lastCategoryId = "Groceries",
            totalTransactions = 10,
            lastUpdatedEpochMs = System.currentTimeMillis()
        )
        
        val distribution = calculator.calculateProbabilities(prior)
        
        assertEquals(3, distribution.probabilities.size)
        assertEquals(10, distribution.totalObservations)
        
        // With Laplace smoothing (pseudo_count = 1):
        // Groceries: (6 + 1) / (10 + 3*1) = 7/13 ≈ 0.538
        // Shopping: (3 + 1) / (10 + 3*1) = 4/13 ≈ 0.308
        // Home: (1 + 1) / (10 + 3*1) = 2/13 ≈ 0.154
        val groceriesProb = distribution.probabilityFor("Groceries")
        val shoppingProb = distribution.probabilityFor("Shopping")
        val homeProb = distribution.probabilityFor("Home")
        
        assertTrue(groceriesProb > shoppingProb)
        assertTrue(shoppingProb > homeProb)
        
        // Sum should be close to 1
        val sum = groceriesProb + shoppingProb + homeProb
        assertEquals(1.0f, sum, 0.001f)
    }

    @Test
    fun `calculateProbabilities with decay weights recent transactions more`() {
        val now = System.currentTimeMillis()
        val dayInMs = 24 * 60 * 60 * 1000L
        
        // Old assignments to Groceries, recent to Shopping
        val assignments = listOf(
            TimestampedAssignment("Groceries", now - 180 * dayInMs), // 180 days ago
            TimestampedAssignment("Groceries", now - 180 * dayInMs),
            TimestampedAssignment("Groceries", now - 180 * dayInMs),
            TimestampedAssignment("Shopping", now - 1 * dayInMs),    // 1 day ago
            TimestampedAssignment("Shopping", now - 2 * dayInMs)     // 2 days ago
        )
        
        val prior = MerchantPrior(
            merchantId = "target",
            categoryCounts = mapOf("Groceries" to 3, "Shopping" to 2),
            lastCategoryId = "Shopping",
            totalTransactions = 5,
            lastUpdatedEpochMs = now
        )
        
        val distribution = calculator.calculateProbabilities(
            prior = prior,
            categoryAssignments = assignments,
            currentTimeMs = now
        )
        
        // Despite Groceries having more raw counts (3 vs 2),
        // Shopping should have higher probability due to recency
        val groceriesProb = distribution.probabilityFor("Groceries")
        val shoppingProb = distribution.probabilityFor("Shopping")
        
        assertTrue(
            shoppingProb > groceriesProb,
            "Recent Shopping ($shoppingProb) should outweigh old Groceries ($groceriesProb)"
        )
    }

    @Test
    fun `calculateDecayWeight returns 1 for current time`() {
        val weight = calculator.calculateDecayWeight(0)
        assertEquals(1.0f, weight, 0.001f)
    }

    @Test
    fun `calculateDecayWeight returns approximately 0_5 at half-life`() {
        val halfLifeDays = 90f
        val halfLifeMs = (halfLifeDays * 24 * 60 * 60 * 1000).toLong()
        
        val weight = calculator.calculateDecayWeight(halfLifeMs)
        assertEquals(0.5f, weight, 0.01f)
    }

    @Test
    fun `calculateDecayWeight decreases with age`() {
        val dayInMs = 24 * 60 * 60 * 1000L
        
        val weight1Day = calculator.calculateDecayWeight(1 * dayInMs)
        val weight30Days = calculator.calculateDecayWeight(30 * dayInMs)
        val weight90Days = calculator.calculateDecayWeight(90 * dayInMs)
        val weight180Days = calculator.calculateDecayWeight(180 * dayInMs)
        
        assertTrue(weight1Day > weight30Days)
        assertTrue(weight30Days > weight90Days)
        assertTrue(weight90Days > weight180Days)
    }

    @Test
    fun `combineScores produces weighted average`() {
        val merchantPrior = 0.8f
        val classifierConfidence = 0.6f
        val weight = 0.4f
        
        val combined = calculator.combineScores(merchantPrior, classifierConfidence, weight)
        
        // Expected: 0.4 * 0.8 + 0.6 * 0.6 = 0.32 + 0.36 = 0.68
        assertEquals(0.68f, combined, 0.001f)
    }

    @Test
    fun `combineScores with zero weight ignores merchant prior`() {
        val combined = calculator.combineScores(
            merchantPriorProbability = 0.9f,
            classifierConfidence = 0.5f,
            merchantWeight = 0.0f
        )
        
        assertEquals(0.5f, combined, 0.001f)
    }

    @Test
    fun `combineScores with full weight uses only merchant prior`() {
        val combined = calculator.combineScores(
            merchantPriorProbability = 0.9f,
            classifierConfidence = 0.5f,
            merchantWeight = 1.0f
        )
        
        assertEquals(0.9f, combined, 0.001f)
    }

    @Test
    fun `computePosterior calculates correct Bayesian update`() {
        val priors = mapOf(
            "Dining" to 0.6f,
            "Groceries" to 0.3f,
            "Shopping" to 0.1f
        )
        val likelihoods = mapOf(
            "Dining" to 0.8f,
            "Groceries" to 0.1f,
            "Shopping" to 0.1f
        )
        
        val diningPosterior = calculator.computePosterior(
            categoryId = "Dining",
            priorProbability = 0.6f,
            likelihood = 0.8f,
            priors = priors,
            likelihoods = likelihoods
        )
        
        // Evidence = 0.6*0.8 + 0.3*0.1 + 0.1*0.1 = 0.48 + 0.03 + 0.01 = 0.52
        // Posterior = (0.8 * 0.6) / 0.52 = 0.48 / 0.52 ≈ 0.923
        assertEquals(0.923f, diningPosterior, 0.01f)
    }

    @Test
    fun `mostLikelyCategory returns category with highest probability`() {
        val prior = MerchantPrior(
            merchantId = "test",
            categoryCounts = mapOf(
                "A" to 5,
                "B" to 3,
                "C" to 2
            ),
            lastCategoryId = "A",
            totalTransactions = 10,
            lastUpdatedEpochMs = System.currentTimeMillis()
        )
        
        val distribution = calculator.calculateProbabilities(prior)
        
        assertNotNull(distribution.mostLikelyCategory)
        assertEquals("A", distribution.mostLikelyCategory?.categoryId)
    }

    @Test
    fun `custom params affect calculation`() {
        val customCalculator = PriorCalculator(
            PriorCalculationParams(
                decayHalfLifeDays = 30f, // Faster decay
                pseudoCount = 0.5f,      // Less smoothing
                minObservationsForPrior = 5
            )
        )
        
        // With minObservations = 5, a prior with 3 transactions should return empty
        val prior = MerchantPrior(
            merchantId = "test",
            categoryCounts = mapOf("A" to 3),
            lastCategoryId = "A",
            totalTransactions = 3,
            lastUpdatedEpochMs = System.currentTimeMillis()
        )
        
        val distribution = customCalculator.calculateProbabilities(prior)
        
        assertTrue(distribution.probabilities.isEmpty())
        assertFalse(distribution.isReliable)
    }
}
