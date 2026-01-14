package com.ledgerlens.categorization.pipeline

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class ReviewQueueManager(private val priorityScorer: ReviewPriorityScorer = DefaultReviewPriorityScorer()) {
    private val queue = mutableMapOf<String, ReviewQueueItem>()

    fun enqueue(transactionId: String, features: TransactionFeatures, pipelineResult: PipelineResult, metadata: ReviewItemMetadata = ReviewItemMetadata()): ReviewQueueItem {
        val item = ReviewQueueItem(transactionId, features, pipelineResult.classification, pipelineResult.classification.alternatives,
            priorityScorer.calculatePriority(features, pipelineResult), Clock.System.now(), metadata = metadata)
        queue[transactionId] = item
        return item
    }

    fun enqueueBatch(items: List<ReviewEnqueueRequest>) = items.map { enqueue(it.transactionId, it.features, it.pipelineResult, it.metadata) }
    fun getNextForReview() = queue.values.filter { it.status == ReviewStatus.PENDING }.maxByOrNull { it.priority }
    fun getForBatchReview(limit: Int = 10) = queue.values.filter { it.status == ReviewStatus.PENDING }.sortedByDescending { it.priority }.take(limit)

    fun getFiltered(filter: ReviewQueueFilter) = queue.values.filter { item ->
        (filter.status == null || item.status == filter.status) && (filter.minPriority == null || item.priority >= filter.minPriority) &&
        (filter.maxPriority == null || item.priority <= filter.maxPriority) && (filter.categoryId == null || item.suggestedCategory.categoryId == filter.categoryId) &&
        (filter.merchantPattern == null || item.features.merchantNormalized.contains(filter.merchantPattern, ignoreCase = true)) &&
        (filter.enqueuedAfter == null || item.enqueuedAt >= filter.enqueuedAfter) && (filter.enqueuedBefore == null || item.enqueuedAt <= filter.enqueuedBefore)
    }.sortedByDescending { it.priority }

    fun recordDecision(transactionId: String, decision: ReviewDecision): Boolean {
        val item = queue[transactionId] ?: return false
        queue[transactionId] = item.copy(status = when (decision) { is ReviewDecision.Accept, is ReviewDecision.CreateRule -> ReviewStatus.ACCEPTED
            is ReviewDecision.Reject -> ReviewStatus.REJECTED; is ReviewDecision.Defer -> ReviewStatus.DEFERRED }, reviewedAt = Clock.System.now())
        return true
    }

    fun remove(transactionId: String) = queue.remove(transactionId)
    fun clear() { queue.clear() }
    fun getItem(transactionId: String) = queue[transactionId]
    fun contains(transactionId: String) = transactionId in queue
    val pendingCount: Int get() = queue.values.count { it.status == ReviewStatus.PENDING }
    val pendingItems: List<ReviewQueueItem> get() = queue.values.filter { it.status == ReviewStatus.PENDING }

    fun getStats(): ReviewQueueStats {
        val items = queue.values.toList()
        return ReviewQueueStats(items.size, items.count { it.status == ReviewStatus.PENDING }, items.count { it.status == ReviewStatus.ACCEPTED },
            items.count { it.status == ReviewStatus.REJECTED }, items.count { it.status == ReviewStatus.DEFERRED },
            if (items.isEmpty()) 0f else items.map { it.priority }.average().toFloat(),
            items.filter { it.status == ReviewStatus.PENDING }.minByOrNull { it.enqueuedAt }?.enqueuedAt)
    }
}

data class ReviewQueueItem(
    val transactionId: String, val features: TransactionFeatures, val suggestedCategory: ClassificationResult,
    val alternatives: List<CategoryScore>, val priority: Float, val enqueuedAt: Instant,
    val status: ReviewStatus = ReviewStatus.PENDING, val reviewedAt: Instant? = null, val metadata: ReviewItemMetadata = ReviewItemMetadata()
) {
    val confidence: Float get() = suggestedCategory.confidence
    val categoryId: String get() = suggestedCategory.categoryId
    val hasAlternatives: Boolean get() = alternatives.isNotEmpty()
    val waitTimeMs: Long get() = Clock.System.now().toEpochMilliseconds() - enqueuedAt.toEpochMilliseconds()
}

data class ReviewItemMetadata(val accountId: String? = null, val importBatchId: String? = null, val source: String? = null, val tags: Set<String> = emptySet())
enum class ReviewStatus { PENDING, ACCEPTED, REJECTED, DEFERRED }

sealed class ReviewDecision {
    abstract val reviewerId: String?; abstract val timestamp: Instant
    data class Accept(override val reviewerId: String? = null, override val timestamp: Instant = Clock.System.now()) : ReviewDecision()
    data class Reject(val newCategoryId: String, val reason: String? = null, override val reviewerId: String? = null, override val timestamp: Instant = Clock.System.now()) : ReviewDecision()
    data class Defer(val reason: String? = null, override val reviewerId: String? = null, override val timestamp: Instant = Clock.System.now()) : ReviewDecision()
    data class CreateRule(val categoryId: String, val rulePattern: String, val ruleType: String = "merchant", override val reviewerId: String? = null, override val timestamp: Instant = Clock.System.now()) : ReviewDecision()
}

data class ReviewQueueFilter(val status: ReviewStatus? = null, val minPriority: Float? = null, val maxPriority: Float? = null,
    val categoryId: String? = null, val merchantPattern: String? = null, val enqueuedAfter: Instant? = null, val enqueuedBefore: Instant? = null) {
    companion object { fun pending() = ReviewQueueFilter(status = ReviewStatus.PENDING); fun highPriority(minPriority: Float = 0.8f) = ReviewQueueFilter(ReviewStatus.PENDING, minPriority) }
}

data class ReviewQueueStats(val totalItems: Int, val pendingCount: Int, val acceptedCount: Int, val rejectedCount: Int, val deferredCount: Int, val averagePriority: Float, val oldestPendingItem: Instant?) {
    val processedCount: Int get() = acceptedCount + rejectedCount
    val acceptanceRate: Float get() = if (processedCount > 0) acceptedCount.toFloat() / processedCount else 0f
}

data class ReviewEnqueueRequest(val transactionId: String, val features: TransactionFeatures, val pipelineResult: PipelineResult, val metadata: ReviewItemMetadata = ReviewItemMetadata())

interface ReviewPriorityScorer { fun calculatePriority(features: TransactionFeatures, result: PipelineResult): Float }

class DefaultReviewPriorityScorer : ReviewPriorityScorer {
    override fun calculatePriority(features: TransactionFeatures, result: PipelineResult): Float {
        var p = (1f - result.confidence) * 0.4f
        p += when { kotlin.math.abs(features.amountCents) > 100000 -> 0.3f; kotlin.math.abs(features.amountCents) > 50000 -> 0.2f; kotlin.math.abs(features.amountCents) > 10000 -> 0.1f; else -> 0f }
        if (features.isDebit) p += 0.1f
        if (result.classification.alternatives.isNotEmpty()) { val topAlt = result.classification.alternatives.maxOfOrNull { it.score } ?: 0f; if (topAlt > result.confidence * 0.8f) p += 0.2f }
        return p.coerceIn(0f, 1f)
    }
}

class FrequencyAwarePriorityScorer(private val merchantFrequencyProvider: (String) -> Int) : ReviewPriorityScorer {
    private val baseScorer = DefaultReviewPriorityScorer()
    override fun calculatePriority(features: TransactionFeatures, result: PipelineResult): Float {
        var p = baseScorer.calculatePriority(features, result)
        val freq = merchantFrequencyProvider(features.merchantNormalized)
        if (freq == 0) p += 0.15f else if (freq < 3) p += 0.05f
        return p.coerceIn(0f, 1f)
    }
}
