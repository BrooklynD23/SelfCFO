package com.ledgerlens.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ledgerlens.ui.navigation.NavHost
import com.ledgerlens.ui.navigation.NavigationActions
import com.ledgerlens.ui.navigation.NavigationController
import com.ledgerlens.ui.navigation.Screen
import com.ledgerlens.ui.navigation.rememberNavigationState

/**
 * CompositionLocal for providing navigation actions throughout the app.
 */
val LocalNavigationActions = compositionLocalOf<NavigationActions> {
    error("No NavigationActions provided")
}

/**
 * CompositionLocal for providing snackbar host state throughout the app.
 */
val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided")
}

/**
 * Root composable for the LedgerLens application.
 * Sets up navigation, theming, and the main scaffold structure.
 *
 * @param navigationController The navigation controller for the app
 * @param screenContent Lambda providing content for each screen
 */
@Composable
fun LedgerLensApp(
    navigationController: NavigationController = remember { NavigationController() },
    screenContent: @Composable (Screen) -> Unit = { screen -> PlaceholderScreen(screen) }
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val navState = rememberNavigationState(navigationController)
    val currentScreen = navState.currentScreen ?: Screen.Dashboard

    // TODO: Wrap with LedgerLensTheme when theme integration is complete
    CompositionLocalProvider(
        LocalNavigationActions provides navigationController,
        LocalSnackbarHostState provides snackbarHostState
    ) {
        MainScaffold(
            currentScreen = currentScreen,
            navigationActions = navigationController,
            snackbarHostState = snackbarHostState,
            onSearchClick = { navigationController.navigateToSearch() }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                screenContent(currentScreen)
            }
        }
    }
}

/**
 * LedgerLens app with navigation host for screen routing.
 * Use this when you want automatic screen switching based on the navigation graph.
 *
 * @param navigationController The navigation controller
 * @param screenRegistry Map of screens to their composable content
 */
@Composable
fun LedgerLensAppWithNavHost(
    navigationController: NavigationController = remember { NavigationController() },
    screenRegistry: Map<Screen, @Composable () -> Unit> = emptyMap()
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // TODO: Wrap with LedgerLensTheme when theme integration is complete
    CompositionLocalProvider(
        LocalNavigationActions provides navigationController,
        LocalSnackbarHostState provides snackbarHostState
    ) {
        NavHost(controller = navigationController) { screen, navActions ->
            MainScaffold(
                currentScreen = screen,
                navigationActions = navActions,
                snackbarHostState = snackbarHostState,
                onSearchClick = { navigationController.navigateToSearch() }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    val content = screenRegistry[screen]
                    if (content != null) {
                        content()
                    } else {
                        PlaceholderScreen(screen)
                    }
                }
            }
        }
    }
}

/**
 * Placeholder screen shown when a screen hasn't been implemented yet.
 */
@Composable
fun PlaceholderScreen(screen: Screen) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${screen.title} Screen\n(Coming Soon)",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Creates a screen registry builder for registering screen content.
 */
class ScreenRegistryBuilder {
    private val registry = mutableMapOf<Screen, @Composable () -> Unit>()

    fun screen(screen: Screen, content: @Composable () -> Unit) {
        registry[screen] = content
    }

    fun build(): Map<Screen, @Composable () -> Unit> = registry.toMap()
}

/**
 * DSL for building a screen registry.
 */
fun screenRegistry(builder: ScreenRegistryBuilder.() -> Unit): Map<Screen, @Composable () -> Unit> {
    return ScreenRegistryBuilder().apply(builder).build()
}

/**
 * AppState holder for managing app-wide state.
 */
data class AppState(
    val isInitialized: Boolean = false,
    val isAuthenticated: Boolean = false,
    val hasCompletedOnboarding: Boolean = false
)

/**
 * CompositionLocal for app state.
 */
val LocalAppState = compositionLocalOf { AppState() }
