package com.ledgerlens.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScreenTest {

    @Test
    fun `Dashboard has correct route`() {
        assertEquals("dashboard", Screen.Dashboard.route)
        assertEquals("Dashboard", Screen.Dashboard.title)
        assertTrue(Screen.Dashboard.showInBottomNav)
    }

    @Test
    fun `Transactions has correct route`() {
        assertEquals("transactions", Screen.Transactions.route)
        assertEquals("Transactions", Screen.Transactions.title)
        assertTrue(Screen.Transactions.showInBottomNav)
    }

    @Test
    fun `TransactionDetail creates route with id`() {
        val route = Screen.TransactionDetail.createRoute("tx-123")
        assertEquals("transactions/tx-123", route)
    }

    @Test
    fun `ReceiptDetail creates route with id`() {
        val route = Screen.ReceiptDetail.createRoute("receipt-456")
        assertEquals("receipts/receipt-456", route)
    }

    @Test
    fun `CategoryDetail creates route with id`() {
        val route = Screen.CategoryDetail.createRoute("cat-789")
        assertEquals("categories/cat-789", route)
    }

    @Test
    fun `AccountDetail creates route with id`() {
        val route = Screen.AccountDetail.createRoute("acc-101")
        assertEquals("accounts/acc-101", route)
    }

    @Test
    fun `bottomNavScreens contains only bottom nav screens`() {
        val bottomNavScreens = Screen.bottomNavScreens
        assertEquals(4, bottomNavScreens.size)
        assertTrue(bottomNavScreens.all { it.showInBottomNav })
        assertTrue(bottomNavScreens.contains(Screen.Dashboard))
        assertTrue(bottomNavScreens.contains(Screen.Transactions))
        assertTrue(bottomNavScreens.contains(Screen.FinancialResources))
        assertTrue(bottomNavScreens.contains(Screen.Settings))
    }

    @Test
    fun `allScreens contains all screens`() {
        val allScreens = Screen.allScreens
        assertTrue(allScreens.size >= 13)
        assertTrue(allScreens.contains(Screen.Dashboard))
        assertTrue(allScreens.contains(Screen.TransactionDetail))
        assertTrue(allScreens.contains(Screen.Settings))
    }

    @Test
    fun `fromRoute finds simple routes`() {
        assertEquals(Screen.Dashboard, Screen.fromRoute("dashboard"))
        assertEquals(Screen.Transactions, Screen.fromRoute("transactions"))
        assertEquals(Screen.Settings, Screen.fromRoute("settings"))
    }

    @Test
    fun `fromRoute finds parameterized routes`() {
        val screen = Screen.fromRoute("transactions/tx-123")
        assertEquals(Screen.TransactionDetail, screen)
    }

    @Test
    fun `fromRoute returns null for unknown routes`() {
        assertNull(Screen.fromRoute("unknown-route"))
        assertNull(Screen.fromRoute(""))
    }

    @Test
    fun `Import screen has correct properties`() {
        assertEquals("import", Screen.Import.route)
        assertEquals("Import", Screen.Import.title)
        assertTrue(Screen.Import.showInBottomNav)
    }

    @Test
    fun `Review screen does not show in bottom nav`() {
        assertEquals("review", Screen.Review.route)
        assertFalse(Screen.Review.showInBottomNav)
    }

    @Test
    fun `Search screen does not show in bottom nav`() {
        assertEquals("search", Screen.Search.route)
        assertFalse(Screen.Search.showInBottomNav)
    }

    @Test
    fun `Reports screen does not show in bottom nav`() {
        assertEquals("reports", Screen.Reports.route)
        assertFalse(Screen.Reports.showInBottomNav)
    }
}
