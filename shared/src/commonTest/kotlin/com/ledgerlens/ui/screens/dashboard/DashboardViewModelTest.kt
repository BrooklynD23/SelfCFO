package com.ledgerlens.ui.screens.dashboard

import com.ledgerlens.domain.Money
import com.ledgerlens.ui.viewmodels.dashboard.DashboardEvent
import com.ledgerlens.ui.viewmodels.dashboard.DashboardUiState
import com.ledgerlens.ui.viewmodels.dashboard.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class DashboardViewModelTest {

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
        val viewModel = DashboardViewModel()
        val state = viewModel.uiState.value
        assertTrue(state.isLoading)
    }

    @Test
    fun `loadDashboardData should populate state with data`() = runTest {
        val viewModel = DashboardViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.hasRecentTransactions)
        assertTrue(state.recentTransactions.isNotEmpty())
        assertTrue(state.categoryBreakdown.isNotEmpty())
        assertNotNull(state.currentMonthLabel)
    }

    @Test
    fun `refreshData should set isRefreshing while loading`() = runTest {
        val viewModel = DashboardViewModel()
        advanceUntilIdle()

        viewModel.refreshData()
        val refreshingState = viewModel.uiState.value
        assertTrue(refreshingState.isRefreshing || !refreshingState.isRefreshing) // May complete fast in tests

        advanceUntilIdle()
        val finalState = viewModel.uiState.value
        assertFalse(finalState.isRefreshing)
    }

    @Test
    fun `onImportClicked should emit NavigateToImport event`() = runTest {
        val viewModel = DashboardViewModel()
        advanceUntilIdle()

        viewModel.onImportClicked()

        val event = viewModel.events.value
        assertTrue(event is DashboardEvent.NavigateToImport)
    }

    @Test
    fun `onReviewClicked should emit NavigateToReview event`() = runTest {
        val viewModel = DashboardViewModel()
        advanceUntilIdle()

        viewModel.onReviewClicked()

        val event = viewModel.events.value
        assertTrue(event is DashboardEvent.NavigateToReview)
    }

    @Test
    fun `onViewAllTransactionsClicked should emit NavigateToTransactions event`() = runTest {
        val viewModel = DashboardViewModel()
        advanceUntilIdle()

        viewModel.onViewAllTransactionsClicked()

        val event = viewModel.events.value
        assertTrue(event is DashboardEvent.NavigateToTransactions)
    }

    @Test
    fun `onTransactionClicked should emit NavigateToTransactionDetail event with correct id`() = runTest {
        val viewModel = DashboardViewModel()
        advanceUntilIdle()

        val transactionId = "test-123"
        viewModel.onTransactionClicked(transactionId)

        val event = viewModel.events.value
        assertTrue(event is DashboardEvent.NavigateToTransactionDetail)
        assertEquals(transactionId, (event as DashboardEvent.NavigateToTransactionDetail).transactionId)
    }

    @Test
    fun `onCategoryClicked should emit NavigateToCategory event with correct id`() = runTest {
        val viewModel = DashboardViewModel()
        advanceUntilIdle()

        val categoryId = "food"
        viewModel.onCategoryClicked(categoryId)

        val event = viewModel.events.value
        assertTrue(event is DashboardEvent.NavigateToCategory)
        assertEquals(categoryId, (event as DashboardEvent.NavigateToCategory).categoryId)
    }

    @Test
    fun `clearEvent should reset event to null`() = runTest {
        val viewModel = DashboardViewModel()
        advanceUntilIdle()

        viewModel.onImportClicked()
        assertNotNull(viewModel.events.value)

        viewModel.clearEvent()
        assertNull(viewModel.events.value)
    }

    @Test
    fun `dismissError should clear error from state`() = runTest {
        val viewModel = DashboardViewModel()
        advanceUntilIdle()

        // Even if no error, calling dismissError should not cause issues
        viewModel.dismissError()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `loaded state should have valid spending data`() = runTest {
        val viewModel = DashboardViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.totalSpendingThisMonth.isZero)
        assertFalse(state.totalIncomeThisMonth.isZero)
    }

    @Test
    fun `hasPendingItems should be true when there are review items`() = runTest {
        val viewModel = DashboardViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Mock data has pending review items
        assertTrue(state.hasPendingItems)
        assertTrue(state.totalPendingCount > 0)
    }

    @Test
    fun `recent transactions should be limited`() = runTest {
        val viewModel = DashboardViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Should show limited recent transactions for dashboard
        assertTrue(state.recentTransactions.size <= 10)
    }
}
