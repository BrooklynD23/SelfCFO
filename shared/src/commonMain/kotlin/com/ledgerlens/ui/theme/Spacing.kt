package com.ledgerlens.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * LedgerLens spacing system based on 8dp grid.
 * Consistent spacing values for margins, padding, and gaps.
 */
@Immutable
data class LedgerLensSpacing(
    val none: Dp = 0.dp,
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val huge: Dp = 48.dp,
    val massive: Dp = 64.dp
) {
    companion object {
        val Default = LedgerLensSpacing()
    }
}

/**
 * Common spacing patterns for consistent layouts.
 */
object SpacingPatterns {
    val screenPaddingHorizontal: Dp = 16.dp
    val screenPaddingVertical: Dp = 16.dp
    
    val cardPadding: Dp = 16.dp
    val cardMargin: Dp = 8.dp
    
    val listItemPadding: Dp = 16.dp
    val listItemSpacing: Dp = 8.dp
    
    val buttonPaddingHorizontal: Dp = 24.dp
    val buttonPaddingVertical: Dp = 12.dp
    
    val chipPaddingHorizontal: Dp = 12.dp
    val chipPaddingVertical: Dp = 6.dp
    
    val iconTextGap: Dp = 8.dp
    val sectionGap: Dp = 24.dp
    
    val dialogPadding: Dp = 24.dp
    val bottomSheetPadding: Dp = 16.dp
}

/**
 * Elevation values for consistent depth.
 */
object Elevation {
    val none: Dp = 0.dp
    val level1: Dp = 1.dp
    val level2: Dp = 3.dp
    val level3: Dp = 6.dp
    val level4: Dp = 8.dp
    val level5: Dp = 12.dp
    
    val card: Dp = level1
    val cardHovered: Dp = level2
    val dialog: Dp = level3
    val bottomSheet: Dp = level4
    val navigation: Dp = level2
}
