package com.ledgerlens.ui.app

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.navigation.Screen

/**
 * Bottom navigation bar data for each tab.
 */
data class BottomNavItem(
    val screen: Screen,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: String,
    val contentDescription: String
)

/**
 * Default bottom navigation items for LedgerLens.
 */
object BottomNavItems {
    val Dashboard = BottomNavItem(
        screen = Screen.Dashboard,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        label = "Dashboard",
        contentDescription = "Navigate to dashboard"
    )

    val Transactions = BottomNavItem(
        screen = Screen.Transactions,
        selectedIcon = Icons.Filled.List,
        unselectedIcon = Icons.Outlined.List,
        label = "Transactions",
        contentDescription = "Navigate to transactions"
    )

    val Import = BottomNavItem(
        screen = Screen.Import,
        selectedIcon = Icons.Filled.Add,
        unselectedIcon = Icons.Outlined.Add,
        label = "Import",
        contentDescription = "Import transactions"
    )

    // TODO: Replace with proper category icon from LedgerLens icon set
    val Categories = BottomNavItem(
        screen = Screen.Categories,
        selectedIcon = Icons.Filled.List,
        unselectedIcon = Icons.Outlined.List,
        label = "Categories",
        contentDescription = "Navigate to categories"
    )

    val Settings = BottomNavItem(
        screen = Screen.Settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        label = "Settings",
        contentDescription = "Navigate to settings"
    )

    val items = listOf(Dashboard, Transactions, Import, Categories, Settings)
}

/**
 * Bottom navigation bar composable for LedgerLens.
 *
 * @param currentRoute The currently active route
 * @param onNavigate Callback when a navigation item is clicked
 * @param modifier Optional modifier
 */
@Composable
fun BottomNavBar(currentRoute: String, onNavigate: (Screen) -> Unit, modifier: Modifier = Modifier) {
    // TODO: Replace with LedgerLensTheme.colorScheme when theme integration is complete
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        BottomNavItems.items.forEach { item ->
            BottomNavBarItem(
                item = item,
                selected = isRouteSelected(currentRoute, item.screen),
                onClick = { onNavigate(item.screen) }
            )
        }
    }
}

/**
 * Individual bottom navigation bar item.
 */
@Composable
private fun RowScope.BottomNavBarItem(item: BottomNavItem, selected: Boolean, onClick: () -> Unit) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                contentDescription = item.contentDescription,
                modifier = Modifier.size(24.dp)
            )
        },
        label = {
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelSmall
            )
        },
        // TODO: Replace with LedgerLensTheme colors when available
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}

/**
 * Determines if a route matches the given screen, handling parameterized routes.
 */
private fun isRouteSelected(currentRoute: String, screen: Screen): Boolean {
    // Direct match
    if (currentRoute == screen.route) return true

    // Check if current route starts with the base route for detail screens
    val baseRoute = screen.route.split("/").firstOrNull() ?: return false
    val currentBase = currentRoute.split("/").firstOrNull() ?: return false

    return baseRoute == currentBase
}
