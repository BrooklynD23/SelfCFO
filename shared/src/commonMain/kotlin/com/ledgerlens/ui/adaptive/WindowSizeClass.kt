package com.ledgerlens.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Window size class for adaptive layouts.
 * Determines navigation style and layout configuration.
 */
enum class WindowSizeClass {
    /**
     * Compact: < 600dp width
     * Use bottom navigation, single-column layouts
     */
    COMPACT,

    /**
     * Medium: 600-840dp width
     * Use navigation rail, may show master-detail
     */
    MEDIUM,

    /**
     * Expanded: > 840dp width
     * Use permanent side navigation, multi-column layouts
     */
    EXPANDED
}

/**
 * Width breakpoints for size class determination.
 */
object WindowBreakpoints {
    val COMPACT_MAX = 600.dp
    val MEDIUM_MAX = 840.dp
}

/**
 * Determine window size class from width.
 */
fun calculateWindowSizeClass(widthDp: Dp): WindowSizeClass {
    return when {
        widthDp < WindowBreakpoints.COMPACT_MAX -> WindowSizeClass.COMPACT
        widthDp < WindowBreakpoints.MEDIUM_MAX -> WindowSizeClass.MEDIUM
        else -> WindowSizeClass.EXPANDED
    }
}

/**
 * Navigation mode based on window size class.
 */
enum class NavigationMode {
    BOTTOM_NAV,      // Compact: Bottom navigation bar
    NAVIGATION_RAIL, // Medium: Vertical rail on left
    SIDE_NAV         // Expanded: Full side navigation
}

/**
 * Get navigation mode for window size class.
 */
fun WindowSizeClass.toNavigationMode(): NavigationMode = when (this) {
    WindowSizeClass.COMPACT -> NavigationMode.BOTTOM_NAV
    WindowSizeClass.MEDIUM -> NavigationMode.NAVIGATION_RAIL
    WindowSizeClass.EXPANDED -> NavigationMode.SIDE_NAV
}

/**
 * Layout configuration for different window sizes.
 */
data class LayoutConfig(
    val windowSizeClass: WindowSizeClass,
    val navigationMode: NavigationMode,
    val showTwoColumns: Boolean,
    val contentMaxWidth: Dp?,
    val horizontalPadding: Dp
) {
    companion object {
        fun forCompact() = LayoutConfig(
            windowSizeClass = WindowSizeClass.COMPACT,
            navigationMode = NavigationMode.BOTTOM_NAV,
            showTwoColumns = false,
            contentMaxWidth = null,
            horizontalPadding = 16.dp
        )

        fun forMedium() = LayoutConfig(
            windowSizeClass = WindowSizeClass.MEDIUM,
            navigationMode = NavigationMode.NAVIGATION_RAIL,
            showTwoColumns = false,
            contentMaxWidth = 600.dp,
            horizontalPadding = 24.dp
        )

        fun forExpanded() = LayoutConfig(
            windowSizeClass = WindowSizeClass.EXPANDED,
            navigationMode = NavigationMode.SIDE_NAV,
            showTwoColumns = true,
            contentMaxWidth = 1200.dp,
            horizontalPadding = 32.dp
        )

        fun forWindowSize(windowSizeClass: WindowSizeClass): LayoutConfig = when (windowSizeClass) {
            WindowSizeClass.COMPACT -> forCompact()
            WindowSizeClass.MEDIUM -> forMedium()
            WindowSizeClass.EXPANDED -> forExpanded()
        }
    }
}

/**
 * Calculate layout configuration from window width.
 */
fun calculateLayoutConfig(widthDp: Dp): LayoutConfig {
    val sizeClass = calculateWindowSizeClass(widthDp)
    return LayoutConfig.forWindowSize(sizeClass)
}
