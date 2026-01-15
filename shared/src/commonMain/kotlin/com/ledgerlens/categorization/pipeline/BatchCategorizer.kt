package com.ledgerlens.categorization.pipeline

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.TimeSource

class BatchCategorizer(private val pipeline: CategorizationPipeline, private val config: BatchCategorizerConfig = BatchCategorizerConfig()) {
    fun processBatch(transactions: List<TransactionInput>, progressCallback: BatchProgressCallback? = null): BatchResult {
        val start = TimeSource.Monotonic.markNow()
        val results = mutableListOf<PipelineResult>()
        val errors = mutableMapOf<String, Throwable>()
        progressCallback?.onBatchStarted(transactions.size)
        transactions.forEachIndexed { i, input ->
            try { val r = pipeline.categorize(input.features, input.transactionId); results.add(r); progressCallback?.onTransactionProcessed(i + 1, transactions.size, r) }
            catch (e: Exception) { errors[input.transactionId] = e; progressCallback?.onTransactionError(input.transactionId, e) }
        }
        val result = BatchResult(results, errors, start.elapsedNow().inWholeMilliseconds, transactions.size)
        progressCallback?.onBatchCompleted(result)
        return result
    }

    suspend fun processBatchAsync(transactions: List<TransactionInput>, progressCallback: BatchProgressCallback? = null): BatchResult = coroutineScope {
        val start = TimeSource.Monotonic.markNow()
        progressCallback?.onBatchStarted(transactions.size)
        val results = mutableListOf<PipelineResult>(); val errors = mutableMapOf<String, Throwable>(); var count = 0
        transactions.chunked(config.chunkSize).forEach { chunk ->
            chunk.map { input -> async(Dispatchers.Default) { try { Result.success(pipeline.categorize(input.features, input.transactionId)) } catch (e: Exception) { Result.failure<PipelineResult>(TransactionProcessingException(input.transactionId, e)) } } }.awaitAll().forEach { r ->
                r.fold({ results.add(it); count++; progressCallback?.onTransactionProcessed(count, transactions.size, it) },
                    { if (it is TransactionProcessingException) { errors[it.transactionId] = it.cause ?: it; progressCallback?.onTransactionError(it.transactionId, it.cause ?: it) } })
            }
        }
        val result = BatchResult(results, errors, start.elapsedNow().inWholeMilliseconds, transactions.size)
        progressCallback?.onBatchCompleted(result)
        result
    }

    fun processFlow(transactions: Flow<TransactionInput>): Flow<PipelineResult> = flow { transactions.collect { emit(pipeline.categorize(it.features, it.transactionId)) } }
}

data class BatchCategorizerConfig(val chunkSize: Int = 20, val maxRetries: Int = 2, val retryDelayMs: Long = 100L, val timeoutMs: Long = 30_000L) {
    init { require(chunkSize > 0); require(maxRetries >= 0); require(timeoutMs > 0) }
}

data class BatchResult(val results: List<PipelineResult>, val errors: Map<String, Throwable>, val totalTimeMs: Long, val batchSize: Int) {
    val successCount: Int get() = results.size
    val errorCount: Int get() = errors.size
    val successRate: Float get() = if (batchSize > 0) successCount.toFloat() / batchSize else 0f
    val autoAppliedCount: Int get() = results.count { it.wasAutoApplied }
    val needsReviewCount: Int get() = results.count { it.needsReview }
    val averageConfidence: Float get() = if (results.isEmpty()) 0f else results.map { it.confidence }.average().toFloat()
    val averageProcessingTimeMs: Double get() = if (results.isEmpty()) 0.0 else results.map { it.processingTimeMs }.average()
    fun groupByAction() = results.groupBy { it.action }
    fun getReviewRequired() = results.filter { it.needsReview }
    fun getAutoApplied() = results.filter { it.wasAutoApplied }
    fun summary() = BatchSummary(batchSize, successCount, errorCount, autoAppliedCount, needsReviewCount, averageConfidence, totalTimeMs, averageProcessingTimeMs)
}

data class BatchSummary(val totalProcessed: Int, val successful: Int, val failed: Int, val autoApplied: Int, val needsReview: Int, val averageConfidence: Float, val totalTimeMs: Long, val averageTimePerTransactionMs: Double)

interface BatchProgressCallback {
    fun onBatchStarted(totalCount: Int) {}
    fun onTransactionProcessed(current: Int, total: Int, result: PipelineResult) {}
    fun onTransactionError(transactionId: String, error: Throwable) {}
    fun onBatchCompleted(result: BatchResult) {}
}

class SimpleProgressCallback(private val onProgress: (Float, String) -> Unit) : BatchProgressCallback {
    override fun onBatchStarted(totalCount: Int) = onProgress(0f, "Starting batch of $totalCount")
    override fun onTransactionProcessed(current: Int, total: Int, result: PipelineResult) = onProgress(current.toFloat() / total, "Processed $current of $total")
    override fun onBatchCompleted(result: BatchResult) = onProgress(1f, "Completed: ${result.successCount} successful, ${result.errorCount} errors")
}

class TransactionProcessingException(val transactionId: String, override val cause: Throwable?) : Exception("Failed to process $transactionId", cause)

class BatchCategorizationBuilder {
    private val transactions = mutableListOf<TransactionInput>()
    private var progressCallback: BatchProgressCallback? = null
    private var config = BatchCategorizerConfig()
    fun addTransaction(transactionId: String, features: TransactionFeatures) = apply { transactions.add(TransactionInput(transactionId, features)) }
    fun addTransactions(inputs: List<TransactionInput>) = apply { transactions.addAll(inputs) }
    fun withProgressCallback(callback: BatchProgressCallback) = apply { progressCallback = callback }
    fun withConfig(config: BatchCategorizerConfig) = apply { this.config = config }
    fun build() = BatchCategorizationJob(transactions.toList(), progressCallback, config)
}

data class BatchCategorizationJob(val transactions: List<TransactionInput>, val progressCallback: BatchProgressCallback?, val config: BatchCategorizerConfig) {
    fun execute(categorizer: BatchCategorizer) = categorizer.processBatch(transactions, progressCallback)
    suspend fun executeAsync(categorizer: BatchCategorizer) = categorizer.processBatchAsync(transactions, progressCallback)
}
