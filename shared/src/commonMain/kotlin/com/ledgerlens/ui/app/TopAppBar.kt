package com.ledgerlens.ui.app

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.ledgerlens.ui.navigation.Screen

/**
 * Configuration for the top app bar.
 */
data class TopAppBarConfig(
    val title: String,
    val showBackButton: Boolean = false,
    val showSearchButton: Boolean = false,
    val showMenuButton: Boolean = false,
    val centerTitle: Boolean = false,
    val actions: @Composable (RowScope.() -> Unit)? = null
)

/**
 * Creates a TopAppBarConfig from the current screen.
 */
fun Screen.toTopAppBarConfig(
    showBackButton: Boolean = false,
    showSearchButton: Boolean = true,
    showMenuButton: Boolean = false,
    centerTitle: Boolean = false,
    actions: @Composable (RowScope.() -> Unit)? = null
): TopAppBarConfig = TopAppBarConfig(
    title = this.title,
    showBackButton = showBackButton,
    showSearchButton = showSearchButton,
    showMenuButton = showMenuButton,
    centerTitle = centerTitle,
    actions = actions
)

/**
 * LedgerLens top app bar composable.
 *
 * @param config Configuration for the app bar
 * @param onBackClick Callback when back button is clicked
 * @param onSearchClick Callback when search button is clicked
 * @param onMenuClick Callback when menu button is clicked
 * @param scrollBehavior Optional scroll behavior for collapsing
 * @param modifier Optional modifier
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerLensTopAppBar(
    config: TopAppBarConfig,
    onBackClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    modifier: Modifier = Modifier
) {
    // TODO: Replace with LedgerLensTheme.colorScheme when theme integration is complete
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (config.centerTitle) {
        CenteredTopAppBar(
            config = config,
            colors = colors,
            onBackClick = onBackClick,
            onSearchClick = onSearchClick,
            onMenuClick = onMenuClick,
            scrollBehavior = scrollBehavior,
            modifier = modifier
        )
    } else {
        StandardTopAppBar(
            config = config,
            colors = colors,
            onBackClick = onBackClick,
            onSearchClick = onSearchClick,
            onMenuClick = onMenuClick,
            scrollBehavior = scrollBehavior,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StandardTopAppBar(
    config: TopAppBarConfig,
    colors: TopAppBarColors,
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior?,
    modifier: Modifier
) {
    TopAppBar(
        title = {
            Text(
                text = config.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge
            )
        },
        navigationIcon = {
            if (config.showBackButton) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Navigate back"
                    )
                }
            }
        },
        actions = {
            config.actions?.invoke(this)
            if (config.showSearchButton) {
                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search"
                    )
                }
            }
            if (config.showMenuButton) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More options"
                    )
                }
            }
        },
        colors = colors,
        scrollBehavior = scrollBehavior,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CenteredTopAppBar(
    config: TopAppBarConfig,
    colors: TopAppBarColors,
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior?,
    modifier: Modifier
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = config.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge
            )
        },
        navigationIcon = {
            if (config.showBackButton) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Navigate back"
                    )
                }
            }
        },
        actions = {
            config.actions?.invoke(this)
            if (config.showSearchButton) {
                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search"
                    )
                }
            }
            if (config.showMenuButton) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More options"
                    )
                }
            }
        },
        colors = colors,
        scrollBehavior = scrollBehavior,
        modifier = modifier
    )
}

/**
 * Simple top app bar for screens that just need a title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleTopAppBar(title: String, modifier: Modifier = Modifier) {
    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        // TODO: Replace with LedgerLensTheme.colorScheme when available
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
    )
}

/**
 * Top app bar with back navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackNavigationTopAppBar(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable (RowScope.() -> Unit) = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Navigate back"
                )
            }
        },
        actions = actions,
        // TODO: Replace with LedgerLensTheme.colorScheme when available
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
    )
}
