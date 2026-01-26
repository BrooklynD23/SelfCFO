package com.ledgerlens.ui.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.ledgerlens.ui.navigation.NavGraph
import com.ledgerlens.ui.navigation.NavigationActions
import com.ledgerlens.ui.navigation.Screen

/**
 * Main scaffold configuration for the app.
 */
data class ScaffoldConfig(
    val showTopBar: Boolean = true,
    val showBottomBar: Boolean = true,
    val topAppBarConfig: TopAppBarConfig? = null,
    val enableScrollBehavior: Boolean = false
)

/**
 * Creates a ScaffoldConfig based on the current screen.
 */
fun Screen.toScaffoldConfig(): ScaffoldConfig = ScaffoldConfig(
    // Most screens currently include their own `TopAppBar` inside their screen-level `Scaffold`.
    // Until we refactor screens to be "content-only", keep the global top bar off to avoid
    // double app bars.
    showTopBar = false,
    showBottomBar = NavGraph.shouldShowBottomNav(this),
    topAppBarConfig = this.toTopAppBarConfig(
        showBackButton = NavGraph.shouldShowBackButton(this)
    ),
    enableScrollBehavior = false
)

/**
 * Main scaffold composable that wraps all screens.
 *
 * @param currentScreen The currently displayed screen
 * @param navigationActions Navigation actions for handling navigation events
 * @param snackbarHostState State for showing snackbars
 * @param onSearchClick Callback when search is clicked
 * @param onMenuClick Callback when menu is clicked
 * @param content The screen content
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    currentScreen: Screen,
    navigationActions: NavigationActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onSearchClick: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val config = currentScreen.toScaffoldConfig()
    val scrollBehavior = if (config.enableScrollBehavior) {
        TopAppBarDefaults.pinnedScrollBehavior()
    } else {
        null
    }

    MainScaffoldContent(
        config = config,
        currentRoute = currentScreen.route,
        scrollBehavior = scrollBehavior,
        snackbarHostState = snackbarHostState,
        onBackClick = { navigationActions.navigateBack() },
        onNavigate = { screen -> navigationActions.navigateTo(screen) },
        onSearchClick = onSearchClick,
        onMenuClick = onMenuClick,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffoldContent(
    config: ScaffoldConfig,
    currentRoute: String,
    scrollBehavior: TopAppBarScrollBehavior?,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onNavigate: (Screen) -> Unit,
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val modifier = if (scrollBehavior != null) {
        Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    } else {
        Modifier
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (config.showTopBar && config.topAppBarConfig != null) {
                LedgerLensTopAppBar(
                    config = config.topAppBarConfig,
                    onBackClick = onBackClick,
                    onSearchClick = onSearchClick,
                    onMenuClick = onMenuClick,
                    scrollBehavior = scrollBehavior
                )
            }
        },
        bottomBar = {
            if (config.showBottomBar) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = onNavigate
                )
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        // TODO: Replace with LedgerLensTheme.colorScheme when available
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        content = content
    )
}

/**
 * Simplified scaffold for detail screens with back navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(
    title: String,
    onBackClick: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    actions: @Composable (androidx.compose.foundation.layout.RowScope.() -> Unit) = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            BackNavigationTopAppBar(
                title = title,
                onBackClick = onBackClick,
                actions = actions
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        // TODO: Replace with LedgerLensTheme.colorScheme when available
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        content = content
    )
}

/**
 * Scaffold without top or bottom bars, for full-screen content.
 */
@Composable
fun FullScreenScaffold(
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        // TODO: Replace with LedgerLensTheme.colorScheme when available
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        content = content
    )
}
