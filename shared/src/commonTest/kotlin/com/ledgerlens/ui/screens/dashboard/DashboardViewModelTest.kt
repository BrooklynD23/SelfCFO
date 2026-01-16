package com.ledgerlens.ui.screens.dashboard

import com.ledgerlens.ui.viewmodels.dashboard.DashboardEvent
import com.ledgerlens.ui.viewmodels.dashboard.DashboardViewModel
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
    fun `initial state is loading`() = runTest {
        val viewModel = DashboardViewModel()
        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `loadDashboardData populates state with mock data`() = runTest {
        val viewModel = DashboardViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertTrue(state.recentTransactions.isNotEmpty())
        assertTrue(state.categoryBreakdown.isNotEmpty())
        assertTrue(state.currentMonthLabel.isNotBlank())
    }

    @Test
    fun `refreshData sets isRefreshing to true then false`() = runTest {
        val viewModel = DashboardViewModel()
        advanceUntilIdle()

        viewModel.refreshData()
        assertTrue(viewModel.uiState.value.isRefreshing)

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `onImportClicked emits NavigateToImport event`() = runTest {
        val viewModel = DashboardViewModel()
        advanceUntilIdle()

        viewModel.onImportClicked()
        assertEquals(DashboardEvent.NavigateToImport, viewModel.events.value)
    }

    @Test
    fun `onReviewClicked emits NavigateToReview event`() = runTest {
        val viewModel = DashboardViewModel()
        advanceUntilIdle()

        viewModel.onReviewClicked()
        assertEquals(DashboardEvent.NavigateToReview, viewModel.events.value)
    }

    @Test
    fun `onTransactionClicked emits NavigateToTransactionDetail event`() = runTest {
        val viewModel = DashboardViewModel()
        advanceUntilIdle()

        viewModel.onTransactionClicked("tx-123")
        val event = viewModel.events.value
        assertTrue(event is DashboardEvent.NavigateToTransactionDetail)
        assertEquals("tx-123", (event as DashboardEvent.NavigateToTransactionDetail).transactionId)
    }

    @Test
    fun `onCategoryClicked emits NavigateToCategory event`() = runTest {
        val viewModel = DashboardViewModel()
        advanceUntilIdle()

        viewModel.onCategoryClicked("food")
        val event = viewModel.events.value
        assertTrue(event is DashboardEvent.NavigateToCategory)
        assertEquals("food", (event as DashboardEvent.NavigateToCategory).categoryId)
    }

    @Test
    fun `clearEvent resets events to null`() = runTest {
        val viewModel = DashboardViewModel()
        advanceUntilIdle()

        viewModel.onImportClicked()
        assertNotNull(viewModel.events.value)

        viewModel.clearEvent()
        assertNull(viewModel.events.value)
    }

    @Test
    fun `dismissError clears error from state`() = runTest {
        val viewModel = DashboardViewModel()
        advanceUntilIdle()

        viewModel.dismissError()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `hasRecentTransactions returns true when transactions exist`() = runTest {
        val viewModel = DashboardViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasRecentTransactions)
    }

    @Test
    fun `hasPendingItems returns true when pending count is positive`() = runTest {
        val viewModel = DashboardViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasPendingItems)
        assertTrue(viewModel.uiState.value.totalPendingCount > 0)
    }
}
