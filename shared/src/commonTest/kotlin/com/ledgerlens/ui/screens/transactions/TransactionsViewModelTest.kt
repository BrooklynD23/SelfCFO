package com.ledgerlens.ui.screens.transactions

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
    fun `initial state is loading`() = runTest {
        val viewModel = TransactionsViewModel()
        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `loadTransactions populates state with mock data`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertTrue(state.transactions.isNotEmpty())
        assertTrue(state.availableCategories.isNotEmpty())
    }

    @Test
    fun `updateSearchQuery filters transactions`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        val initialCount = viewModel.uiState.value.transactions.size
        viewModel.updateSearchQuery("Starbucks")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.transactions.size <= initialCount)
        assertEquals("Starbucks", viewModel.uiState.value.filters.searchQuery)
    }

    @Test
    fun `toggleCategoryFilter adds and removes categories`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        viewModel.toggleCategoryFilter("food")
        assertTrue("food" in viewModel.uiState.value.filters.selectedCategories)

        viewModel.toggleCategoryFilter("food")
        assertFalse("food" in viewModel.uiState.value.filters.selectedCategories)
    }

    @Test
    fun `toggleIncomeFilter enables income only filter`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        viewModel.toggleIncomeFilter()
        assertTrue(viewModel.uiState.value.filters.showIncomeOnly)
        assertFalse(viewModel.uiState.value.filters.showExpensesOnly)
    }

    @Test
    fun `toggleExpensesFilter enables expenses only filter`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        viewModel.toggleExpensesFilter()
        assertTrue(viewModel.uiState.value.filters.showExpensesOnly)
        assertFalse(viewModel.uiState.value.filters.showIncomeOnly)
    }

    @Test
    fun `clearAllFilters resets all filters`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        viewModel.updateSearchQuery("test")
        viewModel.toggleIncomeFilter()
        viewModel.toggleCategoryFilter("food")

        viewModel.clearAllFilters()

        val filters = viewModel.uiState.value.filters
        assertEquals("", filters.searchQuery)
        assertFalse(filters.showIncomeOnly)
        assertTrue(filters.selectedCategories.isEmpty())
    }

    @Test
    fun `toggleSelectionMode enables selection mode`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        viewModel.toggleSelectionMode()
        assertTrue(viewModel.uiState.value.isSelectionMode)

        viewModel.toggleSelectionMode()
        assertFalse(viewModel.uiState.value.isSelectionMode)
    }

    @Test
    fun `toggleTransactionSelection adds and removes from selection`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        viewModel.toggleTransactionSelection("1")
        assertTrue("1" in viewModel.uiState.value.selectedTransactions)

        viewModel.toggleTransactionSelection("1")
        assertFalse("1" in viewModel.uiState.value.selectedTransactions)
    }

    @Test
    fun `onTransactionClicked in selection mode toggles selection`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        viewModel.toggleSelectionMode()
        viewModel.onTransactionClicked("1")

        assertTrue("1" in viewModel.uiState.value.selectedTransactions)
    }

    @Test
    fun `onTransactionClicked outside selection mode emits navigation event`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        viewModel.onTransactionClicked("1")

        val event = viewModel.events.value
        assertTrue(event is TransactionsEvent.NavigateToDetail)
        assertEquals("1", (event as TransactionsEvent.NavigateToDetail).transactionId)
    }

    @Test
    fun `loadTransactionDetail populates detail state`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        viewModel.loadTransactionDetail("1")
        advanceUntilIdle()

        val detailState = viewModel.detailState.value
        assertFalse(detailState.isLoading)
        assertNotNull(detailState.transaction)
        assertEquals("1", detailState.transaction?.id)
    }

    @Test
    fun `startEditing and cancelEditing toggle edit mode`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        viewModel.loadTransactionDetail("1")
        advanceUntilIdle()

        viewModel.startEditing()
        assertTrue(viewModel.detailState.value.isEditing)

        viewModel.cancelEditing()
        assertFalse(viewModel.detailState.value.isEditing)
    }

    @Test
    fun `updateTransactionCategory updates category and emits snackbar`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        viewModel.loadTransactionDetail("1")
        advanceUntilIdle()

        viewModel.startEditing()
        viewModel.updateTransactionCategory("shopping")
        advanceUntilIdle()

        assertFalse(viewModel.detailState.value.isEditing)
        assertEquals("shopping", viewModel.detailState.value.transaction?.category?.id)

        val event = viewModel.events.value
        assertTrue(event is TransactionsEvent.ShowSnackbar)
    }

    @Test
    fun `clearEvent resets events to null`() = runTest {
        val viewModel = TransactionsViewModel()
        advanceUntilIdle()

        viewModel.onTransactionClicked("1")
        assertNotNull(viewModel.events.value)

        viewModel.clearEvent()
        assertNull(viewModel.events.value)
    }
}
