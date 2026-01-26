package com.ledgerlens.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.ledgerlens.ui.app.DesktopSideNav
import com.ledgerlens.ui.app.FloatingActionBottomNav
import com.ledgerlens.ui.app.NavigationRail
import com.ledgerlens.ui.navigation.Screen

/**
 * Adaptive scaffold that adjusts navigation based on window size.
 * - COMPACT: Bottom navigation with FAB
 * - MEDIUM: Navigation rail on left
 * - EXPANDED: Permanent side navigation
 */
@Composable
fun AdaptiveScaffold(
    layoutConfig: LayoutConfig,
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onFabClick: () -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    var sideNavExpanded by remember { mutableStateOf(true) }

    when (layoutConfig.navigationMode) {
        NavigationMode.BOTTOM_NAV -> {
            Scaffold(
                modifier = modifier,
                topBar = topBar,
                bottomBar = {
                    FloatingActionBottomNav(
                        currentScreen = currentScreen,
                        onNavigate = onNavigate,
                        onFabClick = onFabClick
                    )
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    content()
                }
            }
        }

        NavigationMode.NAVIGATION_RAIL -> {
            Row(modifier = modifier.fillMaxSize()) {
                NavigationRail(
                    currentScreen = currentScreen,
                    onNavigate = onNavigate,
                    onAddClick = onFabClick
                )

                Scaffold(
                    modifier = Modifier.weight(1f),
                    topBar = topBar
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = layoutConfig.contentMaxWidth ?: Dp.Unspecified)
                                .fillMaxHeight()
                        ) {
                            content()
                        }
                    }
                }
            }
        }

        NavigationMode.SIDE_NAV -> {
            Row(modifier = modifier.fillMaxSize()) {
                DesktopSideNav(
                    currentScreen = currentScreen,
                    onNavigate = onNavigate,
                    onAddClick = onFabClick,
                    expanded = sideNavExpanded,
                    onExpandToggle = { sideNavExpanded = it }
                )

                Scaffold(
                    modifier = Modifier.weight(1f),
                    topBar = topBar
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = layoutConfig.contentMaxWidth ?: Dp.Unspecified)
                                .fillMaxHeight()
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Simpler adaptive scaffold without FAB.
 */
@Composable
fun AdaptiveScaffoldSimple(
    layoutConfig: LayoutConfig,
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    AdaptiveScaffold(
        layoutConfig = layoutConfig,
        currentScreen = currentScreen,
        onNavigate = onNavigate,
        onFabClick = { /* No FAB action */ },
        modifier = modifier,
        topBar = topBar,
        content = content
    )
}
