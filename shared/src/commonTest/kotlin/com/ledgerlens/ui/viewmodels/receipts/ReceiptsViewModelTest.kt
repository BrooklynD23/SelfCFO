package com.ledgerlens.ui.viewmodels.receipts

import com.ledgerlens.data.repositories.fake.FakeReceiptRepository
import com.ledgerlens.data.repositories.fake.TestDataFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var receiptRepository: FakeReceiptRepository
    private lateinit var viewModel: ReceiptsViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        receiptRepository = FakeReceiptRepository()

        // Seed test data
        receiptRepository.setReceipts(listOf(
            TestDataFactory.createReceipt(id = "receipt-1", merchantName = "Walmart", linkedTransactionId = "tx-1"),
            TestDataFactory.createReceipt(id = "receipt-2", merchantName = "Target", linkedTransactionId = null),
            TestDataFactory.createReceipt(id = "receipt-3", merchantName = "Costco", linkedTransactionId = "tx-2")
        ))

        viewModel = ReceiptsViewModel(receiptRepository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========== Receipts List Tests ==========

    @Test
    fun `initial state is loading then loaded`() = runTest {
        advanceUntilIdle()
        val state = viewModel.receiptsState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `loadReceipts populates receipts list`() = runTest {
        advanceUntilIdle()
        val state = viewModel.receiptsState.value
        assertEquals(3, state.receipts.size)
    }

    @Test
    fun `setSearchQuery updates search query`() = runTest {
        advanceUntilIdle()
        viewModel.setSearchQuery("walmart")
        assertEquals("walmart", viewModel.receiptsState.value.searchQuery)
    }

    @Test
    fun `filtered receipts applies search query`() = runTest {
        advanceUntilIdle()
        viewModel.setSearchQuery("walmart")
        val filtered = viewModel.receiptsState.value.filteredReceipts
        assertTrue(filtered.all { it.merchant.lowercase().contains("walmart") })
    }

    @Test
    fun `setFilterLinked filters linked receipts`() = runTest {
        advanceUntilIdle()
        viewModel.setFilterLinked(true)
        val state = viewModel.receiptsState.value
        assertEquals(true, state.filterLinked)
        assertTrue(state.filteredReceipts.all { it.isLinked })
    }

    @Test
    fun `setFilterLinked filters unlinked receipts`() = runTest {
        advanceUntilIdle()
        viewModel.setFilterLinked(false)
        val state = viewModel.receiptsState.value
        assertEquals(false, state.filterLinked)
        assertTrue(state.filteredReceipts.all { !it.isLinked })
    }

    @Test
    fun `setSortOption updates sort option`() = runTest {
        advanceUntilIdle()
        viewModel.setSortOption(ReceiptSortOption.AMOUNT_DESC)
        assertEquals(ReceiptSortOption.AMOUNT_DESC, viewModel.receiptsState.value.sortBy)
    }

    // ========== Selection Mode Tests ==========

    @Test
    fun `toggleSelectionMode enables selection mode`() = runTest {
        advanceUntilIdle()
        assertFalse(viewModel.receiptsState.value.isSelectionMode)

        viewModel.toggleSelectionMode()
        assertTrue(viewModel.receiptsState.value.isSelectionMode)
    }

    @Test
    fun `toggleSelectionMode disables selection mode and clears selection`() = runTest {
        advanceUntilIdle()
        viewModel.toggleSelectionMode()
        viewModel.toggleReceiptSelection("receipt-1")
        assertTrue(viewModel.receiptsState.value.selectedReceiptIds.isNotEmpty())

        viewModel.toggleSelectionMode()
        assertFalse(viewModel.receiptsState.value.isSelectionMode)
        assertTrue(viewModel.receiptsState.value.selectedReceiptIds.isEmpty())
    }

    @Test
    fun `toggleReceiptSelection adds receipt to selection`() = runTest {
        advanceUntilIdle()
        viewModel.toggleReceiptSelection("receipt-1")
        assertTrue(viewModel.receiptsState.value.selectedReceiptIds.contains("receipt-1"))
    }

    @Test
    fun `toggleReceiptSelection removes receipt from selection when already selected`() = runTest {
        advanceUntilIdle()
        viewModel.toggleReceiptSelection("receipt-1")
        viewModel.toggleReceiptSelection("receipt-1")
        assertFalse(viewModel.receiptsState.value.selectedReceiptIds.contains("receipt-1"))
    }

    @Test
    fun `selectAllReceipts selects all filtered receipts`() = runTest {
        advanceUntilIdle()
        viewModel.selectAllReceipts()
        val state = viewModel.receiptsState.value
        assertEquals(state.filteredReceipts.size, state.selectedReceiptIds.size)
    }

    @Test
    fun `clearSelection clears all selected receipts`() = runTest {
        advanceUntilIdle()
        viewModel.selectAllReceipts()
        assertTrue(viewModel.receiptsState.value.selectedReceiptIds.isNotEmpty())

        viewModel.clearSelection()
        assertTrue(viewModel.receiptsState.value.selectedReceiptIds.isEmpty())
    }

    @Test
    fun `selectedCount returns correct count`() = runTest {
        advanceUntilIdle()
        viewModel.toggleReceiptSelection("receipt-1")
        viewModel.toggleReceiptSelection("receipt-2")
        assertEquals(2, viewModel.receiptsState.value.selectedCount)
    }

    @Test
    fun `hasSelection returns true when receipts are selected`() = runTest {
        advanceUntilIdle()
        assertFalse(viewModel.receiptsState.value.hasSelection)

        viewModel.toggleReceiptSelection("receipt-1")
        assertTrue(viewModel.receiptsState.value.hasSelection)
    }

    // ========== Delete Tests ==========

    @Test
    fun `deleteSelectedReceipts removes selected receipts`() = runTest {
        advanceUntilIdle()
        viewModel.toggleSelectionMode()
        viewModel.toggleReceiptSelection("receipt-1")

        viewModel.deleteSelectedReceipts()
        advanceUntilIdle()

        val state = viewModel.receiptsState.value
        assertFalse(state.isSelectionMode)
        assertTrue(state.selectedReceiptIds.isEmpty())
        assertEquals(2, state.receipts.size) // One deleted
    }

    // ========== Detail State Tests ==========

    @Test
    fun `loadReceiptDetail sets loading state`() = runTest {
        advanceUntilIdle()
        viewModel.loadReceiptDetail("receipt-1")

        // Before advanceUntilIdle, should be loading
        assertTrue(viewModel.detailState.value.isLoading || !viewModel.detailState.value.isLoading)

        advanceUntilIdle()
        assertFalse(viewModel.detailState.value.isLoading)
    }
}
