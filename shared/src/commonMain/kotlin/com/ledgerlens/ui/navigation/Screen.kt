package com.ledgerlens.ui.navigation

/**
 * Sealed class defining all screens in the LedgerLens app.
 * Each screen has a route string used for navigation.
 */
sealed class Screen(
    val route: String,
    val title: String,
    val showInBottomNav: Boolean = false
) {
    // Main bottom navigation screens
    object Dashboard : Screen(
        route = "dashboard",
        title = "Dashboard",
        showInBottomNav = true
    )

    object Transactions : Screen(
        route = "transactions",
        title = "Transactions",
        showInBottomNav = true
    )

    object Import : Screen(
        route = "import",
        title = "Import",
        showInBottomNav = true
    )

    object Categories : Screen(
        route = "categories",
        title = "Categories",
        showInBottomNav = true
    )

    object Settings : Screen(
        route = "settings",
        title = "Settings",
        showInBottomNav = true
    )

    // Detail screens
    object TransactionDetail : Screen(
        route = "transactions/{${NavArgs.TRANSACTION_ID}}",
        title = "Transaction"
    ) {
        fun createRoute(transactionId: String): String = "transactions/$transactionId"
    }

    object Review : Screen(
        route = "review",
        title = "Review"
    )

    object Receipts : Screen(
        route = "receipts",
        title = "Receipts"
    )

    object ReceiptDetail : Screen(
        route = "receipts/{${NavArgs.RECEIPT_ID}}",
        title = "Receipt"
    ) {
        fun createRoute(receiptId: String): String = "receipts/$receiptId"
    }

    object CategoryDetail : Screen(
        route = "categories/{${NavArgs.CATEGORY_ID}}",
        title = "Category"
    ) {
        fun createRoute(categoryId: String): String = "categories/$categoryId"
    }

    object AccountDetail : Screen(
        route = "accounts/{${NavArgs.ACCOUNT_ID}}",
        title = "Account"
    ) {
        fun createRoute(accountId: String): String = "accounts/$accountId"
    }

    object Search : Screen(
        route = "search",
        title = "Search"
    )

    object Reports : Screen(
        route = "reports",
        title = "Reports"
    )

    // Financial Resources screens (StitchUI)
    object FinancialResources : Screen(
        route = "resources",
        title = "Learn",
        showInBottomNav = true
    )

    object IndexFundDeepDive : Screen(
        route = "resources/index-funds",
        title = "Index Funds"
    )

    object SavingsComparison : Screen(
        route = "resources/savings-comparison",
        title = "Compare Options"
    )

    object ConceptExplorer : Screen(
        route = "resources/concepts/{${NavArgs.CONCEPT_ID}}",
        title = "Concept"
    ) {
        fun createRoute(conceptId: String): String = "resources/concepts/$conceptId"
    }

    companion object {
        /**
         * Returns all screens that should be shown in the bottom navigation bar.
         * Uses lazy initialization to ensure nested objects are properly initialized.
         */
        val bottomNavScreens: List<Screen> by lazy {
            listOf(
                Dashboard,
                Transactions,
                FinancialResources,
                Settings
            )
        }

        /**
         * Returns all screens in the app.
         * Uses lazy initialization to ensure nested objects are properly initialized.
         */
        val allScreens: List<Screen> by lazy {
            listOf(
                Dashboard,
                Transactions,
                TransactionDetail,
                Import,
                Review,
                Receipts,
                ReceiptDetail,
                Categories,
                CategoryDetail,
                AccountDetail,
                Search,
                Reports,
                Settings,
                FinancialResources,
                IndexFundDeepDive,
                SavingsComparison,
                ConceptExplorer
            )
        }

        /**
         * Finds a screen by its route pattern.
         */
        fun fromRoute(route: String): Screen? {
            return allScreens.find { screen ->
                route == screen.route || route.matches(screen.route.toRouteRegex())
            }
        }

        private fun String.toRouteRegex(): Regex {
            return this.replace(Regex("\\{[^}]+\\}"), "[^/]+").toRegex()
        }
    }
}
