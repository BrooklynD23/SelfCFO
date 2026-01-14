package com.ledgerlens.categorization

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MerchantPriorRepositoryTest {

    private fun createRepository(): MerchantPriorRepositoryImpl {
        return MerchantPriorRepositoryImpl(timeProvider = { 1000000L })
    }

    @Test
    fun `getPrior returns null for unknown merchant`() = runTest {
        val repo = createRepository()
        
        val result = repo.getPrior("unknown-merchant")
        
        assertNull(result)
    }

    @Test
    fun `savePrior and getPrior round-trip correctly`() = runTest {
        val repo = createRepository()
        val prior = MerchantPrior(
            merchantId = "starbucks",
            categoryCounts = mapOf("Dining" to 5, "Coffee" to 3),
            lastCategoryId = "Dining",
            totalTransactions = 8,
            lastUpdatedEpochMs = 12345L
        )
        
        repo.savePrior(prior)
        val result = repo.getPrior("starbucks")
        
        assertNotNull(result)
        assertEquals("starbucks", result.merchantId)
        assertEquals(8, result.totalTransactions)
        assertEquals(5, result.categoryCounts["Dining"])
        assertEquals(3, result.categoryCounts["Coffee"])
    }

    @Test
    fun `getPrior is case-insensitive`() = runTest {
        val repo = createRepository()
        val prior = MerchantPrior(
            merchantId = "Amazon",
            categoryCounts = mapOf("Shopping" to 10),
            lastCategoryId = "Shopping",
            totalTransactions = 10,
            lastUpdatedEpochMs = 12345L
        )
        
        repo.savePrior(prior)
        
        assertNotNull(repo.getPrior("amazon"))
        assertNotNull(repo.getPrior("AMAZON"))
        assertNotNull(repo.getPrior("Amazon"))
    }

    @Test
    fun `recordCategoryAssignment creates new prior for unknown merchant`() = runTest {
        val repo = createRepository()
        
        repo.recordCategoryAssignment("new-merchant", "Groceries", System.currentTimeMillis())
        
        val result = repo.getPrior("new-merchant")
        assertNotNull(result)
        assertEquals(1, result.totalTransactions)
        assertEquals(1, result.categoryCounts["Groceries"])
        assertEquals("Groceries", result.lastCategoryId)
    }

    @Test
    fun `recordCategoryAssignment increments existing prior`() = runTest {
        val repo = createRepository()
        
        repo.recordCategoryAssignment("target", "Groceries", 1000L)
        repo.recordCategoryAssignment("target", "Groceries", 2000L)
        repo.recordCategoryAssignment("target", "Shopping", 3000L)
        
        val result = repo.getPrior("target")
        assertNotNull(result)
        assertEquals(3, result.totalTransactions)
        assertEquals(2, result.categoryCounts["Groceries"])
        assertEquals(1, result.categoryCounts["Shopping"])
        assertEquals("Shopping", result.lastCategoryId)
    }

    @Test
    fun `recordCategoryAssignments handles batch updates`() = runTest {
        val repo = createRepository()
        val assignments = listOf(
            CategoryAssignment("merchant1", "Dining", 1000L),
            CategoryAssignment("merchant1", "Dining", 2000L),
            CategoryAssignment("merchant2", "Shopping", 3000L),
            CategoryAssignment("merchant1", "Coffee", 4000L)
        )
        
        repo.recordCategoryAssignments(assignments)
        
        val merchant1 = repo.getPrior("merchant1")
        assertNotNull(merchant1)
        assertEquals(3, merchant1.totalTransactions)
        assertEquals(2, merchant1.categoryCounts["Dining"])
        assertEquals(1, merchant1.categoryCounts["Coffee"])
        
        val merchant2 = repo.getPrior("merchant2")
        assertNotNull(merchant2)
        assertEquals(1, merchant2.totalTransactions)
    }

    @Test
    fun `getPriors returns batch results`() = runTest {
        val repo = createRepository()
        repo.recordCategoryAssignment("a", "Cat1", 1000L)
        repo.recordCategoryAssignment("b", "Cat2", 1000L)
        repo.recordCategoryAssignment("c", "Cat3", 1000L)
        
        val results = repo.getPriors(listOf("a", "b", "d"))
        
        assertEquals(2, results.size)
        assertTrue(results.containsKey("a"))
        assertTrue(results.containsKey("b"))
    }

    @Test
    fun `deletePrior removes merchant`() = runTest {
        val repo = createRepository()
        repo.recordCategoryAssignment("to-delete", "Cat1", 1000L)
        
        assertNotNull(repo.getPrior("to-delete"))
        
        repo.deletePrior("to-delete")
        
        assertNull(repo.getPrior("to-delete"))
    }

    @Test
    fun `getMerchantsForCategory returns correct merchants`() = runTest {
        val repo = createRepository()
        repo.recordCategoryAssignment("starbucks", "Dining", 1000L)
        repo.recordCategoryAssignment("chipotle", "Dining", 1000L)
        repo.recordCategoryAssignment("amazon", "Shopping", 1000L)
        repo.recordCategoryAssignment("target", "Shopping", 1000L)
        repo.recordCategoryAssignment("target", "Groceries", 2000L)
        
        val diningMerchants = repo.getMerchantsForCategory("Dining")
        val shoppingMerchants = repo.getMerchantsForCategory("Shopping")
        
        assertEquals(2, diningMerchants.size)
        assertTrue(diningMerchants.contains("starbucks"))
        assertTrue(diningMerchants.contains("chipotle"))
        
        assertEquals(2, shoppingMerchants.size)
        assertTrue(shoppingMerchants.contains("amazon"))
        assertTrue(shoppingMerchants.contains("target"))
    }

    @Test
    fun `getTopMerchants returns sorted by transaction count`() = runTest {
        val repo = createRepository()
        repeat(5) { repo.recordCategoryAssignment("merchant-5", "Cat", 1000L) }
        repeat(10) { repo.recordCategoryAssignment("merchant-10", "Cat", 1000L) }
        repeat(3) { repo.recordCategoryAssignment("merchant-3", "Cat", 1000L) }
        
        val top = repo.getTopMerchants(2)
        
        assertEquals(2, top.size)
        assertEquals("merchant-10", top[0].merchantId)
        assertEquals("merchant-5", top[1].merchantId)
    }

    @Test
    fun `count returns correct number of merchants`() = runTest {
        val repo = createRepository()
        
        assertEquals(0, repo.count())
        
        repo.recordCategoryAssignment("a", "Cat", 1000L)
        repo.recordCategoryAssignment("b", "Cat", 1000L)
        repo.recordCategoryAssignment("a", "Cat", 2000L) // Same merchant
        
        assertEquals(2, repo.count())
    }

    @Test
    fun `clearAll removes all priors`() = runTest {
        val repo = createRepository()
        repo.recordCategoryAssignment("a", "Cat", 1000L)
        repo.recordCategoryAssignment("b", "Cat", 1000L)
        
        assertEquals(2, repo.count())
        
        repo.clearAll()
        
        assertEquals(0, repo.count())
        assertNull(repo.getPrior("a"))
    }

    @Test
    fun `savePriors batch saves correctly`() = runTest {
        val repo = createRepository()
        val priors = listOf(
            MerchantPrior(
                merchantId = "m1",
                categoryCounts = mapOf("A" to 1),
                lastCategoryId = "A",
                totalTransactions = 1,
                lastUpdatedEpochMs = 1000L
            ),
            MerchantPrior(
                merchantId = "m2",
                categoryCounts = mapOf("B" to 2),
                lastCategoryId = "B",
                totalTransactions = 2,
                lastUpdatedEpochMs = 1000L
            )
        )
        
        repo.savePriors(priors)
        
        assertEquals(2, repo.count())
        assertEquals(1, repo.getPrior("m1")?.totalTransactions)
        assertEquals(2, repo.getPrior("m2")?.totalTransactions)
    }
}
