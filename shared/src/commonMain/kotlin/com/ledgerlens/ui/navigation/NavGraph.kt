package com.ledgerlens.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Navigation graph configuration for LedgerLens.
 * Defines how screens are composed and connected.
 */
object NavGraph {
    /**
     * Default start destination for the app.
     */
    val startDestination: Screen = Screen.Dashboard

    /**
     * Screens that should maintain their state when navigating away.
     */
    val persistentScreens: Set<Screen> = setOf(
        Screen.Dashboard,
        Screen.Transactions,
        Screen.Categories
    )

    /**
     * Screens that require authentication.
     */
    val protectedScreens: Set<Screen> = Screen.allScreens.toSet()

    /**
     * Returns the parent screen for a given detail screen, if applicable.
     */
    fun getParentScreen(screen: Screen): Screen? = when (screen) {
        is Screen.TransactionDetail -> Screen.Transactions
        is Screen.ReceiptDetail -> Screen.Receipts
        is Screen.CategoryDetail -> Screen.Categories
        is Screen.AccountDetail -> Screen.Dashboard
        is Screen.Review -> Screen.Import
        else -> null
    }

    /**
     * Returns whether the given screen should show the bottom navigation bar.
     */
    fun shouldShowBottomNav(screen: Screen): Boolean = when (screen) {
        is Screen.Dashboard,
        is Screen.Transactions,
        is Screen.Import,
        is Screen.Categories,
        is Screen.Settings -> true
        else -> false
    }

    /**
     * Returns whether the given screen should show the back button in the top app bar.
     */
    fun shouldShowBackButton(screen: Screen): Boolean = when (screen) {
        is Screen.Dashboard,
        is Screen.Transactions,
        is Screen.Import,
        is Screen.Categories,
        is Screen.Settings -> false
        else -> true
    }
}

/**
 * Composable that observes navigation state and provides the current screen.
 */
@Composable
fun rememberNavigationState(
    controller: NavigationController
): NavigationState {
    var state by remember { mutableStateOf(controller.state) }

    DisposableEffect(controller) {
        val listener: (NavigationState) -> Unit = { newState ->
            state = newState
        }
        controller.addListener(listener)
        onDispose {
            controller.removeListener(listener)
        }
    }

    return state
}

/**
 * Navigation host composable that renders the current screen.
 * 
 * @param controller The navigation controller managing navigation state
 * @param screenContent Lambda that provides the content for each screen
 */
@Composable
fun NavHost(
    controller: NavigationController,
    screenContent: @Composable (Screen, NavigationActions) -> Unit
) {
    val navState = rememberNavigationState(controller)
    val currentScreen = navState.currentScreen ?: Screen.Dashboard

    screenContent(currentScreen, controller)
}

/**
 * Data class representing a navigation destination with associated content.
 */
data class NavDestination(
    val screen: Screen,
    val content: @Composable (NavigationActions) -> Unit
)

/**
 * Builder for creating navigation graph with composable destinations.
 */
class NavGraphBuilder {
    private val destinations = mutableMapOf<String, NavDestination>()

    fun composable(
        screen: Screen,
        content: @Composable (NavigationActions) -> Unit
    ) {
        destinations[screen.route] = NavDestination(screen, content)
    }

    fun build(): Map<String, NavDestination> = destinations.toMap()
}

/**
 * Creates a navigation graph using the DSL builder.
 */
fun navGraph(builder: NavGraphBuilder.() -> Unit): Map<String, NavDestination> {
    return NavGraphBuilder().apply(builder).build()
}

/**
 * Deep link configuration for the app.
 */
object DeepLinks {
    private const val SCHEME = "ledgerlens"
    private const val HOST = "app"

    fun createUri(screen: Screen): String = "$SCHEME://$HOST/${screen.route}"

    fun parseUri(uri: String): Screen? {
        val prefix = "$SCHEME://$HOST/"
        if (!uri.startsWith(prefix)) return null
        val route = uri.removePrefix(prefix)
        return Screen.fromRoute(route)
    }

    object Patterns {
        const val TRANSACTION = "$SCHEME://$HOST/transactions/{id}"
        const val RECEIPT = "$SCHEME://$HOST/receipts/{id}"
        const val CATEGORY = "$SCHEME://$HOST/categories/{id}"
        const val DASHBOARD = "$SCHEME://$HOST/dashboard"
        const val IMPORT = "$SCHEME://$HOST/import"
    }
}
