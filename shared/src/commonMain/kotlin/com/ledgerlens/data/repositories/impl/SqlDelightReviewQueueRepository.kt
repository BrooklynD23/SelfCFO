package com.ledgerlens.data.repositories.impl

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.ledgerlens.categorization.pipeline.DefaultReviewPriorityScorer
import com.ledgerlens.categorization.pipeline.ReviewDecision
import com.ledgerlens.categorization.pipeline.ReviewEnqueueRequest
import com.ledgerlens.categorization.pipeline.ReviewItemMetadata
import com.ledgerlens.categorization.pipeline.ReviewPriorityScorer
import com.ledgerlens.categorization.pipeline.ReviewQueueFilter
import com.ledgerlens.categorization.pipeline.ReviewQueueItem
import com.ledgerlens.categorization.pipeline.ReviewQueueStats
import com.ledgerlens.categorization.pipeline.ReviewStatus
import com.ledgerlens.data.mappers.ReviewQueueMapper
import com.ledgerlens.data.repositories.ReviewQueueRepository
import com.ledgerlens.db.LedgerLensDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * SQLDelight implementation of ReviewQueueRepository.
 */
class SqlDelightReviewQueueRepository(
    private val database: LedgerLensDatabase,
    private val priorityScorer: ReviewPriorityScorer = DefaultReviewPriorityScorer(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ReviewQueueRepository {

    private val reviewQueueQueries = database.reviewQueueQueries

    override suspend fun enqueue(
        transactionId: String,
        features: com.ledgerlens.categorization.pipeline.TransactionFeatures,
        pipelineResult: com.ledgerlens.categorization.pipeline.PipelineResult,
        metadata: ReviewItemMetadata
    ): ReviewQueueItem = withContext(dispatcher) {
        val priority = priorityScorer.calculatePriority(features, pipelineResult)
        val item = ReviewQueueItem(
            transactionId = transactionId,
            features = features,
            suggestedCategory = pipelineResult.classification,
            alternatives = pipelineResult.classification.alternatives,
            priority = priority,
            enqueuedAt = Clock.System.now(),
            status = ReviewStatus.PENDING,
            reviewedAt = null,
            metadata = metadata
        )

        val params = ReviewQueueMapper.toDbParams(item)
        reviewQueueQueries.insert(
            transaction_id = params.transactionId,
            features_json = params.featuresJson,
            suggested_category_id = params.suggestedCategoryId,
            suggested_confidence = params.suggestedConfidence,
            explanation_classifier = params.explanationClassifier,
            explanation_reason = params.explanationReason,
            alternatives_json = params.alternativesJson,
            priority = params.priority,
            status = params.status,
            enqueued_at = params.enqueuedAt,
            reviewed_at = params.reviewedAt,
            metadata_json = params.metadataJson
        )

        item
    }

    override suspend fun enqueueBatch(items: List<ReviewEnqueueRequest>): List<ReviewQueueItem> = withContext(dispatcher) {
        items.map { request ->
            enqueue(request.transactionId, request.features, request.pipelineResult, request.metadata)
        }
    }

    override fun getNextForReview(): Flow<ReviewQueueItem?> {
        return reviewQueueQueries.selectNextForReview()
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it?.let(ReviewQueueMapper::toDomain) }
    }

    override fun getForBatchReview(limit: Int): Flow<List<ReviewQueueItem>> {
        return reviewQueueQueries.selectBatchForReview(limit.toLong())
            .asFlow()
            .mapToList(dispatcher)
            .map { list -> list.map(ReviewQueueMapper::toDomain) }
    }

    override fun getFiltered(filter: ReviewQueueFilter): Flow<List<ReviewQueueItem>> {
        val statusStr = filter.status?.let {
            when (it) {
                ReviewStatus.PENDING -> "PENDING"
                ReviewStatus.ACCEPTED -> "ACCEPTED"
                ReviewStatus.REJECTED -> "REJECTED"
                ReviewStatus.DEFERRED -> "DEFERRED"
            }
        }

        // SQLDelight doesn't support optional-parameter WHERE clauses well, so we fetch a base
        // query and apply additional filtering in memory.
        val baseQuery = if (statusStr != null) {
            reviewQueueQueries.selectByStatus(statusStr)
        } else {
            reviewQueueQueries.selectAll()
        }

        return baseQuery
            .asFlow()
            .mapToList(dispatcher)
            .map { list ->
                list.map(ReviewQueueMapper::toDomain)
                    .filter { item ->
                        val okMin = filter.minPriority == null || item.priority >= filter.minPriority
                        val okMax = filter.maxPriority == null || item.priority <= filter.maxPriority
                        val okCategory = filter.categoryId == null || item.suggestedCategory.categoryId == filter.categoryId
                        val okAfter = filter.enqueuedAfter == null || item.enqueuedAt >= filter.enqueuedAfter
                        val okBefore = filter.enqueuedBefore == null || item.enqueuedAt <= filter.enqueuedBefore
                        val okMerchant = filter.merchantPattern == null ||
                            item.features.merchantNormalized.contains(filter.merchantPattern, ignoreCase = true)

                        okMin && okMax && okCategory && okAfter && okBefore && okMerchant
                    }
            }
    }

    override suspend fun recordDecision(transactionId: String, decision: ReviewDecision): Boolean = withContext(dispatcher) {
        // Check if item exists first
        val exists = reviewQueueQueries.selectByTransactionId(transaction_id = transactionId)
            .executeAsOneOrNull() != null
        if (!exists) return@withContext false

        val status = when (decision) {
            is ReviewDecision.Accept, is ReviewDecision.CreateRule -> "ACCEPTED"
            is ReviewDecision.Reject -> "REJECTED"
            is ReviewDecision.Defer -> "DEFERRED"
        }
        val reviewedAt = Clock.System.now().toEpochMilliseconds()

        reviewQueueQueries.updateStatus(
            status = status,
            reviewed_at = reviewedAt,
            transaction_id = transactionId
        )

        true
    }

    override suspend fun remove(transactionId: String): Boolean = withContext(dispatcher) {
        // Check if item exists first
        val exists = reviewQueueQueries.selectByTransactionId(transaction_id = transactionId)
            .executeAsOneOrNull() != null
        if (!exists) return@withContext false

        reviewQueueQueries.delete(transaction_id = transactionId)
        true
    }

    override suspend fun clear() = withContext(dispatcher) {
        reviewQueueQueries.clear()
    }

    override fun getItem(transactionId: String): Flow<ReviewQueueItem?> {
        return reviewQueueQueries.selectByTransactionId(transaction_id = transactionId)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it?.let(ReviewQueueMapper::toDomain) }
    }

    override suspend fun contains(transactionId: String): Boolean = withContext(dispatcher) {
        reviewQueueQueries.selectByTransactionId(transaction_id = transactionId)
            .executeAsOneOrNull() != null
    }

    override val pendingCount: Flow<Int>
        get() = reviewQueueQueries.countPending()
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it?.toInt() ?: 0 }

    override val pendingItems: Flow<List<ReviewQueueItem>>
        get() = reviewQueueQueries.selectPending()
            .asFlow()
            .mapToList(dispatcher)
            .map { list -> list.map(ReviewQueueMapper::toDomain) }

    override fun getStats(): Flow<ReviewQueueStats> {
        return reviewQueueQueries.stats()
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { stats ->
                if (stats == null) {
                    ReviewQueueStats(
                        totalItems = 0,
                        pendingCount = 0,
                        acceptedCount = 0,
                        rejectedCount = 0,
                        deferredCount = 0,
                        averagePriority = 0f,
                        oldestPendingItem = null
                    )
                } else {
                    ReviewQueueStats(
                        totalItems = stats.total_items?.toInt() ?: 0,
                        pendingCount = stats.pending_count?.toInt() ?: 0,
                        acceptedCount = stats.accepted_count?.toInt() ?: 0,
                        rejectedCount = stats.rejected_count?.toInt() ?: 0,
                        deferredCount = stats.deferred_count?.toInt() ?: 0,
                        averagePriority = stats.avg_priority?.toFloat() ?: 0f,
                        oldestPendingItem = stats.oldest_pending_at?.let {
                            kotlinx.datetime.Instant.fromEpochMilliseconds(it)
                        }
                    )
                }
            }
    }
}
