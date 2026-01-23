package com.ledgerlens.ui.screens.dashboard

import com.ledgerlens.data.repositories.fake.FakeCategoryRepository
import com.ledgerlens.data.repositories.fake.FakeStatisticsRepository
import com.ledgerlens.data.repositories.fake.FakeTransactionRepository
import com.ledgerlens.data.repositories.fake.TestDataFactory
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
    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var statisticsRepository: FakeStatisticsRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        transactionRepository = FakeTransactionRepository()
        categoryRepository = FakeCategoryRepository()
        statisticsRepository = FakeStatisticsRepository()

        // Seed test data
        categoryRepository.setCategories(listOf(
            TestDataFactory.createCategory(id = "groceries", name = "Groceries"),
            TestDataFactory.createCategory(id = "dining", name = "Dining")
        ))

        transactionRepository.setTransactions(listOf(
            TestDataFactory.createTransaction(id = "1", merchantNormalized = "WALMART", categoryId = "groceries", amountMinorUnits = -5000),
            TestDataFactory.createTransaction(id = "2", merchantNormalized = "STARBUCKS", categoryId = "dining", amountMinorUnits = -450),
            TestDataFactory.createTransaction(id = "3", merchantNormalized = "SALARY", categoryId = null, amountMinorUnits = 500000)
        ))

        statisticsRepository.setPendingReviewCount(3)
        statisticsRepository.setUncategorizedCount(2)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = DashboardViewModel(transactionRepository, categoryRepository, statisticsRepository)

    @Test
    fun `initial state should be loading`() = runTest {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value
        assertTrue(state.isLoading)
    }

    @Test
    fun `loadDashboardData should populate state with data`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.hasRecentTransactions)
        assertTrue(state.recentTransactions.isNotEmpty())
        assertNotNull(state.currentMonthLabel)
    }

    @Test
    fun `refreshData should set isRefreshing while loading`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.refreshData()
        advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assertFalse(finalState.isRefreshing)
    }

    @Test
    fun `onImportClicked should emit NavigateToImport event`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onImportClicked()

        val event = viewModel.events.value
        assertTrue(event is DashboardEvent.NavigateToImport)
    }

    @Test
    fun `onReviewClicked should emit NavigateToReview event`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onReviewClicked()

        val event = viewModel.events.value
        assertTrue(event is DashboardEvent.NavigateToReview)
    }

    @Test
    fun `onViewAllTransactionsClicked should emit NavigateToTransactions event`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onViewAllTransactionsClicked()

        val event = viewModel.events.value
        assertTrue(event is DashboardEvent.NavigateToTransactions)
    }

    @Test
    fun `onTransactionClicked should emit NavigateToTransactionDetail event with correct id`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val transactionId = "test-123"
        viewModel.onTransactionClicked(transactionId)

        val event = viewModel.events.value
        assertTrue(event is DashboardEvent.NavigateToTransactionDetail)
        assertEquals(transactionId, (event as DashboardEvent.NavigateToTransactionDetail).transactionId)
    }

    @Test
    fun `onCategoryClicked should emit NavigateToCategory event with correct id`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val categoryId = "groceries"
        viewModel.onCategoryClicked(categoryId)

        val event = viewModel.events.value
        assertTrue(event is DashboardEvent.NavigateToCategory)
        assertEquals(categoryId, (event as DashboardEvent.NavigateToCategory).categoryId)
    }

    @Test
    fun `clearEvent should reset event to null`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onImportClicked()
        assertNotNull(viewModel.events.value)

        viewModel.clearEvent()
        assertNull(viewModel.events.value)
    }

    @Test
    fun `dismissError should clear error from state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.dismissError()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `hasPendingItems should be true when there are review items`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasPendingItems)
        assertEquals(5, state.totalPendingCount) // 3 pending + 2 uncategorized
    }

    @Test
    fun `recent transactions should be limited`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.recentTransactions.size <= 10)
    }
}
