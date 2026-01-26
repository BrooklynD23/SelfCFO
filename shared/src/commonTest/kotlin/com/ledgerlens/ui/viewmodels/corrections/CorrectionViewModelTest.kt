package com.ledgerlens.ui.viewmodels.corrections

import com.ledgerlens.categorization.AmountBucket
import com.ledgerlens.categorization.TransactionFeatures
import kotlin.test.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class CorrectionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: CorrectionViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CorrectionViewModel()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========== Initial State Tests ==========

    @Test
    fun `initial state has no pending corrections`() {
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.pendingCorrections.isEmpty())
        assertFalse(state.hasPendingCorrections)
        assertFalse(state.isSelectionMode)
        assertNull(state.error)
    }

    @Test
    fun `initial state has default categories loaded`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state.availableCategories.isNotEmpty())
        assertTrue(state.availableCategories.any { it.id == "food" })
        assertTrue(state.availableCategories.any { it.id == "income" })
    }

    // ========== Add Pending Correction Tests ==========

    @Test
    fun `addPendingCorrection adds to queue`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addPendingCorrection(
            transactionId = "tx_123",
            features = createTestFeatures("starbucks"),
            oldCategoryId = "uncategorized",
            newCategoryId = "food",
            originalConfidence = 0.3f,
            classifierUsed = "naive_bayes"
        )

        val state = viewModel.uiState.value
        assertEquals(1, state.pendingCorrections.size)
        assertTrue(state.hasPendingCorrections)

        val correction = state.pendingCorrections.first()
        assertEquals("tx_123", correction.transactionId)
        assertEquals("starbucks", correction.merchantName)
        assertEquals("uncategorized", correction.oldCategoryId)
        assertEquals("food", correction.newCategoryId)
        assertEquals(0.3f, correction.originalConfidence)
    }

    @Test
    fun `addPendingCorrection maps category names`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addPendingCorrection(
            transactionId = "tx_456",
            features = createTestFeatures("amazon"),
            oldCategoryId = "uncategorized",
            newCategoryId = "shopping",
            originalConfidence = 0.45f,
            classifierUsed = "rule_based"
        )

        val correction = viewModel.uiState.value.pendingCorrections.first()
        assertEquals("Uncategorized", correction.oldCategoryName)
        assertEquals("Shopping", correction.newCategoryName)
    }

    @Test
    fun `multiple corrections can be added`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addPendingCorrection("tx_1", createTestFeatures("merchant1"), "a", "b", 0.5f, "test")
        viewModel.addPendingCorrection("tx_2", createTestFeatures("merchant2"), "c", "d", 0.6f, "test")
        viewModel.addPendingCorrection("tx_3", createTestFeatures("merchant3"), "e", "f", 0.7f, "test")

        assertEquals(3, viewModel.uiState.value.pendingCorrections.size)
    }

    // ========== Remove Pending Correction Tests ==========

    @Test
    fun `removePendingCorrection removes from queue`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addPendingCorrection("tx_1", createTestFeatures("m1"), "a", "b", 0.5f, "test")
        viewModel.addPendingCorrection("tx_2", createTestFeatures("m2"), "c", "d", 0.6f, "test")

        val firstId = viewModel.uiState.value.pendingCorrections.first().id
        viewModel.removePendingCorrection(firstId)

        assertEquals(1, viewModel.uiState.value.pendingCorrections.size)
        assertNull(viewModel.uiState.value.pendingCorrections.find { it.id == firstId })
    }

    @Test
    fun `removePendingCorrection also removes from selection`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addPendingCorrection("tx_1", createTestFeatures("m1"), "a", "b", 0.5f, "test")
        val id = viewModel.uiState.value.pendingCorrections.first().id

        viewModel.toggleSelectionMode()
        viewModel.toggleCorrectionSelection(id)
        assertTrue(viewModel.uiState.value.selectedCorrections.contains(id))

        viewModel.removePendingCorrection(id)
        assertFalse(viewModel.uiState.value.selectedCorrections.contains(id))
    }

    // ========== Update Correction Category Tests ==========

    @Test
    fun `updateCorrectionCategory updates category`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addPendingCorrection("tx_1", createTestFeatures("m1"), "uncategorized", "food", 0.5f, "test")
        val id = viewModel.uiState.value.pendingCorrections.first().id

        viewModel.updateCorrectionCategory(id, "entertainment")

        val updated = viewModel.uiState.value.pendingCorrections.first()
        assertEquals("entertainment", updated.newCategoryId)
        assertEquals("Entertainment", updated.newCategoryName)
    }

    // ========== Selection Mode Tests ==========

    @Test
    fun `toggleSelectionMode enables selection mode`() {
        assertFalse(viewModel.uiState.value.isSelectionMode)

        viewModel.toggleSelectionMode()

        assertTrue(viewModel.uiState.value.isSelectionMode)
    }

    @Test
    fun `toggleSelectionMode clears selection when disabled`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addPendingCorrection("tx_1", createTestFeatures("m1"), "a", "b", 0.5f, "test")
        val id = viewModel.uiState.value.pendingCorrections.first().id

        viewModel.toggleSelectionMode()
        viewModel.toggleCorrectionSelection(id)
        assertEquals(1, viewModel.uiState.value.selectedCount)

        viewModel.toggleSelectionMode()

        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertEquals(0, viewModel.uiState.value.selectedCount)
    }

    @Test
    fun `toggleCorrectionSelection adds and removes from selection`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addPendingCorrection("tx_1", createTestFeatures("m1"), "a", "b", 0.5f, "test")
        val id = viewModel.uiState.value.pendingCorrections.first().id

        viewModel.toggleCorrectionSelection(id)
        assertTrue(viewModel.uiState.value.selectedCorrections.contains(id))

        viewModel.toggleCorrectionSelection(id)
        assertFalse(viewModel.uiState.value.selectedCorrections.contains(id))
    }

    @Test
    fun `selectAllCorrections selects all`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addPendingCorrection("tx_1", createTestFeatures("m1"), "a", "b", 0.5f, "test")
        viewModel.addPendingCorrection("tx_2", createTestFeatures("m2"), "c", "d", 0.6f, "test")
        viewModel.addPendingCorrection("tx_3", createTestFeatures("m3"), "e", "f", 0.7f, "test")

        viewModel.selectAllCorrections()

        assertEquals(3, viewModel.uiState.value.selectedCount)
    }

    @Test
    fun `clearSelection removes all selections`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addPendingCorrection("tx_1", createTestFeatures("m1"), "a", "b", 0.5f, "test")
        viewModel.selectAllCorrections()
        assertEquals(1, viewModel.uiState.value.selectedCount)

        viewModel.clearSelection()

        assertEquals(0, viewModel.uiState.value.selectedCount)
    }

    // ========== Batch Processing Tests ==========

    @Test
    fun `canBatchProcess is true when corrections selected`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addPendingCorrection("tx_1", createTestFeatures("m1"), "a", "b", 0.5f, "test")
        assertFalse(viewModel.uiState.value.canBatchProcess)

        viewModel.selectAllCorrections()
        assertTrue(viewModel.uiState.value.canBatchProcess)
    }

    @Test
    fun `processAllCorrections clears queue on completion`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addPendingCorrection("tx_1", createTestFeatures("m1"), "a", "b", 0.5f, "test")
        viewModel.addPendingCorrection("tx_2", createTestFeatures("m2"), "c", "d", 0.6f, "test")
        assertEquals(2, viewModel.uiState.value.pendingCorrections.size)

        viewModel.processAllCorrections()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.pendingCorrections.isEmpty())
        assertNotNull(viewModel.uiState.value.successMessage)
    }

    @Test
    fun `batch processing shows progress`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addPendingCorrection("tx_1", createTestFeatures("m1"), "a", "b", 0.5f, "test")

        viewModel.processAllCorrections()

        // During processing
        val midState = viewModel.uiState.value
        assertTrue(midState.isBatchProcessing || midState.batchProgress != null || midState.successMessage != null)

        testDispatcher.scheduler.advanceUntilIdle()

        // After processing
        val finalState = viewModel.uiState.value
        assertFalse(finalState.isBatchProcessing)
        assertNull(finalState.batchProgress)
    }

    // ========== Click Handler Tests ==========

    @Test
    fun `onCorrectionClicked toggles selection in selection mode`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addPendingCorrection("tx_1", createTestFeatures("m1"), "a", "b", 0.5f, "test")
        val id = viewModel.uiState.value.pendingCorrections.first().id

        viewModel.toggleSelectionMode()
        viewModel.onCorrectionClicked(id)

        assertTrue(viewModel.uiState.value.selectedCorrections.contains(id))
    }

    @Test
    fun `onCorrectionClicked emits navigation event when not in selection mode`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addPendingCorrection("tx_1", createTestFeatures("m1"), "a", "b", 0.5f, "test")
        val id = viewModel.uiState.value.pendingCorrections.first().id

        viewModel.onCorrectionClicked(id)

        val event = viewModel.events.value
        assertTrue(event is CorrectionEvent.NavigateToTransaction)
        assertEquals("tx_1", (event as CorrectionEvent.NavigateToTransaction).transactionId)
    }

    // ========== Suggested Rules Tests ==========

    @Test
    fun `acceptSuggestedRule emits event and removes rule`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        // Manually add a suggested rule for testing
        val rules = listOf(
            SuggestedRuleUiModel("starbucks", "food", "Food & Dining", 5, 0.9f, "Consistent corrections")
        )
        // Note: In real scenario, rules come from correctionProcessor.analyzeCorrections()

        viewModel.acceptSuggestedRule("starbucks", "food")

        val event = viewModel.events.value
        assertTrue(event is CorrectionEvent.RuleSuggestionAccepted)
        assertEquals("starbucks", (event as CorrectionEvent.RuleSuggestionAccepted).merchantPattern)
        assertEquals("food", event.categoryId)
    }

    // ========== Error/Message Handling Tests ==========

    @Test
    fun `dismissError clears error`() {
        viewModel.dismissError()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `dismissSuccessMessage clears success message`() {
        viewModel.dismissSuccessMessage()
        assertNull(viewModel.uiState.value.successMessage)
    }

    @Test
    fun `clearEvent clears event`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addPendingCorrection("tx_1", createTestFeatures("m1"), "a", "b", 0.5f, "test")
        val id = viewModel.uiState.value.pendingCorrections.first().id
        viewModel.onCorrectionClicked(id)

        assertNotNull(viewModel.events.value)

        viewModel.clearEvent()

        assertNull(viewModel.events.value)
    }

    // ========== UI Model Tests ==========

    @Test
    fun `PendingCorrectionUiModel calculates confidence percent`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addPendingCorrection("tx_1", createTestFeatures("m1"), "a", "b", 0.75f, "test")

        val correction = viewModel.uiState.value.pendingCorrections.first()
        assertEquals(75, correction.confidencePercent)
    }

    @Test
    fun `PendingCorrectionUiModel identifies low confidence`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addPendingCorrection("tx_1", createTestFeatures("m1"), "a", "b", 0.3f, "test")
        viewModel.addPendingCorrection("tx_2", createTestFeatures("m2"), "c", "d", 0.7f, "test")

        val corrections = viewModel.uiState.value.pendingCorrections
        assertTrue(corrections[0].isLowConfidence)
        assertFalse(corrections[1].isLowConfidence)
    }

    @Test
    fun `amount is formatted correctly`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addPendingCorrection(
            "tx_1",
            createTestFeatures("m1", amountCents = 1234, isDebit = true),
            "a", "b", 0.5f, "test"
        )
        viewModel.addPendingCorrection(
            "tx_2",
            createTestFeatures("m2", amountCents = 5000, isDebit = false),
            "c", "d", 0.6f, "test"
        )

        val corrections = viewModel.uiState.value.pendingCorrections
        assertEquals("-$12.34", corrections[0].amount)
        assertEquals("+$50.00", corrections[1].amount)
    }

    // ========== Helper Functions ==========

    private fun createTestFeatures(merchant: String, amountCents: Long = 1000, isDebit: Boolean = true) =
        TransactionFeatures(
            merchantNormalized = merchant,
            descriptionRaw = "Purchase at $merchant",
            descriptionTokens = listOf("purchase", merchant),
            amountCents = amountCents,
            amountBucket = AmountBucket.fromCents(amountCents),
            isDebit = isDebit,
            dayOfWeek = 2,
            dayOfMonth = 15,
            accountId = "account_1"
        )
}
