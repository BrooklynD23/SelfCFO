# 01: Design System

## Overview

Implement the foundational design system including theme, colors, typography, spacing, and reusable UI components.

---

## Implementation Steps

### Step 1: Color Palette

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/theme/Color.kt
package com.ledgerlens.ui.theme

import androidx.compose.ui.graphics.Color

// Primary palette - Teal/Green for financial trust
val Primary = Color(0xFF00897B)
val PrimaryVariant = Color(0xFF00695C)
val PrimaryLight = Color(0xFF4DB6AC)

// Secondary palette - Amber for accents
val Secondary = Color(0xFFFFB300)
val SecondaryVariant = Color(0xFFFFA000)

// Semantic colors
val Income = Color(0xFF43A047)      // Green for positive
val Expense = Color(0xFFE53935)     // Red for negative
val Transfer = Color(0xFF1E88E5)    // Blue for transfers
val Pending = Color(0xFFFF9800)     // Orange for pending

// Category colors
val CategoryColors = listOf(
    Color(0xFF5C6BC0), // Indigo
    Color(0xFF26A69A), // Teal
    Color(0xFFEF5350), // Red
    Color(0xFF66BB6A), // Green
    Color(0xFFFFCA28), // Amber
    Color(0xFF42A5F5), // Blue
    Color(0xFFAB47BC), // Purple
    Color(0xFF78909C), // Blue Grey
    Color(0xFFFF7043), // Deep Orange
    Color(0xFF8D6E63), // Brown
)

// Light theme
val LightBackground = Color(0xFFFAFAFA)
val LightSurface = Color(0xFFFFFFFF)
val LightOnBackground = Color(0xFF212121)
val LightOnSurface = Color(0xFF212121)
val LightDivider = Color(0xFFE0E0E0)

// Dark theme
val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkOnBackground = Color(0xFFE0E0E0)
val DarkOnSurface = Color(0xFFE0E0E0)
val DarkDivider = Color(0xFF424242)

// Confidence indicators
fun confidenceColor(confidence: Float): Color = when {
    confidence >= 0.8f -> Color(0xFF43A047)  // High - Green
    confidence >= 0.5f -> Color(0xFFFF9800)  // Medium - Orange
    else -> Color(0xFFE53935)                // Low - Red
}
```

### Step 2: Typography

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/theme/Type.kt
package com.ledgerlens.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val LedgerLensTypography = Typography(
    // Display styles
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp
    ),

    // Headline styles
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),

    // Title styles
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // Body styles
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),

    // Label styles
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

// Money-specific typography
val MoneyLarge = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 32.sp,
    letterSpacing = (-0.5).sp
)

val MoneyMedium = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp
)

val MoneySmall = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp
)
```

### Step 3: Spacing & Dimensions

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/theme/Spacing.kt
package com.ledgerlens.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Spacing(
    val none: Dp = 0.dp,
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val huge: Dp = 48.dp
)

data class Dimensions(
    val iconSmall: Dp = 16.dp,
    val iconMedium: Dp = 24.dp,
    val iconLarge: Dp = 32.dp,

    val buttonHeight: Dp = 48.dp,
    val buttonHeightSmall: Dp = 36.dp,

    val cardElevation: Dp = 2.dp,
    val cardRadius: Dp = 12.dp,

    val chipHeight: Dp = 32.dp,

    val listItemHeight: Dp = 72.dp,
    val listItemHeightSmall: Dp = 56.dp,

    val bottomNavHeight: Dp = 80.dp,
    val appBarHeight: Dp = 64.dp,

    val maxContentWidth: Dp = 600.dp  // For desktop/tablet
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
val LocalDimensions = staticCompositionLocalOf { Dimensions() }
```

### Step 4: Theme

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/theme/Theme.kt
package com.ledgerlens.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = PrimaryLight,
    onPrimaryContainer = Color.Black,
    secondary = Secondary,
    onSecondary = Color.Black,
    secondaryContainer = SecondaryVariant,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = Color(0xFFF5F5F5),
    outline = LightDivider,
    error = Color(0xFFB00020),
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryLight,
    onPrimary = Color.Black,
    primaryContainer = Primary,
    onPrimaryContainer = Color.White,
    secondary = Secondary,
    onSecondary = Color.Black,
    secondaryContainer = SecondaryVariant,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = Color(0xFF2C2C2C),
    outline = DarkDivider,
    error = Color(0xFFCF6679),
    onError = Color.Black
)

@Composable
fun LedgerLensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalDimensions provides Dimensions()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LedgerLensTypography,
            content = content
        )
    }
}

// Convenience accessors
object LedgerLensTheme {
    val spacing: Spacing
        @Composable get() = LocalSpacing.current

    val dimensions: Dimensions
        @Composable get() = LocalDimensions.current
}
```

### Step 5: Core Components

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/components/MoneyText.kt
package com.ledgerlens.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.ledgerlens.domain.Money
import com.ledgerlens.ui.theme.*

@Composable
fun MoneyText(
    money: Money,
    modifier: Modifier = Modifier,
    style: TextStyle = MoneyMedium,
    showSign: Boolean = false,
    colorBySign: Boolean = true
) {
    val color = when {
        !colorBySign -> MaterialTheme.colorScheme.onSurface
        money.minorUnits > 0 -> Income
        money.minorUnits < 0 -> Expense
        else -> MaterialTheme.colorScheme.onSurface
    }

    val prefix = when {
        !showSign -> ""
        money.minorUnits > 0 -> "+"
        else -> ""
    }

    Text(
        text = "$prefix${money.formatForDisplay()}",
        style = style,
        color = color,
        modifier = modifier
    )
}

// shared/src/commonMain/kotlin/com/ledgerlens/ui/components/CategoryChip.kt
@Composable
fun CategoryChip(
    category: Category,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false
) {
    val backgroundColor = if (selected) {
        category.color?.toColor() ?: MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = if (selected) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        onClick = onClick ?: {}
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            category.icon?.let { icon ->
                Text(
                    text = icon,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Text(
                text = category.name,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
        }
    }
}

// shared/src/commonMain/kotlin/com/ledgerlens/ui/components/LedgerCard.kt
@Composable
fun LedgerCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val dimensions = LedgerLensTheme.dimensions

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(dimensions.cardRadius),
        elevation = CardDefaults.cardElevation(defaultElevation = dimensions.cardElevation),
        onClick = onClick ?: {},
        enabled = onClick != null
    ) {
        Column(content = content)
    }
}

// shared/src/commonMain/kotlin/com/ledgerlens/ui/components/ConfidenceIndicator.kt
@Composable
fun ConfidenceIndicator(
    confidence: Float,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    val color = confidenceColor(confidence)
    val label = when {
        confidence >= 0.8f -> "High"
        confidence >= 0.5f -> "Medium"
        else -> "Low"
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        if (showLabel) {
            Text(
                text = "$label (${(confidence * 100).toInt()}%)",
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

// shared/src/commonMain/kotlin/com/ledgerlens/ui/components/EmptyState.kt
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        action?.invoke()
    }
}

// shared/src/commonMain/kotlin/com/ledgerlens/ui/components/LoadingState.kt
@Composable
fun LoadingState(
    message: String = "Loading...",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator()
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// shared/src/commonMain/kotlin/com/ledgerlens/ui/components/ErrorState.kt
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Error,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        if (onRetry != null) {
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}
```

---

## Acceptance Criteria

- [ ] Light and dark color schemes defined
- [ ] Typography scale complete
- [ ] Spacing tokens available via CompositionLocal
- [ ] MoneyText displays with correct formatting and colors
- [ ] CategoryChip renders with category color
- [ ] Empty, loading, and error states reusable
- [ ] Theme applied consistently throughout app

---

## Testing

### Visual Tests
- Light/dark mode screenshots
- Component showcase screen
- Different money amounts (positive, negative, zero)

---

## Estimated Complexity

**Medium** - Foundational setup with many components.
