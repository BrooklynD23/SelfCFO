package com.ledgerlens.data.repositories

import com.ledgerlens.categorization.pipeline.ReviewDecision
import com.ledgerlens.categorization.pipeline.ReviewEnqueueRequest
import com.ledgerlens.categorization.pipeline.ReviewQueueFilter
import com.ledgerlens.categorization.pipeline.ReviewQueueItem
import com.ledgerlens.categorization.pipeline.ReviewQueueStats
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for review queue data access.
 * Provides persistent storage for review queue items using SQLDelight.
 */
interface ReviewQueueRepository {
    /**
     * Enqueue a transaction for review.
     */
    suspend fun enqueue(
        transactionId: String,
        features: com.ledgerlens.categorization.pipeline.TransactionFeatures,
        pipelineResult: com.ledgerlens.categorization.pipeline.PipelineResult,
        metadata: com.ledgerlens.categorization.pipeline.ReviewItemMetadata = com.ledgerlens.categorization.pipeline.ReviewItemMetadata()
    ): ReviewQueueItem

    /**
     * Enqueue multiple transactions for review.
     */
    suspend fun enqueueBatch(items: List<ReviewEnqueueRequest>): List<ReviewQueueItem>

    /**
     * Get the next item for review (highest priority pending).
     */
    fun getNextForReview(): Flow<ReviewQueueItem?>

    /**
     * Get a batch of items for review (top N pending by priority).
     */
    fun getForBatchReview(limit: Int = 10): Flow<List<ReviewQueueItem>>

    /**
     * Get filtered review queue items.
     */
    fun getFiltered(filter: ReviewQueueFilter): Flow<List<ReviewQueueItem>>

    /**
     * Record a decision for a review item.
     */
    suspend fun recordDecision(transactionId: String, decision: ReviewDecision): Boolean

    /**
     * Remove a review queue item.
     */
    suspend fun remove(transactionId: String): Boolean

    /**
     * Clear all review queue items.
     */
    suspend fun clear()

    /**
     * Get a specific review queue item.
     */
    fun getItem(transactionId: String): Flow<ReviewQueueItem?>

    /**
     * Check if a transaction is in the review queue.
     */
    suspend fun contains(transactionId: String): Boolean

    /**
     * Get count of pending items.
     */
    val pendingCount: Flow<Int>

    /**
     * Get all pending items.
     */
    val pendingItems: Flow<List<ReviewQueueItem>>

    /**
     * Get review queue statistics.
     */
    fun getStats(): Flow<ReviewQueueStats>
}
