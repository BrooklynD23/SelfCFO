package com.ledgerlens.ui.screens.transactions

import com.ledgerlens.ui.viewmodels.transactions.TransactionFilters
import com.ledgerlens.ui.viewmodels.transactions.TransactionsEvent
import com.ledgerlens.ui.viewmodels.transactions.TransactionsViewModel
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
class TransactionsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state should be loading`() = runTest {
        val viewModel = TransactionsViewModel()
        val state = viewModel.uiState.value
        assertTrue(state.isLoading)
    }

    @Test
    fun `loadTransactions should populate transactions`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.hasTransactions)
        assertTrue(state.transactions.isNotEmpty())
    }

    @Test
    fun `updateSearchQuery should filter transactions`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        viewModel.updateSearchQuery("Starbucks")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Starbucks", state.filters.searchQuery)
        assertTrue(state.transactions.all {
            it.displayMerchant.contains("Starbucks", ignoreCase = true) ||
            it.description.contains("Starbucks", ignoreCase = true)
        })
    }

    @Test
    fun `toggleCategoryFilter should add and remove category from filters`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        val categoryId = "food"
        viewModel.toggleCategoryFilter(categoryId)
        advanceUntilIdle()

        assertTrue(categoryId in viewModel.uiState.value.filters.selectedCategories)

        viewModel.toggleCategoryFilter(categoryId)
        advanceUntilIdle()

        assertFalse(categoryId in viewModel.uiState.value.filters.selectedCategories)
    }

    @Test
    fun `toggleIncomeFilter should filter income transactions only`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        viewModel.toggleIncomeFilter()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.filters.showIncomeOnly)
        assertFalse(state.filters.showExpensesOnly)
        assertTrue(state.transactions.all { it.isIncome })
    }

    @Test
    fun `toggleExpensesFilter should filter expense transactions only`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        viewModel.toggleExpensesFilter()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.filters.showExpensesOnly)
        assertFalse(state.filters.showIncomeOnly)
        assertTrue(state.transactions.all { it.isExpense })
    }

    @Test
    fun `toggleNeedsReviewFilter should filter transactions needing review`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        viewModel.toggleNeedsReviewFilter()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.filters.showNeedsReview)
        assertTrue(state.transactions.all { it.needsReview })
    }

    @Test
    fun `clearAllFilters should reset all filters`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        // Apply some filters
        viewModel.updateSearchQuery("test")
        viewModel.toggleIncomeFilter()
        viewModel.toggleCategoryFilter("food")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.filters.hasActiveFilters)

        viewModel.clearAllFilters()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(TransactionFilters(), state.filters)
        assertFalse(state.filters.hasActiveFilters)
    }

    @Test
    fun `toggleSelectionMode should enable and disable selection mode`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSelectionMode)

        viewModel.toggleSelectionMode()
        assertTrue(viewModel.uiState.value.isSelectionMode)

        viewModel.toggleSelectionMode()
        assertFalse(viewModel.uiState.value.isSelectionMode)
    }

    @Test
    fun `toggleTransactionSelection should add and remove transaction from selection`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        val transactionId = viewModel.uiState.value.transactions.first().id

        viewModel.toggleTransactionSelection(transactionId)
        assertTrue(transactionId in viewModel.uiState.value.selectedTransactions)

        viewModel.toggleTransactionSelection(transactionId)
        assertFalse(transactionId in viewModel.uiState.value.selectedTransactions)
    }

    @Test
    fun `selectAllTransactions should select all visible transactions`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        viewModel.selectAllTransactions()

        val state = viewModel.uiState.value
        assertEquals(state.transactions.size, state.selectedTransactions.size)
        assertTrue(state.transactions.all { it.id in state.selectedTransactions })
    }

    @Test
    fun `clearSelection should remove all selections`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        viewModel.selectAllTransactions()
        assertTrue(viewModel.uiState.value.selectedTransactions.isNotEmpty())

        viewModel.clearSelection()
        assertTrue(viewModel.uiState.value.selectedTransactions.isEmpty())
    }

    @Test
    fun `onTransactionClicked in normal mode should emit NavigateToDetail event`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        val transactionId = "test-123"
        viewModel.onTransactionClicked(transactionId)

        val event = viewModel.events.value
        assertTrue(event is TransactionsEvent.NavigateToDetail)
        assertEquals(transactionId, (event as TransactionsEvent.NavigateToDetail).transactionId)
    }

    @Test
    fun `onTransactionClicked in selection mode should toggle selection`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        viewModel.toggleSelectionMode()
        val transactionId = viewModel.uiState.value.transactions.first().id

        viewModel.onTransactionClicked(transactionId)
        assertTrue(transactionId in viewModel.uiState.value.selectedTransactions)

        viewModel.onTransactionClicked(transactionId)
        assertFalse(transactionId in viewModel.uiState.value.selectedTransactions)
    }

    @Test
    fun `pagination should work correctly`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        val initialState = viewModel.uiState.value
        val initialCount = initialState.transactions.size

        if (initialState.pagination.hasMorePages) {
            viewModel.loadMoreTransactions()
            advanceUntilIdle()

            val newState = viewModel.uiState.value
            assertTrue(newState.transactions.size >= initialCount)
        }
    }

    @Test
    fun `loadTransactionDetail should populate detail state`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        val transactionId = viewModel.uiState.value.transactions.first().id
        viewModel.loadTransactionDetail(transactionId)
        advanceUntilIdle()

        val detailState = viewModel.detailState.value
        assertFalse(detailState.isLoading)
        assertNotNull(detailState.transaction)
        assertEquals(transactionId, detailState.transaction?.id)
    }

    @Test
    fun `startEditing and cancelEditing should toggle editing state`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        viewModel.loadTransactionDetail("1")
        advanceUntilIdle()

        assertFalse(viewModel.detailState.value.isEditing)

        viewModel.startEditing()
        assertTrue(viewModel.detailState.value.isEditing)

        viewModel.cancelEditing()
        assertFalse(viewModel.detailState.value.isEditing)
    }

    @Test
    fun `updateTransactionCategory should update category and emit snackbar event`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        viewModel.loadTransactionDetail("1")
        advanceUntilIdle()
        viewModel.startEditing()

        viewModel.updateTransactionCategory("shopping")
        advanceUntilIdle()

        val detailState = viewModel.detailState.value
        assertFalse(detailState.isEditing)
        assertEquals("shopping", detailState.transaction?.category?.id)

        val event = viewModel.events.value
        assertTrue(event is TransactionsEvent.ShowSnackbar)
    }

    @Test
    fun `filters activeFilterCount should count active filters correctly`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.filters.activeFilterCount)

        viewModel.updateSearchQuery("test")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filters.activeFilterCount)

        viewModel.toggleIncomeFilter()
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.filters.activeFilterCount)

        viewModel.toggleCategoryFilter("food")
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.filters.activeFilterCount)
    }

    @Test
    fun `clearEvent should reset event to null`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        viewModel.onTransactionClicked("test")
        assertNotNull(viewModel.events.value)

        viewModel.clearEvent()
        assertNull(viewModel.events.value)
    }

    @Test
    fun `dismissError should clear error from state`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        viewModel.dismissError()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `setCategoryFilters should set multiple categories at once`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        val categories = setOf("food", "transport", "shopping")
        viewModel.setCategoryFilters(categories)
        advanceUntilIdle()

        assertEquals(categories, viewModel.uiState.value.filters.selectedCategories)
    }

    @Test
    fun `availableCategories should be populated after load`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.availableCategories.isNotEmpty())
    }
}
