package com.ledgerlens.categorization.pipeline

import kotlin.test.*

class ReviewQueueManagerTest {
    private fun createFeatures(merchant: String = "Test", amountCents: Long = -2500) = TransactionFeatures(
        merchant, "Test", listOf("test"), amountCents, AmountBucket.fromCents(amountCents), amountCents < 0, 1, 15
    )

    private fun createResult(transactionId: String = "tx-1", confidence: Float = 0.4f) = PipelineResult(
        transactionId, ClassificationResult("Groceries", confidence, listOf(CategoryScore("Dining", 0.3f)), ClassificationExplanation("test", "Test")),
        CategorizationAction.QUEUE_FOR_REVIEW, emptyMap(), 10, PipelineStage.ML_CLASSIFICATION
    )

    @Test fun `enqueue adds item to queue`() {
        val m = ReviewQueueManager()
        val item = m.enqueue("tx-1", createFeatures(), createResult())
        assertNotNull(item); assertEquals("tx-1", item.transactionId); assertEquals(ReviewStatus.PENDING, item.status); assertTrue(m.contains("tx-1"))
    }

    @Test fun `enqueueBatch adds multiple items`() {
        val m = ReviewQueueManager()
        val items = m.enqueueBatch((1..5).map { ReviewEnqueueRequest("tx-$it", createFeatures(), createResult(transactionId = "tx-$it")) })
        assertEquals(5, items.size); assertEquals(5, m.pendingCount)
    }

    @Test fun `getNextForReview returns highest priority item`() {
        val m = ReviewQueueManager()
        m.enqueue("tx-low", createFeatures(amountCents = -500), createResult("tx-low", 0.8f))
        m.enqueue("tx-high", createFeatures(amountCents = -150000), createResult("tx-high", 0.2f))
        val next = m.getNextForReview()
        assertNotNull(next); assertEquals("tx-high", next.transactionId)
    }

    @Test fun `getFiltered returns matching items`() {
        val m = ReviewQueueManager()
        m.enqueue("tx-1", createFeatures(merchant = "Walmart"), createResult("tx-1"))
        m.enqueue("tx-2", createFeatures(merchant = "Target"), createResult("tx-2"))
        val filtered = m.getFiltered(ReviewQueueFilter(merchantPattern = "walmart"))
        assertEquals(1, filtered.size); assertEquals("tx-1", filtered[0].transactionId)
    }

    @Test fun `recordDecision updates item status`() {
        val m = ReviewQueueManager()
        m.enqueue("tx-1", createFeatures(), createResult())
        assertTrue(m.recordDecision("tx-1", ReviewDecision.Accept()))
        assertEquals(ReviewStatus.ACCEPTED, m.getItem("tx-1")?.status)
    }

    @Test fun `recordDecision Reject updates status`() {
        val m = ReviewQueueManager()
        m.enqueue("tx-1", createFeatures(), createResult())
        m.recordDecision("tx-1", ReviewDecision.Reject(newCategoryId = "Dining"))
        assertEquals(ReviewStatus.REJECTED, m.getItem("tx-1")?.status)
    }

    @Test fun `remove removes item from queue`() {
        val m = ReviewQueueManager()
        m.enqueue("tx-1", createFeatures(), createResult())
        assertNotNull(m.remove("tx-1")); assertFalse(m.contains("tx-1"))
    }

    @Test fun `getStats returns correct statistics`() {
        val m = ReviewQueueManager()
        m.enqueue("tx-1", createFeatures(), createResult("tx-1"))
        m.enqueue("tx-2", createFeatures(), createResult("tx-2"))
        m.enqueue("tx-3", createFeatures(), createResult("tx-3"))
        m.recordDecision("tx-1", ReviewDecision.Accept())
        m.recordDecision("tx-2", ReviewDecision.Reject(newCategoryId = "Other"))
        val stats = m.getStats()
        assertEquals(3, stats.totalItems); assertEquals(1, stats.pendingCount); assertEquals(1, stats.acceptedCount); assertEquals(1, stats.rejectedCount)
    }
}

class ReviewPriorityScorerTest {
    private fun createFeatures(amountCents: Long = -2500, isDebit: Boolean = true) = TransactionFeatures("Test", "Test", listOf("test"), amountCents, AmountBucket.fromCents(amountCents), isDebit, 1, 15)
    private fun createResult(confidence: Float) = PipelineResult("tx-1", ClassificationResult("Test", confidence, emptyList(), ClassificationExplanation("test", "test")), CategorizationAction.QUEUE_FOR_REVIEW, emptyMap(), 0, null)

    @Test fun `DefaultReviewPriorityScorer prioritizes low confidence`() {
        val s = DefaultReviewPriorityScorer()
        assertTrue(s.calculatePriority(createFeatures(), createResult(0.2f)) > s.calculatePriority(createFeatures(), createResult(0.9f)))
    }

    @Test fun `DefaultReviewPriorityScorer prioritizes high amounts`() {
        val s = DefaultReviewPriorityScorer()
        assertTrue(s.calculatePriority(createFeatures(amountCents = -150000), createResult(0.5f)) > s.calculatePriority(createFeatures(amountCents = -500), createResult(0.5f)))
    }

    @Test fun `priority is clamped between 0 and 1`() {
        val p = DefaultReviewPriorityScorer().calculatePriority(createFeatures(amountCents = -500000), createResult(0.0f))
        assertTrue(p in 0f..1f)
    }
}

class ReviewQueueFilterTest {
    @Test fun `pending filter returns correct config`() { assertEquals(ReviewStatus.PENDING, ReviewQueueFilter.pending().status) }
    @Test fun `highPriority filter sets threshold`() { assertEquals(0.9f, ReviewQueueFilter.highPriority(0.9f).minPriority) }
}
