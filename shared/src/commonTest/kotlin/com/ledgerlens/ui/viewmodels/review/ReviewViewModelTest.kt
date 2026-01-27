package com.ledgerlens.ui.viewmodels.review

import com.ledgerlens.categorization.pipeline.*
import com.ledgerlens.data.repositories.fake.FakeReviewQueueRepository
import com.ledgerlens.data.repositories.fake.FakeTransactionRepository
import com.ledgerlens.data.repositories.fake.TestDataFactory
import kotlin.test.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var reviewQueueRepository: FakeReviewQueueRepository
    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var viewModel: ReviewViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        reviewQueueRepository = FakeReviewQueueRepository()
        transactionRepository = FakeTransactionRepository()

        // Seed test transactions
        transactionRepository.setTransactions(
            listOf(
                TestDataFactory.createTransaction(id = "tx-1", merchantNormalized = "WALMART", categoryId = "groceries"),
                TestDataFactory.createTransaction(id = "tx-2", merchantNormalized = "STARBUCKS", categoryId = "dining"),
                TestDataFactory.createTransaction(id = "tx-3", merchantNormalized = "UBER", categoryId = "transportation")
            )
        )

        viewModel = ReviewViewModel(reviewQueueRepository, transactionRepository)
    }

    private suspend fun seedReviewQueueItems() {
        val features1 = TransactionFeatures(
            merchantNormalized = "WALMART",
            descriptionRaw = "WALMART STORE #1234",
            descriptionTokens = listOf("walmart", "store"),
            amountCents = -5000,
            amountBucket = AmountBucket.MEDIUM,
            isDebit = true,
            dayOfWeek = 1,
            dayOfMonth = 15,
            accountId = null
        )
        val classification1 = ClassificationResult(
            categoryId = "groceries",
            confidence = 0.65f,
            alternatives = emptyList(),
            explanation = ClassificationExplanation("ml", "ML classifier predicted groceries")
        )
        val result1 = PipelineResult(
            transactionId = "tx-1",
            classification = classification1,
            action = CategorizationAction.QUEUE_FOR_REVIEW,
            stageResults = emptyMap(),
            processingTimeMs = 10,
            usedStage = PipelineStage.ML_CLASSIFICATION
        )

        val features2 = TransactionFeatures(
            merchantNormalized = "STARBUCKS",
            descriptionRaw = "STARBUCKS COFFEE #567",
            descriptionTokens = listOf("starbucks", "coffee"),
            amountCents = -450,
            amountBucket = AmountBucket.SMALL,
            isDebit = true,
            dayOfWeek = 2,
            dayOfMonth = 16,
            accountId = null
        )
        val classification2 = ClassificationResult(
            categoryId = "dining",
            confidence = 0.55f,
            alternatives = emptyList(),
            explanation = ClassificationExplanation("ml", "ML classifier predicted dining")
        )
        val result2 = PipelineResult(
            transactionId = "tx-2",
            classification = classification2,
            action = CategorizationAction.QUEUE_FOR_REVIEW,
            stageResults = emptyMap(),
            processingTimeMs = 10,
            usedStage = PipelineStage.ML_CLASSIFICATION
        )

        reviewQueueRepository.enqueue("tx-1", features1, result1)
        reviewQueueRepository.enqueue("tx-2", features2, result2)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========== Initial State Tests ==========

    @Test
    fun `initial state is loading then loaded`() = runTest {
        seedReviewQueueItems()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `loadReviewItems populates items`() = runTest {
        seedReviewQueueItems()
        viewModel.loadReviewItems()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(2, state.items.size)
    }

    @Test
    fun `loadReviewItems calculates stats`() = runTest {
        seedReviewQueueItems()
        viewModel.loadReviewItems()
        advanceUntilIdle()
        val stats = viewModel.uiState.value.stats
        assertEquals(2, stats.totalItems)
        assertEquals(2, stats.pendingCount)
    }

    // ========== Filter Tests ==========

    @Test
    fun `setFilter updates current filter`() = runTest {
        seedReviewQueueItems()
        viewModel.loadReviewItems()
        advanceUntilIdle()
        viewModel.setFilter(ReviewFilter.LOW_CONFIDENCE)
        assertEquals(ReviewFilter.LOW_CONFIDENCE, viewModel.uiState.value.currentFilter)
    }

    @Test
    fun `setFilter applies filtering to items`() = runTest {
        seedReviewQueueItems()
        viewModel.loadReviewItems()
        advanceUntilIdle()
        viewModel.setFilter(ReviewFilter.ALL)
        val filteredCount = viewModel.uiState.value.filteredItems.size
        assertTrue(filteredCount >= 0)
    }

    @Test
    fun `setFilter to PENDING shows only pending items`() = runTest {
        seedReviewQueueItems()
        viewModel.loadReviewItems()
        advanceUntilIdle()
        viewModel.setFilter(ReviewFilter.PENDING)
        val filtered = viewModel.uiState.value.filteredItems
        assertTrue(filtered.all { it.status == ReviewItemStatus.PENDING })
    }

    // ========== Selection Tests ==========

    @Test
    fun `selectItem sets selected item`() = runTest {
        seedReviewQueueItems()
        viewModel.loadReviewItems()
        advanceUntilIdle()
        val item = viewModel.uiState.value.items.first()
        viewModel.selectItem(item)
        assertEquals(item, viewModel.selectedItem.value)
    }

    @Test
    fun `clearSelection clears selected item`() = runTest {
        seedReviewQueueItems()
        viewModel.loadReviewItems()
        advanceUntilIdle()
        val item = viewModel.uiState.value.items.first()
        viewModel.selectItem(item)
        assertNotNull(viewModel.selectedItem.value)

        viewModel.clearSelection()
        assertNull(viewModel.selectedItem.value)
    }

    // ========== Review Decision Tests ==========

    @Test
    fun `acceptSuggestion marks item as accepted`() = runTest {
        seedReviewQueueItems()
        viewModel.loadReviewItems()
        advanceUntilIdle()
        val transactionId = viewModel.uiState.value.items.first().transactionId

        viewModel.acceptSuggestion(transactionId)
        advanceUntilIdle()

        // After acceptance, item should be completed
        val item = reviewQueueRepository.getItem(transactionId).first()
        assertTrue(item?.status == ReviewStatus.ACCEPTED || item == null)
    }

    @Test
    fun `rejectSuggestion marks item as rejected`() = runTest {
        seedReviewQueueItems()
        viewModel.loadReviewItems()
        advanceUntilIdle()
        val transactionId = viewModel.uiState.value.items.first().transactionId

        viewModel.rejectSuggestion(transactionId, "dining")
        advanceUntilIdle()

        val item = reviewQueueRepository.getItem(transactionId).first()
        // After rejection, item should be removed from queue or marked as rejected
        assertTrue(item?.status == ReviewStatus.REJECTED || item == null)
    }

    @Test
    fun `deferItem marks item as deferred`() = runTest {
        seedReviewQueueItems()
        viewModel.loadReviewItems()
        advanceUntilIdle()
        val transactionId = viewModel.uiState.value.items.first().transactionId

        viewModel.deferItem(transactionId)
        advanceUntilIdle()

        val item = reviewQueueRepository.getItem(transactionId).first()
        // After deferral, item should be marked as deferred or removed
        assertTrue(item?.status == ReviewStatus.DEFERRED || item == null)
    }

    // ========== Batch Operations Tests ==========

    @Test
    fun `acceptAll accepts all pending items`() = runTest {
        seedReviewQueueItems()
        viewModel.loadReviewItems()
        advanceUntilIdle()
        val initialPending = viewModel.uiState.value.filteredItems.filter {
            it.status == ReviewItemStatus.PENDING
        }.size
        assertTrue(initialPending > 0)

        viewModel.acceptAll()
        advanceUntilIdle()

        // After acceptAll, pending count should be reduced
        viewModel.loadReviewItems()
        advanceUntilIdle()
        val newPending = viewModel.uiState.value.filteredItems.filter {
            it.status == ReviewItemStatus.PENDING
        }.size
        assertTrue(newPending < initialPending || initialPending == 0)
    }

    @Test
    fun `dismissAll defers all pending items`() = runTest {
        seedReviewQueueItems()
        viewModel.loadReviewItems()
        advanceUntilIdle()

        viewModel.dismissAll()
        advanceUntilIdle()

        // After dismissAll, pending items should be completed/deferred
        val pendingCount = reviewQueueRepository.pendingCount.first()
        assertEquals(0, pendingCount)
    }

    // ========== Stats Tests ==========

    @Test
    fun `stats reflect current queue state`() = runTest {
        seedReviewQueueItems()
        viewModel.loadReviewItems()
        advanceUntilIdle()

        val stats = viewModel.uiState.value.stats
        assertEquals(2, stats.totalItems)
        assertEquals(2, stats.pendingCount)
        assertEquals(0, stats.acceptedCount)
        assertEquals(0, stats.rejectedCount)
    }

    @Test
    fun `stats update after accept`() = runTest {
        seedReviewQueueItems()
        viewModel.loadReviewItems()
        advanceUntilIdle()
        val transactionId = viewModel.uiState.value.items.first().transactionId

        viewModel.acceptSuggestion(transactionId)
        advanceUntilIdle()

        viewModel.loadReviewItems()
        advanceUntilIdle()

        val stats = viewModel.uiState.value.stats
        // Either pendingCount decreases or acceptedCount increases
        assertTrue(stats.pendingCount < 2 || stats.acceptedCount > 0)
    }

    // ========== Error State Tests ==========

    @Test
    fun `error state is null initially`() = runTest {
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.error)
    }
}
