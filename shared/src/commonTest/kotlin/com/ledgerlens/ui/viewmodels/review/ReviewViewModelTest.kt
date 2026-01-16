package com.ledgerlens.ui.viewmodels.review

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: ReviewViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ReviewViewModel()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has default filter PENDING`() = runTest {
        advanceUntilIdle()
        assertEquals(ReviewFilter.PENDING, viewModel.uiState.value.currentFilter)
    }

    @Test
    fun `loadReviewItems populates items list`() = runTest {
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.items.isNotEmpty())
    }

    @Test
    fun `setFilter updates currentFilter`() = runTest {
        advanceUntilIdle()

        viewModel.setFilter(ReviewFilter.LOW_CONFIDENCE)
        assertEquals(ReviewFilter.LOW_CONFIDENCE, viewModel.uiState.value.currentFilter)

        viewModel.setFilter(ReviewFilter.ALL)
        assertEquals(ReviewFilter.ALL, viewModel.uiState.value.currentFilter)
    }

    @Test
    fun `setFilter filters items correctly for LOW_CONFIDENCE`() = runTest {
        advanceUntilIdle()

        viewModel.setFilter(ReviewFilter.LOW_CONFIDENCE)
        val filteredItems = viewModel.uiState.value.filteredItems

        filteredItems.forEach { item ->
            assertTrue(item.confidence < 0.5f)
            assertEquals(ReviewItemStatus.PENDING, item.status)
        }
    }

    @Test
    fun `setFilter filters items correctly for DUPLICATES`() = runTest {
        advanceUntilIdle()

        viewModel.setFilter(ReviewFilter.DUPLICATES)
        val filteredItems = viewModel.uiState.value.filteredItems

        filteredItems.forEach { item ->
            assertEquals(ReviewType.POSSIBLE_DUPLICATE, item.reviewType)
            assertEquals(ReviewItemStatus.PENDING, item.status)
        }
    }

    @Test
    fun `selectItem updates selectedItem`() = runTest {
        advanceUntilIdle()

        val item = viewModel.uiState.value.items.first()
        viewModel.selectItem(item)

        assertEquals(item, viewModel.selectedItem.value)
    }

    @Test
    fun `clearSelection sets selectedItem to null`() = runTest {
        advanceUntilIdle()

        val item = viewModel.uiState.value.items.first()
        viewModel.selectItem(item)
        assertNotNull(viewModel.selectedItem.value)

        viewModel.clearSelection()
        assertNull(viewModel.selectedItem.value)
    }

    @Test
    fun `acceptSuggestion updates item status to ACCEPTED`() = runTest {
        advanceUntilIdle()

        val item = viewModel.uiState.value.items.first()
        viewModel.acceptSuggestion(item.id)
        advanceUntilIdle()

        val updatedItem = viewModel.uiState.value.items.find { it.id == item.id }
        assertNotNull(updatedItem)
        assertEquals(ReviewItemStatus.ACCEPTED, updatedItem.status)
        assertNotNull(updatedItem.reviewedAt)
    }

    @Test
    fun `rejectSuggestion updates item status to REJECTED with new category`() = runTest {
        advanceUntilIdle()

        val item = viewModel.uiState.value.items.first()
        val newCategoryId = "different_category"

        viewModel.rejectSuggestion(item.id, newCategoryId)
        advanceUntilIdle()

        val updatedItem = viewModel.uiState.value.items.find { it.id == item.id }
        assertNotNull(updatedItem)
        assertEquals(ReviewItemStatus.REJECTED, updatedItem.status)
        assertEquals(newCategoryId, updatedItem.selectedCategoryId)
        assertNotNull(updatedItem.reviewedAt)
    }

    @Test
    fun `deferItem updates item status to DEFERRED`() = runTest {
        advanceUntilIdle()

        val item = viewModel.uiState.value.items.first()
        viewModel.deferItem(item.id)
        advanceUntilIdle()

        val updatedItem = viewModel.uiState.value.items.find { it.id == item.id }
        assertNotNull(updatedItem)
        assertEquals(ReviewItemStatus.DEFERRED, updatedItem.status)
    }

    @Test
    fun `acceptAll marks all pending filtered items as accepted`() = runTest {
        advanceUntilIdle()

        viewModel.setFilter(ReviewFilter.PENDING)
        val initialPendingCount = viewModel.uiState.value.filteredItems.count {
            it.status == ReviewItemStatus.PENDING
        }
        assertTrue(initialPendingCount > 0)

        viewModel.acceptAll()
        advanceUntilIdle()

        val pendingAfter = viewModel.uiState.value.items.count {
            it.status == ReviewItemStatus.PENDING
        }
        assertEquals(0, pendingAfter)
    }

    @Test
    fun `dismissAll marks all pending filtered items as dismissed`() = runTest {
        advanceUntilIdle()

        viewModel.setFilter(ReviewFilter.PENDING)

        viewModel.dismissAll()
        advanceUntilIdle()

        val dismissedCount = viewModel.uiState.value.items.count {
            it.status == ReviewItemStatus.DISMISSED
        }
        assertTrue(dismissedCount > 0)
    }

    @Test
    fun `stats are calculated correctly after accepting items`() = runTest {
        advanceUntilIdle()

        val initialStats = viewModel.uiState.value.stats
        val initialPending = initialStats.pendingCount

        val item = viewModel.uiState.value.items.first { it.status == ReviewItemStatus.PENDING }
        viewModel.acceptSuggestion(item.id)
        advanceUntilIdle()

        val newStats = viewModel.uiState.value.stats
        assertEquals(initialPending - 1, newStats.pendingCount)
        assertEquals(initialStats.acceptedCount + 1, newStats.acceptedCount)
    }

    @Test
    fun `ReviewStats completionPercent calculates correctly`() {
        val stats = ReviewStats(
            totalItems = 100,
            pendingCount = 60,
            acceptedCount = 30,
            rejectedCount = 10,
            lowConfidenceCount = 20,
            duplicateCount = 5,
            uncategorizedCount = 10
        )

        assertEquals(40, stats.completionPercent) // (30 + 10) / 100 * 100
    }

    @Test
    fun `ReviewStats processedCount sums accepted and rejected`() {
        val stats = ReviewStats(
            totalItems = 100,
            pendingCount = 60,
            acceptedCount = 25,
            rejectedCount = 15,
            lowConfidenceCount = 20,
            duplicateCount = 5,
            uncategorizedCount = 10
        )

        assertEquals(40, stats.processedCount)
    }

    @Test
    fun `ReviewItemUi formattedAmount formats negative amounts correctly`() {
        val item = createTestItem(amount = -4599)
        assertEquals("-$45.99", item.formattedAmount)
    }

    @Test
    fun `ReviewItemUi formattedAmount formats positive amounts correctly`() {
        val item = createTestItem(amount = 10050)
        assertEquals("+$100.50", item.formattedAmount)
    }

    @Test
    fun `ReviewItemUi formattedAmount handles cents less than 10`() {
        val item = createTestItem(amount = -1005)
        assertEquals("-$10.05", item.formattedAmount)
    }

    @Test
    fun `ReviewItemUi isLowConfidence returns true for confidence below 0_5`() {
        val lowConfItem = createTestItem(confidence = 0.4f)
        assertTrue(lowConfItem.isLowConfidence)

        val highConfItem = createTestItem(confidence = 0.6f)
        assertFalse(highConfItem.isLowConfidence)
    }

    @Test
    fun `ReviewItemUi hasSuggestion returns true when suggestedCategoryId is not null`() {
        val withSuggestion = createTestItem(suggestedCategoryId = "category_1")
        assertTrue(withSuggestion.hasSuggestion)

        val withoutSuggestion = createTestItem(suggestedCategoryId = null)
        assertFalse(withoutSuggestion.hasSuggestion)
    }

    @Test
    fun `ReviewItemUi isPossibleDuplicate returns true when duplicateOf is not null`() {
        val duplicate = createTestItem(
            duplicateOf = DuplicateInfo(
                transactionId = "dup_1",
                date = "2024-01-01",
                amount = -1000,
                similarity = 0.95f
            )
        )
        assertTrue(duplicate.isPossibleDuplicate)

        val notDuplicate = createTestItem(duplicateOf = null)
        assertFalse(notDuplicate.isPossibleDuplicate)
    }

    @Test
    fun `CategoryAlternative confidencePercent calculates correctly`() {
        val alt = CategoryAlternative(
            categoryId = "cat_1",
            categoryName = "Test Category",
            confidence = 0.75f
        )
        assertEquals(75, alt.confidencePercent)
    }

    @Test
    fun `DuplicateInfo similarityPercent calculates correctly`() {
        val info = DuplicateInfo(
            transactionId = "txn_1",
            date = "2024-01-01",
            amount = -1000,
            similarity = 0.92f
        )
        assertEquals(92, info.similarityPercent)
    }

    @Test
    fun `ReviewFilter displayName values are correct`() {
        assertEquals("All", ReviewFilter.ALL.displayName)
        assertEquals("Pending", ReviewFilter.PENDING.displayName)
        assertEquals("Low Confidence", ReviewFilter.LOW_CONFIDENCE.displayName)
        assertEquals("Possible Duplicates", ReviewFilter.DUPLICATES.displayName)
        assertEquals("Uncategorized", ReviewFilter.UNCATEGORIZED.displayName)
    }

    private fun createTestItem(
        id: String = "test_id",
        amount: Long = -1000,
        confidence: Float = 0.5f,
        suggestedCategoryId: String? = "category_1",
        duplicateOf: DuplicateInfo? = null
    ) = ReviewItemUi(
        id = id,
        transactionId = "txn_$id",
        merchantName = "Test Merchant",
        normalizedMerchant = "Test",
        description = "Test description",
        amount = amount,
        date = "2024-01-15",
        suggestedCategoryId = suggestedCategoryId,
        suggestedCategoryName = if (suggestedCategoryId != null) "Test Category" else null,
        confidence = confidence,
        alternatives = emptyList(),
        explanation = "Test explanation",
        reviewType = ReviewType.LOW_CONFIDENCE,
        duplicateOf = duplicateOf
    )
}
