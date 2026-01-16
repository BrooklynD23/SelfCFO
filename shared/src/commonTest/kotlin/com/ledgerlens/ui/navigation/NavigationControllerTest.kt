package com.ledgerlens.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class NavigationControllerTest {

    @Test
    fun `initial state is Dashboard`() {
        val controller = NavigationController()
        assertEquals(Screen.Dashboard.route, controller.state.currentRoute)
        assertEquals(Screen.Dashboard, controller.state.currentScreen)
        assertFalse(controller.state.canGoBack)
        assertTrue(controller.state.isAtRoot)
    }

    @Test
    fun `navigateTo updates current route`() {
        val controller = NavigationController()
        controller.navigateTo(Screen.Transactions)
        
        assertEquals(Screen.Transactions.route, controller.state.currentRoute)
        assertEquals(Screen.Dashboard.route, controller.state.previousRoute)
        assertTrue(controller.state.canGoBack)
    }

    @Test
    fun `navigateToRoute updates with string route`() {
        val controller = NavigationController()
        controller.navigateToRoute("transactions/tx-123")
        
        assertEquals("transactions/tx-123", controller.state.currentRoute)
    }

    @Test
    fun `navigateBack returns to previous screen`() {
        val controller = NavigationController()
        controller.navigateTo(Screen.Transactions)
        controller.navigateTo(Screen.Settings)
        
        assertEquals(Screen.Settings.route, controller.state.currentRoute)
        assertTrue(controller.state.canGoBack)
        
        controller.navigateBack()
        assertEquals(Screen.Transactions.route, controller.state.currentRoute)
        
        controller.navigateBack()
        assertEquals(Screen.Dashboard.route, controller.state.currentRoute)
        assertFalse(controller.state.canGoBack)
    }

    @Test
    fun `navigateBack does nothing when at root`() {
        val controller = NavigationController()
        controller.navigateBack()
        
        assertEquals(Screen.Dashboard.route, controller.state.currentRoute)
        assertFalse(controller.state.canGoBack)
    }

    @Test
    fun `navigateToRoot clears back stack`() {
        val controller = NavigationController()
        controller.navigateTo(Screen.Transactions)
        controller.navigateTo(Screen.Settings)
        controller.navigateTo(Screen.Categories)
        
        controller.navigateToRoot(Screen.Dashboard)
        
        assertEquals(Screen.Dashboard.route, controller.state.currentRoute)
        assertFalse(controller.state.canGoBack)
        assertTrue(controller.state.isAtRoot)
    }

    @Test
    fun `popUpTo navigates to target screen`() {
        val controller = NavigationController()
        controller.navigateTo(Screen.Transactions)
        controller.navigateTo(Screen.Settings)
        controller.navigateTo(Screen.Categories)
        
        controller.popUpTo(Screen.Transactions, inclusive = false)
        
        assertEquals(Screen.Transactions.route, controller.state.currentRoute)
        assertTrue(controller.state.canGoBack)
    }

    @Test
    fun `popUpTo with inclusive removes target screen`() {
        val controller = NavigationController()
        controller.navigateTo(Screen.Transactions)
        controller.navigateTo(Screen.Settings)
        controller.navigateTo(Screen.Categories)
        
        controller.popUpTo(Screen.Transactions, inclusive = true)
        
        assertEquals(Screen.Dashboard.route, controller.state.currentRoute)
        assertFalse(controller.state.canGoBack)
    }

    @Test
    fun `navigateToTransactionDetail creates correct route`() {
        val controller = NavigationController()
        controller.navigateToTransactionDetail("tx-123")
        
        assertEquals("transactions/tx-123", controller.state.currentRoute)
    }

    @Test
    fun `navigateToReceiptDetail creates correct route`() {
        val controller = NavigationController()
        controller.navigateToReceiptDetail("receipt-456")
        
        assertEquals("receipts/receipt-456", controller.state.currentRoute)
    }

    @Test
    fun `navigateToCategoryDetail creates correct route`() {
        val controller = NavigationController()
        controller.navigateToCategoryDetail("cat-789")
        
        assertEquals("categories/cat-789", controller.state.currentRoute)
    }

    @Test
    fun `navigateToAccountDetail creates correct route`() {
        val controller = NavigationController()
        controller.navigateToAccountDetail("acc-101")
        
        assertEquals("accounts/acc-101", controller.state.currentRoute)
    }

    @Test
    fun `convenience methods navigate correctly`() {
        val controller = NavigationController()
        
        controller.navigateToTransactions()
        assertEquals(Screen.Transactions.route, controller.state.currentRoute)
        
        controller.navigateToImport()
        assertEquals(Screen.Import.route, controller.state.currentRoute)
        
        controller.navigateToCategories()
        assertEquals(Screen.Categories.route, controller.state.currentRoute)
        
        controller.navigateToSettings()
        assertEquals(Screen.Settings.route, controller.state.currentRoute)
        
        controller.navigateToDashboard()
        assertEquals(Screen.Dashboard.route, controller.state.currentRoute)
    }

    @Test
    fun `navigateToSearch navigates to search screen`() {
        val controller = NavigationController()
        controller.navigateToSearch()
        
        assertEquals(Screen.Search.route, controller.state.currentRoute)
    }

    @Test
    fun `navigateToReports navigates to reports screen`() {
        val controller = NavigationController()
        controller.navigateToReports()
        
        assertEquals(Screen.Reports.route, controller.state.currentRoute)
    }

    @Test
    fun `navigateToReview navigates to review screen`() {
        val controller = NavigationController()
        controller.navigateToReview()
        
        assertEquals(Screen.Review.route, controller.state.currentRoute)
    }

    @Test
    fun `navigateToReceipts navigates to receipts screen`() {
        val controller = NavigationController()
        controller.navigateToReceipts()
        
        assertEquals(Screen.Receipts.route, controller.state.currentRoute)
    }

    @Test
    fun `listeners are notified on navigation`() {
        val controller = NavigationController()
        var notifiedState: NavigationState? = null
        
        controller.addListener { state ->
            notifiedState = state
        }
        
        controller.navigateTo(Screen.Transactions)
        
        assertNotNull(notifiedState)
        assertEquals(Screen.Transactions.route, notifiedState?.currentRoute)
    }

    @Test
    fun `removed listeners are not notified`() {
        val controller = NavigationController()
        var callCount = 0
        
        val listener: (NavigationState) -> Unit = { callCount++ }
        controller.addListener(listener)
        controller.navigateTo(Screen.Transactions)
        assertEquals(1, callCount)
        
        controller.removeListener(listener)
        controller.navigateTo(Screen.Settings)
        assertEquals(1, callCount) // Should not increment
    }

    @Test
    fun `back stack tracks navigation history`() {
        val controller = NavigationController()
        
        assertEquals(1, controller.state.backStack.size)
        
        controller.navigateTo(Screen.Transactions)
        assertEquals(2, controller.state.backStack.size)
        
        controller.navigateTo(Screen.Settings)
        assertEquals(3, controller.state.backStack.size)
        
        controller.navigateBack()
        assertEquals(2, controller.state.backStack.size)
    }

    @Test
    fun `custom initial screen is supported`() {
        val controller = NavigationController(initialScreen = Screen.Settings)
        
        assertEquals(Screen.Settings.route, controller.state.currentRoute)
        assertFalse(controller.state.canGoBack)
    }
}
