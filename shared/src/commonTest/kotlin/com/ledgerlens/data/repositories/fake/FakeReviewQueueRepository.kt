package com.ledgerlens.data.repositories.fake

import com.ledgerlens.categorization.pipeline.*
import com.ledgerlens.data.repositories.ReviewQueueRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

/**
 * Fake implementation of ReviewQueueRepository for testing.
 */
class FakeReviewQueueRepository : ReviewQueueRepository {
    private val items = MutableStateFlow<Map<String, ReviewQueueItem>>(emptyMap())

    override suspend fun enqueue(
        transactionId: String,
        features: TransactionFeatures,
        pipelineResult: PipelineResult,
        metadata: ReviewItemMetadata
    ): ReviewQueueItem {
        val item = ReviewQueueItem(
            transactionId = transactionId,
            features = features,
            suggestedCategory = pipelineResult.classification,
            alternatives = pipelineResult.classification.alternatives,
            priority = calculatePriority(features, pipelineResult),
            enqueuedAt = Clock.System.now(),
            status = ReviewStatus.PENDING,
            metadata = metadata
        )
        items.value = items.value + (transactionId to item)
        return item
    }

    private fun calculatePriority(features: TransactionFeatures, result: PipelineResult): Float {
        // Simple priority calculation based on confidence
        return (1f - result.classification.confidence) * 100f
    }

    override suspend fun enqueueBatch(items: List<ReviewEnqueueRequest>): List<ReviewQueueItem> {
        return items.map { request ->
            enqueue(request.transactionId, request.features, request.pipelineResult, request.metadata)
        }
    }

    override fun getNextForReview(): Flow<ReviewQueueItem?> {
        return items.map { map ->
            map.values
                .filter { it.status == ReviewStatus.PENDING }
                .maxByOrNull { it.priority }
        }
    }

    override fun getForBatchReview(limit: Int): Flow<List<ReviewQueueItem>> {
        return items.map { map ->
            map.values
                .filter { it.status == ReviewStatus.PENDING }
                .sortedByDescending { it.priority }
                .take(limit)
        }
    }

    override fun getFiltered(filter: ReviewQueueFilter): Flow<List<ReviewQueueItem>> {
        val status = filter.status
        val minPriority = filter.minPriority
        val maxPriority = filter.maxPriority
        val categoryId = filter.categoryId

        return items.map { map ->
            map.values
                .filter { item ->
                    (status == null || item.status == status) &&
                        (minPriority == null || item.priority >= minPriority) &&
                        (maxPriority == null || item.priority <= maxPriority) &&
                        (categoryId == null || item.categoryId == categoryId)
                }
                .sortedByDescending { it.priority }
        }
    }

    override suspend fun recordDecision(transactionId: String, decision: ReviewDecision): Boolean {
        val item = items.value[transactionId] ?: return false
        val newStatus = when (decision) {
            is ReviewDecision.Accept -> ReviewStatus.ACCEPTED
            is ReviewDecision.Reject -> ReviewStatus.REJECTED
            is ReviewDecision.Defer -> ReviewStatus.DEFERRED
            is ReviewDecision.CreateRule -> ReviewStatus.ACCEPTED
        }
        val updatedItem = item.copy(
            status = newStatus,
            reviewedAt = Clock.System.now()
        )
        items.value = items.value + (transactionId to updatedItem)
        return true
    }

    override suspend fun remove(transactionId: String): Boolean {
        if (transactionId !in items.value) return false
        items.value = items.value - transactionId
        return true
    }

    override suspend fun clear() {
        items.value = emptyMap()
    }

    override fun getItem(transactionId: String): Flow<ReviewQueueItem?> {
        return items.map { it[transactionId] }
    }

    override suspend fun contains(transactionId: String): Boolean {
        return transactionId in items.value
    }

    override val pendingCount: Flow<Int>
        get() = items.map { map -> map.values.count { it.status == ReviewStatus.PENDING } }

    override val pendingItems: Flow<List<ReviewQueueItem>>
        get() = items.map { map ->
            map.values.filter { it.status == ReviewStatus.PENDING }
                .sortedByDescending { it.priority }
        }

    override fun getStats(): Flow<ReviewQueueStats> {
        return items.map { map ->
            val values = map.values.toList()
            val pendingItems = values.filter { it.status == ReviewStatus.PENDING }
            ReviewQueueStats(
                totalItems = values.size,
                pendingCount = pendingItems.size,
                acceptedCount = values.count { it.status == ReviewStatus.ACCEPTED },
                rejectedCount = values.count { it.status == ReviewStatus.REJECTED },
                deferredCount = values.count { it.status == ReviewStatus.DEFERRED },
                averagePriority = values.takeIf { it.isNotEmpty() }?.map { it.priority }?.average()?.toFloat() ?: 0f,
                oldestPendingItem = pendingItems.minByOrNull { it.enqueuedAt }?.enqueuedAt
            )
        }
    }

    // Test helper methods
    fun setItems(items: List<ReviewQueueItem>) {
        this.items.value = items.associateBy { it.transactionId }
    }

    fun getItemsSnapshot(): List<ReviewQueueItem> = items.value.values.toList()
}
