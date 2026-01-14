package com.ledgerlens.categorization.pipeline

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class BatchCategorizerTest {
    private fun createFeatures(merchant: String = "Test", amountCents: Long = -2500) = TransactionFeatures(
        merchant, "Test", listOf("test"), amountCents, AmountBucket.fromCents(amountCents), amountCents < 0, 1, 15
    )

    private fun createPipeline(rules: List<ClassificationRule> = emptyList()) = CategorizationPipelineImpl.createDefault(rules = rules)

    @Test fun `processBatch processes all transactions`() {
        val rules = listOf(MerchantContainsRule("r", "Groceries", listOf("walmart"), 0.95f))
        val result = BatchCategorizer(createPipeline(rules)).processBatch((1..10).map { TransactionInput("tx-$it", createFeatures(merchant = if (it % 2 == 0) "walmart" else "unknown")) })
        assertEquals(10, result.batchSize)
        assertEquals(10, result.successCount)
        assertEquals(1f, result.successRate)
    }

    @Test fun `processBatch tracks progress`() {
        val updates = mutableListOf<Pair<Int, Int>>()
        val callback = object : BatchProgressCallback { override fun onTransactionProcessed(current: Int, total: Int, result: PipelineResult) { updates.add(current to total) } }
        BatchCategorizer(createPipeline()).processBatch((1..5).map { TransactionInput("tx-$it", createFeatures()) }, callback)
        assertEquals(5, updates.size)
        assertEquals(1 to 5, updates[0])
        assertEquals(5 to 5, updates.last())
    }

    @Test fun `processBatch calls callbacks`() {
        var started = false; var completed = false
        val callback = object : BatchProgressCallback { override fun onBatchStarted(totalCount: Int) { started = true }; override fun onBatchCompleted(result: BatchResult) { completed = true } }
        BatchCategorizer(createPipeline()).processBatch(listOf(TransactionInput("tx-1", createFeatures())), callback)
        assertTrue(started); assertTrue(completed)
    }

    @Test fun `BatchResult contains correct statistics`() {
        val rules = listOf(MerchantContainsRule("r", "Groceries", listOf("walmart"), 0.95f))
        val result = BatchCategorizer(createPipeline(rules)).processBatch(listOf(
            TransactionInput("tx-1", createFeatures(merchant = "walmart")),
            TransactionInput("tx-2", createFeatures(merchant = "unknown"))
        ))
        assertEquals(1, result.autoAppliedCount)
        assertEquals(1, result.needsReviewCount)
    }

    @Test fun `processBatchAsync processes transactions`() = runTest {
        val rules = listOf(MerchantContainsRule("r", "Groceries", listOf("walmart"), 0.95f))
        val result = BatchCategorizer(createPipeline(rules)).processBatchAsync((1..5).map { TransactionInput("tx-$it", createFeatures(merchant = if (it <= 3) "walmart" else "unknown")) })
        assertEquals(5, result.batchSize)
        assertEquals(3, result.autoAppliedCount)
    }

    @Test fun `BatchCategorizationBuilder creates job`() {
        val job = BatchCategorizationBuilder().addTransaction("tx-1", createFeatures()).addTransaction("tx-2", createFeatures()).withConfig(BatchCategorizerConfig(chunkSize = 10)).build()
        assertEquals(2, job.transactions.size)
        assertEquals(10, job.config.chunkSize)
    }
}

class BatchResultTest {
    @Test fun `empty batch has correct defaults`() {
        val r = BatchResult(emptyList(), emptyMap(), 0, 0)
        assertEquals(0, r.successCount); assertEquals(0f, r.successRate); assertEquals(0f, r.averageConfidence)
    }
}
