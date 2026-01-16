package com.ledgerlens.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class NavGraphTest {

    @Test
    fun `startDestination is Dashboard`() {
        assertEquals(Screen.Dashboard, NavGraph.startDestination)
    }

    @Test
    fun `persistentScreens contains expected screens`() {
        assertTrue(NavGraph.persistentScreens.contains(Screen.Dashboard))
        assertTrue(NavGraph.persistentScreens.contains(Screen.Transactions))
        assertTrue(NavGraph.persistentScreens.contains(Screen.Categories))
    }

    @Test
    fun `protectedScreens contains all screens`() {
        Screen.allScreens.forEach { screen ->
            assertTrue(NavGraph.protectedScreens.contains(screen))
        }
    }

    @Test
    fun `getParentScreen returns parent for detail screens`() {
        assertEquals(Screen.Transactions, NavGraph.getParentScreen(Screen.TransactionDetail))
        assertEquals(Screen.Receipts, NavGraph.getParentScreen(Screen.ReceiptDetail))
        assertEquals(Screen.Categories, NavGraph.getParentScreen(Screen.CategoryDetail))
        assertEquals(Screen.Dashboard, NavGraph.getParentScreen(Screen.AccountDetail))
        assertEquals(Screen.Import, NavGraph.getParentScreen(Screen.Review))
    }

    @Test
    fun `getParentScreen returns null for root screens`() {
        assertNull(NavGraph.getParentScreen(Screen.Dashboard))
        assertNull(NavGraph.getParentScreen(Screen.Transactions))
        assertNull(NavGraph.getParentScreen(Screen.Settings))
    }

    @Test
    fun `shouldShowBottomNav returns true for main screens`() {
        assertTrue(NavGraph.shouldShowBottomNav(Screen.Dashboard))
        assertTrue(NavGraph.shouldShowBottomNav(Screen.Transactions))
        assertTrue(NavGraph.shouldShowBottomNav(Screen.Import))
        assertTrue(NavGraph.shouldShowBottomNav(Screen.Categories))
        assertTrue(NavGraph.shouldShowBottomNav(Screen.Settings))
    }

    @Test
    fun `shouldShowBottomNav returns false for detail screens`() {
        assertFalse(NavGraph.shouldShowBottomNav(Screen.TransactionDetail))
        assertFalse(NavGraph.shouldShowBottomNav(Screen.ReceiptDetail))
        assertFalse(NavGraph.shouldShowBottomNav(Screen.CategoryDetail))
        assertFalse(NavGraph.shouldShowBottomNav(Screen.Search))
        assertFalse(NavGraph.shouldShowBottomNav(Screen.Reports))
    }

    @Test
    fun `shouldShowBackButton returns false for main screens`() {
        assertFalse(NavGraph.shouldShowBackButton(Screen.Dashboard))
        assertFalse(NavGraph.shouldShowBackButton(Screen.Transactions))
        assertFalse(NavGraph.shouldShowBackButton(Screen.Import))
        assertFalse(NavGraph.shouldShowBackButton(Screen.Categories))
        assertFalse(NavGraph.shouldShowBackButton(Screen.Settings))
    }

    @Test
    fun `shouldShowBackButton returns true for detail screens`() {
        assertTrue(NavGraph.shouldShowBackButton(Screen.TransactionDetail))
        assertTrue(NavGraph.shouldShowBackButton(Screen.ReceiptDetail))
        assertTrue(NavGraph.shouldShowBackButton(Screen.CategoryDetail))
        assertTrue(NavGraph.shouldShowBackButton(Screen.Search))
        assertTrue(NavGraph.shouldShowBackButton(Screen.Reports))
        assertTrue(NavGraph.shouldShowBackButton(Screen.Review))
    }
}

class DeepLinksTest {

    @Test
    fun `createUri generates correct URI`() {
        assertEquals("ledgerlens://app/dashboard", DeepLinks.createUri(Screen.Dashboard))
        assertEquals("ledgerlens://app/transactions", DeepLinks.createUri(Screen.Transactions))
        assertEquals("ledgerlens://app/settings", DeepLinks.createUri(Screen.Settings))
    }

    @Test
    fun `parseUri extracts screen from valid URI`() {
        assertEquals(Screen.Dashboard, DeepLinks.parseUri("ledgerlens://app/dashboard"))
        assertEquals(Screen.Transactions, DeepLinks.parseUri("ledgerlens://app/transactions"))
        assertEquals(Screen.Settings, DeepLinks.parseUri("ledgerlens://app/settings"))
    }

    @Test
    fun `parseUri returns null for invalid URI`() {
        assertNull(DeepLinks.parseUri("https://example.com/dashboard"))
        assertNull(DeepLinks.parseUri("ledgerlens://wrong/dashboard"))
        assertNull(DeepLinks.parseUri(""))
    }

    @Test
    fun `Patterns are correctly defined`() {
        assertEquals("ledgerlens://app/transactions/{id}", DeepLinks.Patterns.TRANSACTION)
        assertEquals("ledgerlens://app/receipts/{id}", DeepLinks.Patterns.RECEIPT)
        assertEquals("ledgerlens://app/categories/{id}", DeepLinks.Patterns.CATEGORY)
        assertEquals("ledgerlens://app/dashboard", DeepLinks.Patterns.DASHBOARD)
        assertEquals("ledgerlens://app/import", DeepLinks.Patterns.IMPORT)
    }
}

class NavGraphBuilderTest {

    @Test
    fun `navGraph DSL builds destinations`() {
        val destinations = navGraph {
            composable(Screen.Dashboard) { }
            composable(Screen.Transactions) { }
        }

        assertEquals(2, destinations.size)
        assertNotNull(destinations[Screen.Dashboard.route])
        assertNotNull(destinations[Screen.Transactions.route])
    }

    @Test
    fun `NavDestination contains correct screen`() {
        val destinations = navGraph {
            composable(Screen.Dashboard) { }
        }

        val destination = destinations[Screen.Dashboard.route]
        assertNotNull(destination)
        assertEquals(Screen.Dashboard, destination.screen)
    }
}
