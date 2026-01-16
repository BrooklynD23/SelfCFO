package com.ledgerlens.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Composition locals for LedgerLens theme components.
 */
val LocalLedgerLensColors = staticCompositionLocalOf { LightColorScheme }
val LocalLedgerLensTypography = staticCompositionLocalOf { DefaultTypography }
val LocalLedgerLensSpacing = staticCompositionLocalOf { LedgerLensSpacing.Default }
val LocalLedgerLensShapes = staticCompositionLocalOf { LedgerLensShapes.Default }

/**
 * LedgerLens theme wrapper providing consistent styling across the app.
 * Supports light/dark mode and provides access to custom design tokens.
 *
 * @param darkTheme Whether to use dark theme. Defaults to system setting.
 * @param content The composable content to theme.
 */
@Composable
fun LedgerLensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val materialColorScheme = if (darkTheme) {
        darkColorScheme(
            primary = colorScheme.primary,
            onPrimary = colorScheme.onPrimary,
            primaryContainer = colorScheme.primaryContainer,
            onPrimaryContainer = colorScheme.onPrimaryContainer,
            secondary = colorScheme.secondary,
            onSecondary = colorScheme.onSecondary,
            secondaryContainer = colorScheme.secondaryContainer,
            onSecondaryContainer = colorScheme.onSecondaryContainer,
            tertiary = colorScheme.tertiary,
            onTertiary = colorScheme.onTertiary,
            tertiaryContainer = colorScheme.tertiaryContainer,
            onTertiaryContainer = colorScheme.onTertiaryContainer,
            error = colorScheme.error,
            onError = colorScheme.onError,
            errorContainer = colorScheme.errorContainer,
            onErrorContainer = colorScheme.onErrorContainer,
            background = colorScheme.background,
            onBackground = colorScheme.onBackground,
            surface = colorScheme.surface,
            onSurface = colorScheme.onSurface,
            surfaceVariant = colorScheme.surfaceVariant,
            onSurfaceVariant = colorScheme.onSurfaceVariant,
            outline = colorScheme.outline,
            outlineVariant = colorScheme.outlineVariant
        )
    } else {
        lightColorScheme(
            primary = colorScheme.primary,
            onPrimary = colorScheme.onPrimary,
            primaryContainer = colorScheme.primaryContainer,
            onPrimaryContainer = colorScheme.onPrimaryContainer,
            secondary = colorScheme.secondary,
            onSecondary = colorScheme.onSecondary,
            secondaryContainer = colorScheme.secondaryContainer,
            onSecondaryContainer = colorScheme.onSecondaryContainer,
            tertiary = colorScheme.tertiary,
            onTertiary = colorScheme.onTertiary,
            tertiaryContainer = colorScheme.tertiaryContainer,
            onTertiaryContainer = colorScheme.onTertiaryContainer,
            error = colorScheme.error,
            onError = colorScheme.onError,
            errorContainer = colorScheme.errorContainer,
            onErrorContainer = colorScheme.onErrorContainer,
            background = colorScheme.background,
            onBackground = colorScheme.onBackground,
            surface = colorScheme.surface,
            onSurface = colorScheme.onSurface,
            surfaceVariant = colorScheme.surfaceVariant,
            onSurfaceVariant = colorScheme.onSurfaceVariant,
            outline = colorScheme.outline,
            outlineVariant = colorScheme.outlineVariant
        )
    }

    CompositionLocalProvider(
        LocalLedgerLensColors provides colorScheme,
        LocalLedgerLensTypography provides DefaultTypography,
        LocalLedgerLensSpacing provides LedgerLensSpacing.Default,
        LocalLedgerLensShapes provides LedgerLensShapes.Default
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            content = content
        )
    }
}

/**
 * Accessor object for LedgerLens theme components.
 * Use this to access custom design tokens within composables.
 *
 * Example:
 * ```
 * val spacing = LedgerLensTheme.spacing.medium
 * val positiveColor = LedgerLensTheme.colors.moneyPositive
 * ```
 */
object LedgerLensTheme {
    val colors: LedgerLensColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalLedgerLensColors.current

    val typography: LedgerLensTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalLedgerLensTypography.current

    val spacing: LedgerLensSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalLedgerLensSpacing.current

    val shapes: LedgerLensShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalLedgerLensShapes.current
}
