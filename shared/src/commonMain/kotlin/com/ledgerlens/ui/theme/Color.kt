package com.ledgerlens.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * LedgerLens color palette.
 * Primary: Deep blue (#1565C0) for trust/finance
 * Accent: Teal (#00897B) for positive actions
 */
object LedgerLensColors {
    // Primary palette - Deep Blue (trust/finance)
    val Primary = Color(0xFF1565C0)
    val PrimaryLight = Color(0xFF5E92F3)
    val PrimaryDark = Color(0xFF003C8F)
    val OnPrimary = Color.White

    // Secondary palette - Teal (positive actions)
    val Secondary = Color(0xFF00897B)
    val SecondaryLight = Color(0xFF4EBAAA)
    val SecondaryDark = Color(0xFF005B4F)
    val OnSecondary = Color.White

    // Tertiary palette
    val Tertiary = Color(0xFF7C4DFF)
    val TertiaryLight = Color(0xFFB47CFF)
    val TertiaryDark = Color(0xFF3F1DCB)
    val OnTertiary = Color.White

    // Error palette
    val Error = Color(0xFFD32F2F)
    val ErrorLight = Color(0xFFFF6659)
    val ErrorDark = Color(0xFF9A0007)
    val OnError = Color.White

    // Success palette
    val Success = Color(0xFF388E3C)
    val SuccessLight = Color(0xFF6ABF69)
    val SuccessDark = Color(0xFF00600F)
    val OnSuccess = Color.White

    // Warning palette
    val Warning = Color(0xFFF57C00)
    val WarningLight = Color(0xFFFFAD42)
    val WarningDark = Color(0xFFBB4D00)
    val OnWarning = Color.White

    // Neutral palette - Light mode
    val SurfaceLight = Color(0xFFFFFBFE)
    val SurfaceVariantLight = Color(0xFFE7E0EC)
    val BackgroundLight = Color(0xFFFFFBFE)
    val OnSurfaceLight = Color(0xFF1C1B1F)
    val OnSurfaceVariantLight = Color(0xFF49454F)
    val OutlineLight = Color(0xFF79747E)
    val OutlineVariantLight = Color(0xFFCAC4D0)

    // Neutral palette - Dark mode
    val SurfaceDark = Color(0xFF1C1B1F)
    val SurfaceVariantDark = Color(0xFF49454F)
    val BackgroundDark = Color(0xFF1C1B1F)
    val OnSurfaceDark = Color(0xFFE6E1E5)
    val OnSurfaceVariantDark = Color(0xFFCAC4D0)
    val OutlineDark = Color(0xFF938F99)
    val OutlineVariantDark = Color(0xFF49454F)

    // Money-specific colors
    val MoneyPositive = Color(0xFF2E7D32)
    val MoneyNegative = Color(0xFFC62828)
    val MoneyNeutral = Color(0xFF616161)

    // Confidence level colors
    val ConfidenceVeryHigh = Color(0xFF1B5E20)
    val ConfidenceHigh = Color(0xFF388E3C)
    val ConfidenceMedium = Color(0xFFF9A825)
    val ConfidenceLow = Color(0xFFEF6C00)
    val ConfidenceVeryLow = Color(0xFFD32F2F)

    // Category chip colors (semantic)
    val CategoryFood = Color(0xFFFF7043)
    val CategoryTransport = Color(0xFF42A5F5)
    val CategoryShopping = Color(0xFFAB47BC)
    val CategoryEntertainment = Color(0xFFEC407A)
    val CategoryBills = Color(0xFF78909C)
    val CategoryHealth = Color(0xFF66BB6A)
    val CategoryIncome = Color(0xFF26A69A)
    val CategoryTransfer = Color(0xFF5C6BC0)
    val CategoryUncategorized = Color(0xFF9E9E9E)
}

/**
 * Light color scheme for LedgerLens.
 */
data class LedgerLensColorScheme(
    val primary: Color,
    val primaryContainer: Color,
    val onPrimary: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val secondaryContainer: Color,
    val onSecondary: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val tertiaryContainer: Color,
    val onTertiary: Color,
    val onTertiaryContainer: Color,
    val error: Color,
    val errorContainer: Color,
    val onError: Color,
    val onErrorContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    val moneyPositive: Color,
    val moneyNegative: Color,
    val moneyNeutral: Color,
    val isDark: Boolean
)

val LightColorScheme = LedgerLensColorScheme(
    primary = LedgerLensColors.Primary,
    primaryContainer = LedgerLensColors.PrimaryLight,
    onPrimary = LedgerLensColors.OnPrimary,
    onPrimaryContainer = LedgerLensColors.PrimaryDark,
    secondary = LedgerLensColors.Secondary,
    secondaryContainer = LedgerLensColors.SecondaryLight,
    onSecondary = LedgerLensColors.OnSecondary,
    onSecondaryContainer = LedgerLensColors.SecondaryDark,
    tertiary = LedgerLensColors.Tertiary,
    tertiaryContainer = LedgerLensColors.TertiaryLight,
    onTertiary = LedgerLensColors.OnTertiary,
    onTertiaryContainer = LedgerLensColors.TertiaryDark,
    error = LedgerLensColors.Error,
    errorContainer = LedgerLensColors.ErrorLight,
    onError = LedgerLensColors.OnError,
    onErrorContainer = LedgerLensColors.ErrorDark,
    background = LedgerLensColors.BackgroundLight,
    onBackground = LedgerLensColors.OnSurfaceLight,
    surface = LedgerLensColors.SurfaceLight,
    surfaceVariant = LedgerLensColors.SurfaceVariantLight,
    onSurface = LedgerLensColors.OnSurfaceLight,
    onSurfaceVariant = LedgerLensColors.OnSurfaceVariantLight,
    outline = LedgerLensColors.OutlineLight,
    outlineVariant = LedgerLensColors.OutlineVariantLight,
    success = LedgerLensColors.Success,
    onSuccess = LedgerLensColors.OnSuccess,
    warning = LedgerLensColors.Warning,
    onWarning = LedgerLensColors.OnWarning,
    moneyPositive = LedgerLensColors.MoneyPositive,
    moneyNegative = LedgerLensColors.MoneyNegative,
    moneyNeutral = LedgerLensColors.MoneyNeutral,
    isDark = false
)

val DarkColorScheme = LedgerLensColorScheme(
    primary = LedgerLensColors.PrimaryLight,
    primaryContainer = LedgerLensColors.PrimaryDark,
    onPrimary = LedgerLensColors.PrimaryDark,
    onPrimaryContainer = LedgerLensColors.PrimaryLight,
    secondary = LedgerLensColors.SecondaryLight,
    secondaryContainer = LedgerLensColors.SecondaryDark,
    onSecondary = LedgerLensColors.SecondaryDark,
    onSecondaryContainer = LedgerLensColors.SecondaryLight,
    tertiary = LedgerLensColors.TertiaryLight,
    tertiaryContainer = LedgerLensColors.TertiaryDark,
    onTertiary = LedgerLensColors.TertiaryDark,
    onTertiaryContainer = LedgerLensColors.TertiaryLight,
    error = LedgerLensColors.ErrorLight,
    errorContainer = LedgerLensColors.ErrorDark,
    onError = LedgerLensColors.ErrorDark,
    onErrorContainer = LedgerLensColors.ErrorLight,
    background = LedgerLensColors.BackgroundDark,
    onBackground = LedgerLensColors.OnSurfaceDark,
    surface = LedgerLensColors.SurfaceDark,
    surfaceVariant = LedgerLensColors.SurfaceVariantDark,
    onSurface = LedgerLensColors.OnSurfaceDark,
    onSurfaceVariant = LedgerLensColors.OnSurfaceVariantDark,
    outline = LedgerLensColors.OutlineDark,
    outlineVariant = LedgerLensColors.OutlineVariantDark,
    success = LedgerLensColors.SuccessLight,
    onSuccess = LedgerLensColors.SuccessDark,
    warning = LedgerLensColors.WarningLight,
    onWarning = LedgerLensColors.WarningDark,
    moneyPositive = LedgerLensColors.SuccessLight,
    moneyNegative = LedgerLensColors.ErrorLight,
    moneyNeutral = LedgerLensColors.OnSurfaceVariantDark,
    isDark = true
)
