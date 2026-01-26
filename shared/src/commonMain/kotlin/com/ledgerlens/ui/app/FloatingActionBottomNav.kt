package com.ledgerlens.ui.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.navigation.Screen
import com.ledgerlens.ui.theme.LedgerLensTheme
import com.ledgerlens.ui.theme.ShapePatterns

/**
 * Navigation item for the bottom nav bar.
 */
data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

/**
 * Default navigation items for the app.
 */
val defaultNavItems = listOf(
    BottomNavItem(
        screen = Screen.Dashboard,
        label = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    ),
    BottomNavItem(
        screen = Screen.Transactions,
        label = "Transactions",
        selectedIcon = Icons.Filled.Receipt,
        unselectedIcon = Icons.Outlined.Receipt
    ),
    // Spacer for FAB
    BottomNavItem(
        screen = Screen.FinancialResources,
        label = "Learn",
        selectedIcon = Icons.Filled.School,
        unselectedIcon = Icons.Outlined.School
    ),
    BottomNavItem(
        screen = Screen.Settings,
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
)

/**
 * Bottom navigation bar with floating action button in the center.
 * 4-tab layout with center FAB for primary action (add transaction/receipt).
 */
@Composable
fun FloatingActionBottomNav(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onFabClick: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    navItems: List<BottomNavItem> = defaultNavItems
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it }
    ) {
        Box(
            modifier = modifier.fillMaxWidth()
        ) {
            // Bottom bar surface
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                color = colors.surface,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // First two items
                    navItems.take(2).forEach { item ->
                        NavItem(
                            item = item,
                            isSelected = currentScreen == item.screen,
                            onClick = { onNavigate(item.screen) }
                        )
                    }

                    // Spacer for FAB
                    Spacer(modifier = Modifier.size(56.dp))

                    // Last two items
                    navItems.drop(2).forEach { item ->
                        NavItem(
                            item = item,
                            isSelected = currentScreen == item.screen,
                            onClick = { onNavigate(item.screen) }
                        )
                    }
                }
            }

            // Floating Action Button
            FloatingActionButton(
                onClick = onFabClick,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-20).dp),
                shape = CircleShape,
                containerColor = colors.primary,
                contentColor = colors.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    val contentColor = if (isSelected) colors.primary else colors.onSurfaceVariant
    val icon = if (isSelected) item.selectedIcon else item.unselectedIcon

    Column(
        modifier = Modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = item.label,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.label,
            style = typography.labelSmall,
            color = contentColor
        )
    }
}

/**
 * Simple bottom nav without FAB for screens that don't need it.
 */
@Composable
fun SimpleBottomNav(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
    navItems: List<BottomNavItem> = defaultNavItems
) {
    val colors = LedgerLensTheme.colors

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEach { item ->
                NavItem(
                    item = item,
                    isSelected = currentScreen == item.screen,
                    onClick = { onNavigate(item.screen) }
                )
            }
        }
    }
}
