package com.ledgerlens.ui.navigation

/**
 * Navigation actions interface for handling app navigation.
 * Provides a clean API for navigating between screens.
 */
interface NavigationActions {
    fun navigateTo(screen: Screen)
    fun navigateToRoute(route: String)
    fun navigateBack()
    fun navigateToRoot(screen: Screen)
    fun popUpTo(screen: Screen, inclusive: Boolean = false)
}

/**
 * Navigation state holder that tracks current navigation state.
 */
data class NavigationState(
    val currentRoute: String = Screen.Dashboard.route,
    val previousRoute: String? = null,
    val backStack: List<String> = listOf(Screen.Dashboard.route)
) {
    val currentScreen: Screen?
        get() = Screen.fromRoute(currentRoute)

    val canGoBack: Boolean
        get() = backStack.size > 1

    val isAtRoot: Boolean
        get() = backStack.size == 1 && Screen.bottomNavScreens.any { it.route == currentRoute }
}

/**
 * Simple navigation controller implementation for managing navigation state.
 * Can be wrapped by platform-specific navigation implementations.
 */
class NavigationController(
    initialScreen: Screen = Screen.Dashboard
) : NavigationActions {
    private var _state = NavigationState(
        currentRoute = initialScreen.route,
        backStack = listOf(initialScreen.route)
    )

    val state: NavigationState
        get() = _state

    private val listeners = mutableListOf<(NavigationState) -> Unit>()

    fun addListener(listener: (NavigationState) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (NavigationState) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it(_state) }
    }

    override fun navigateTo(screen: Screen) {
        navigateToRoute(screen.route)
    }

    override fun navigateToRoute(route: String) {
        _state = _state.copy(
            previousRoute = _state.currentRoute,
            currentRoute = route,
            backStack = _state.backStack + route
        )
        notifyListeners()
    }

    override fun navigateBack() {
        if (_state.backStack.size > 1) {
            val newBackStack = _state.backStack.dropLast(1)
            _state = _state.copy(
                previousRoute = _state.currentRoute,
                currentRoute = newBackStack.last(),
                backStack = newBackStack
            )
            notifyListeners()
        }
    }

    override fun navigateToRoot(screen: Screen) {
        _state = NavigationState(
            currentRoute = screen.route,
            previousRoute = _state.currentRoute,
            backStack = listOf(screen.route)
        )
        notifyListeners()
    }

    override fun popUpTo(screen: Screen, inclusive: Boolean) {
        val targetIndex = _state.backStack.indexOfFirst { it == screen.route }
        if (targetIndex >= 0) {
            val newBackStack = if (inclusive) {
                _state.backStack.take(targetIndex)
            } else {
                _state.backStack.take(targetIndex + 1)
            }
            if (newBackStack.isNotEmpty()) {
                _state = _state.copy(
                    previousRoute = _state.currentRoute,
                    currentRoute = newBackStack.last(),
                    backStack = newBackStack
                )
                notifyListeners()
            }
        }
    }

    // Convenience navigation methods
    fun navigateToTransactionDetail(transactionId: String) {
        navigateToRoute(Screen.TransactionDetail.createRoute(transactionId))
    }

    fun navigateToReceiptDetail(receiptId: String) {
        navigateToRoute(Screen.ReceiptDetail.createRoute(receiptId))
    }

    fun navigateToCategoryDetail(categoryId: String) {
        navigateToRoute(Screen.CategoryDetail.createRoute(categoryId))
    }

    fun navigateToAccountDetail(accountId: String) {
        navigateToRoute(Screen.AccountDetail.createRoute(accountId))
    }

    fun navigateToDashboard() {
        navigateToRoot(Screen.Dashboard)
    }

    fun navigateToTransactions() {
        navigateToRoot(Screen.Transactions)
    }

    fun navigateToImport() {
        navigateToRoot(Screen.Import)
    }

    fun navigateToCategories() {
        navigateToRoot(Screen.Categories)
    }

    fun navigateToSettings() {
        navigateToRoot(Screen.Settings)
    }

    fun navigateToSearch() {
        navigateTo(Screen.Search)
    }

    fun navigateToReports() {
        navigateTo(Screen.Reports)
    }

    fun navigateToReview() {
        navigateTo(Screen.Review)
    }

    fun navigateToReceipts() {
        navigateTo(Screen.Receipts)
    }
}
