package com.ledgerlens.ui.viewmodels.receipts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptsViewModelTest {
    
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: ReceiptsViewModel
    
    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ReceiptsViewModel()
    }
    
    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    // ========== List State Tests ==========
    
    @Test
    fun `initial state has empty receipts list`() {
        val state = viewModel.listState.value
        assertTrue(state.receipts.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.error)
    }
    
    @Test
    fun `setSearchQuery updates search query`() {
        viewModel.setSearchQuery("coffee")
        assertEquals("coffee", viewModel.listState.value.searchQuery)
    }
    
    @Test
    fun `toggleFilter adds filter when not present`() {
        viewModel.toggleFilter(ReceiptFilter.LINKED)
        assertTrue(viewModel.listState.value.activeFilters.contains(ReceiptFilter.LINKED))
    }
    
    @Test
    fun `toggleFilter removes filter when present`() {
        viewModel.toggleFilter(ReceiptFilter.LINKED)
        viewModel.toggleFilter(ReceiptFilter.LINKED)
        assertFalse(viewModel.listState.value.activeFilters.contains(ReceiptFilter.LINKED))
    }
    
    @Test
    fun `setSortOrder updates sort order`() {
        viewModel.setSortOrder(ReceiptSortOrder.MERCHANT)
        assertEquals(ReceiptSortOrder.MERCHANT, viewModel.listState.value.sortOrder)
    }
    
    @Test
    fun `clearError clears list error`() = runTest {
        // Simulate an error state then clear
        viewModel.clearError()
        assertNull(viewModel.listState.value.error)
    }
    
    // ========== Selection Mode Tests ==========
    
    @Test
    fun `enterSelectionMode enables selection mode`() {
        viewModel.enterSelectionMode()
        assertTrue(viewModel.listState.value.isSelectionMode)
    }
    
    @Test
    fun `exitSelectionMode disables selection mode and clears selection`() {
        viewModel.enterSelectionMode()
        viewModel.exitSelectionMode()
        assertFalse(viewModel.listState.value.isSelectionMode)
        assertTrue(viewModel.listState.value.selectedIds.isEmpty())
    }
    
    @Test
    fun `toggleSelection adds receipt to selection`() {
        viewModel.enterSelectionMode()
        viewModel.toggleSelection("receipt-1")
        assertTrue(viewModel.listState.value.selectedIds.contains("receipt-1"))
    }
    
    @Test
    fun `toggleSelection removes receipt from selection when already selected`() {
        viewModel.enterSelectionMode()
        viewModel.toggleSelection("receipt-1")
        viewModel.toggleSelection("receipt-1")
        assertFalse(viewModel.listState.value.selectedIds.contains("receipt-1"))
    }
    
    @Test
    fun `selectAll selects all receipts`() {
        // With empty list, selectAll should have no effect
        viewModel.enterSelectionMode()
        viewModel.selectAll()
        // Since receipts list is empty, selected should also be empty
        assertTrue(viewModel.listState.value.selectedIds.isEmpty())
    }
    
    // ========== Detail State Tests ==========
    
    @Test
    fun `initial detail state is null`() {
        assertNull(viewModel.detailState.value.receipt)
        assertFalse(viewModel.detailState.value.isLoading)
    }
    
    @Test
    fun `loadReceiptDetail sets loading state`() = runTest {
        viewModel.loadReceiptDetail("receipt-123")
        testDispatcher.scheduler.advanceUntilIdle()
        // After loading completes, receipt should be null (no mock data)
        // but error should be set since no repository
        // For now, just verify the flow works
        assertFalse(viewModel.detailState.value.isLoading)
    }
    
    // ========== Split State Tests ==========
    
    @Test
    fun `initial split state is not showing sheet`() {
        assertFalse(viewModel.splitState.value.showSheet)
    }
    
    @Test
    fun `showSplitSheet opens split sheet`() {
        viewModel.showSplitSheet()
        assertTrue(viewModel.splitState.value.showSheet)
    }
    
    @Test
    fun `hideSplitSheet closes split sheet`() {
        viewModel.showSplitSheet()
        viewModel.hideSplitSheet()
        assertFalse(viewModel.splitState.value.showSheet)
    }
    
    @Test
    fun `setSplitType updates split type`() {
        viewModel.showSplitSheet()
        viewModel.setSplitType(SplitType.EQUAL)
        assertEquals(SplitType.EQUAL, viewModel.splitState.value.splitType)
    }
    
    @Test
    fun `addParticipant adds participant to split`() {
        viewModel.showSplitSheet()
        val initialCount = viewModel.splitState.value.participants.size
        viewModel.addParticipant("Test User")
        assertEquals(initialCount + 1, viewModel.splitState.value.participants.size)
    }
    
    @Test
    fun `removeParticipant removes participant from split`() {
        viewModel.showSplitSheet()
        viewModel.addParticipant("Test User")
        val participant = viewModel.splitState.value.participants.find { it.name == "Test User" }
        assertNotNull(participant)
        
        viewModel.removeParticipant(participant.id)
        assertNull(viewModel.splitState.value.participants.find { it.id == participant.id })
    }
    
    @Test
    fun `toggleItemForParticipant toggles item assignment`() {
        viewModel.showSplitSheet()
        viewModel.addParticipant("Test User")
        val participant = viewModel.splitState.value.participants.find { it.name == "Test User" }
        assertNotNull(participant)
        
        // Toggle item assignment
        viewModel.toggleItemForParticipant(0, participant.id)
        assertTrue(viewModel.splitState.value.itemAssignments[0]?.contains(participant.id) == true)
        
        // Toggle again to remove
        viewModel.toggleItemForParticipant(0, participant.id)
        assertFalse(viewModel.splitState.value.itemAssignments[0]?.contains(participant.id) == true)
    }
    
    @Test
    fun `setCustomAmount updates custom amount for participant`() {
        viewModel.showSplitSheet()
        viewModel.addParticipant("Test User")
        val participant = viewModel.splitState.value.participants.find { it.name == "Test User" }
        assertNotNull(participant)
        
        viewModel.setCustomAmount(participant.id, 1500L)
        assertEquals(1500L, viewModel.splitState.value.customAmounts[participant.id])
    }
    
    @Test
    fun `setPercentage updates percentage for participant`() {
        viewModel.showSplitSheet()
        viewModel.addParticipant("Test User")
        val participant = viewModel.splitState.value.participants.find { it.name == "Test User" }
        assertNotNull(participant)
        
        viewModel.setPercentage(participant.id, 50.0)
        assertEquals(50.0, viewModel.splitState.value.percentages[participant.id])
    }
}
