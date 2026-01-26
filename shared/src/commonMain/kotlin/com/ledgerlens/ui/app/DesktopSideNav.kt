package com.ledgerlens.ui.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.navigation.Screen
import com.ledgerlens.ui.theme.LedgerLensTheme
import com.ledgerlens.ui.theme.ShapePatterns

/**
 * Navigation item for side navigation.
 */
data class SideNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val badge: String? = null
)

/**
 * Default side navigation items.
 */
val defaultSideNavItems = listOf(
    SideNavItem(
        screen = Screen.Dashboard,
        label = "Dashboard",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    ),
    SideNavItem(
        screen = Screen.Transactions,
        label = "Transactions",
        selectedIcon = Icons.Filled.Receipt,
        unselectedIcon = Icons.Outlined.Receipt
    ),
    SideNavItem(
        screen = Screen.FinancialResources,
        label = "Learn",
        selectedIcon = Icons.Filled.School,
        unselectedIcon = Icons.Outlined.School
    ),
    SideNavItem(
        screen = Screen.Settings,
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
)

/**
 * Desktop side navigation with collapsible drawer.
 */
@Composable
fun DesktopSideNav(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    onExpandToggle: ((Boolean) -> Unit)? = null,
    navItems: List<SideNavItem> = defaultSideNavItems
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography
    val spacing = LedgerLensTheme.spacing

    val width = if (expanded) 240.dp else 72.dp

    Surface(
        modifier = modifier
            .width(width)
            .fillMaxHeight(),
        color = colors.surface,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = spacing.medium)
        ) {
            // Logo/Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (expanded) Arrangement.SpaceBetween else Arrangement.Center
            ) {
                if (expanded) {
                    Text(
                        text = "LedgerLens",
                        style = typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                }

                onExpandToggle?.let { toggle ->
                    IconButton(
                        onClick = { toggle(!expanded) }
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = colors.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.large))

            // Add button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.medium)
            ) {
                if (expanded) {
                    Button(
                        onClick = onAddClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        ),
                        shape = ShapePatterns.button
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Transaction")
                    }
                } else {
                    IconButton(
                        onClick = onAddClick,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(ShapePatterns.avatar)
                            .background(colors.primary)
                            .align(Alignment.Center)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Transaction",
                            tint = colors.onPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.large))

            Divider(
                modifier = Modifier.padding(horizontal = spacing.medium),
                color = colors.outlineVariant
            )

            Spacer(modifier = Modifier.height(spacing.medium))

            // Navigation items
            navItems.forEach { item ->
                SideNavItemRow(
                    item = item,
                    isSelected = currentScreen == item.screen,
                    expanded = expanded,
                    onClick = { onNavigate(item.screen) }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // User section at bottom (optional)
            Divider(
                modifier = Modifier.padding(horizontal = spacing.medium),
                color = colors.outlineVariant
            )

            Spacer(modifier = Modifier.height(spacing.medium))

            if (expanded) {
                Text(
                    text = "v1.0.0",
                    style = typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = spacing.medium)
                )
            }
        }
    }
}

@Composable
private fun SideNavItemRow(
    item: SideNavItem,
    isSelected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography
    val spacing = LedgerLensTheme.spacing

    val backgroundColor = if (isSelected) colors.primaryContainer.copy(alpha = 0.3f) else colors.surface
    val contentColor = if (isSelected) colors.primary else colors.onSurfaceVariant
    val icon = if (isSelected) item.selectedIcon else item.unselectedIcon

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.small, vertical = 2.dp)
            .clip(ShapePatterns.button)
            .background(backgroundColor)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(horizontal = spacing.medium, vertical = spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = item.label,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )

        if (expanded) {
            Spacer(modifier = Modifier.width(spacing.medium))
            Text(
                text = item.label,
                style = typography.bodyMedium,
                color = contentColor,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )

            item.badge?.let { badge ->
                Surface(
                    shape = ShapePatterns.chipSmall,
                    color = colors.primary
                ) {
                    Text(
                        text = badge,
                        style = typography.labelSmall,
                        color = colors.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Navigation rail for medium-sized screens (tablets).
 */
@Composable
fun NavigationRail(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
    navItems: List<SideNavItem> = defaultSideNavItems
) {
    DesktopSideNav(
        currentScreen = currentScreen,
        onNavigate = onNavigate,
        onAddClick = onAddClick,
        modifier = modifier,
        expanded = false,
        onExpandToggle = null,
        navItems = navItems
    )
}
