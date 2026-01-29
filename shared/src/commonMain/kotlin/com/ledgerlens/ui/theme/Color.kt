package com.ledgerlens.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * LedgerLens color palette.
 * StitchUI: Primary Blue (#3B82F6), modern slate surfaces
 * Legacy: Deep blue (#1565C0) for trust/finance
 */
object LedgerLensColors {
    // StitchUI Primary palette - Modern Blue
    val Primary = Color(0xFF3B82F6)
    val PrimaryLight = Color(0xFF60A5FA)
    val PrimaryDark = Color(0xFF2563EB)
    val OnPrimary = Color.White

    // Legacy primary (for backward compat)
    val LegacyPrimary = Color(0xFF1565C0)
    val LegacyPrimaryLight = Color(0xFF5E92F3)
    val LegacyPrimaryDark = Color(0xFF003C8F)

    // Secondary palette - Emerald (positive actions)
    val Secondary = Color(0xFF10B981)
    val SecondaryLight = Color(0xFF34D399)
    val SecondaryDark = Color(0xFF059669)
    val OnSecondary = Color.White

    // Tertiary palette - Purple
    val Tertiary = Color(0xFF8B5CF6)
    val TertiaryLight = Color(0xFFA78BFA)
    val TertiaryDark = Color(0xFF7C3AED)
    val OnTertiary = Color.White

    // Error palette
    val Error = Color(0xFFEF4444)
    val ErrorLight = Color(0xFFF87171)
    val ErrorDark = Color(0xFFDC2626)
    val OnError = Color.White

    // Success palette
    val Success = Color(0xFF22C55E)
    val SuccessLight = Color(0xFF4ADE80)
    val SuccessDark = Color(0xFF16A34A)
    val OnSuccess = Color.White

    // Warning palette
    val Warning = Color(0xFFF59E0B)
    val WarningLight = Color(0xFFFBBF24)
    val WarningDark = Color(0xFFD97706)
    val OnWarning = Color.White

    // StitchUI Slate Neutral palette - Light mode
    val SurfaceLight = Color(0xFFF8FAFC)
    val SurfaceVariantLight = Color(0xFFF1F5F9)
    val BackgroundLight = Color(0xFFFFFFFF)
    val OnSurfaceLight = Color(0xFF0F172A)
    val OnSurfaceVariantLight = Color(0xFF475569)
    val OutlineLight = Color(0xFFCBD5E1)
    val OutlineVariantLight = Color(0xFFE2E8F0)

    // StitchUI Slate Neutral palette - Dark mode
    val SurfaceDark = Color(0xFF0F172A)
    val SurfaceVariantDark = Color(0xFF1E293B)
    val BackgroundDark = Color(0xFF020617)
    val OnSurfaceDark = Color(0xFFF8FAFC)
    val OnSurfaceVariantDark = Color(0xFF94A3B8)
    val OutlineDark = Color(0xFF475569)
    val OutlineVariantDark = Color(0xFF334155)

    // StitchUI Insight Card colors
    val InsightCardBackground = Color(0xFF1E293B)
    val InsightCardBackgroundLight = Color(0xFFE0F2FE)
    val OnInsightCard = Color(0xFFF8FAFC)
    val OnInsightCardLight = Color(0xFF0369A1)

    // StitchUI Risk badge colors
    val RiskLow = Color(0xFF22C55E)
    val RiskMedium = Color(0xFFF59E0B)
    val RiskHigh = Color(0xFFEF4444)
    val OnRiskLow = Color.White
    val OnRiskMedium = Color.White
    val OnRiskHigh = Color.White

    // StitchUI Chart colors
    val ChartLine = Color(0xFF3B82F6)
    val ChartGradientStart = Color(0x803B82F6)
    val ChartGradientEnd = Color(0x003B82F6)
    val ChartGrid = Color(0xFFE2E8F0)
    val ChartGridDark = Color(0xFF334155)

    // StitchUI Pillar card colors
    val PillarSavings = Color(0xFF10B981)
    val PillarExpenses = Color(0xFFF59E0B)
    val PillarInvestments = Color(0xFF8B5CF6)
    val PillarDebt = Color(0xFFEF4444)

    // Money-specific colors (updated)
    val MoneyPositive = Color(0xFF22C55E)
    val MoneyNegative = Color(0xFFEF4444)
    val MoneyNeutral = Color(0xFF64748B)

    // Confidence level colors
    val ConfidenceVeryHigh = Color(0xFF15803D)
    val ConfidenceHigh = Color(0xFF22C55E)
    val ConfidenceMedium = Color(0xFFF59E0B)
    val ConfidenceLow = Color(0xFFF97316)
    val ConfidenceVeryLow = Color(0xFFEF4444)

    // Category chip colors (semantic)
    val CategoryFood = Color(0xFFF97316)
    val CategoryTransport = Color(0xFF3B82F6)
    val CategoryShopping = Color(0xFFA855F7)
    val CategoryEntertainment = Color(0xFFEC4899)
    val CategoryBills = Color(0xFF64748B)
    val CategoryHealth = Color(0xFF22C55E)
    val CategoryIncome = Color(0xFF14B8A6)
    val CategoryTransfer = Color(0xFF6366F1)
    val CategoryUncategorized = Color(0xFF9CA3AF)

    // Participant avatar colors (for bill splitting)
    val AvatarColors = listOf(
        Color(0xFF3B82F6), // Blue
        Color(0xFF10B981), // Emerald
        Color(0xFFF59E0B), // Amber
        Color(0xFFEF4444), // Red
        Color(0xFF8B5CF6), // Purple
        Color(0xFFEC4899), // Pink
        Color(0xFF14B8A6), // Teal
        Color(0xFFF97316) // Orange
    )
}

/**
 * Color scheme for LedgerLens with StitchUI enhancements.
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
    val isDark: Boolean,
    // StitchUI additions
    val insightCardBackground: Color,
    val onInsightCard: Color,
    val riskLow: Color,
    val riskMedium: Color,
    val riskHigh: Color,
    val chartLine: Color,
    val chartGradientStart: Color,
    val chartGradientEnd: Color,
    val chartGrid: Color,
    val pillarSavings: Color,
    val pillarExpenses: Color,
    val pillarInvestments: Color,
    val pillarDebt: Color
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
    isDark = false,
    // StitchUI additions
    insightCardBackground = LedgerLensColors.InsightCardBackgroundLight,
    onInsightCard = LedgerLensColors.OnInsightCardLight,
    riskLow = LedgerLensColors.RiskLow,
    riskMedium = LedgerLensColors.RiskMedium,
    riskHigh = LedgerLensColors.RiskHigh,
    chartLine = LedgerLensColors.ChartLine,
    chartGradientStart = LedgerLensColors.ChartGradientStart,
    chartGradientEnd = LedgerLensColors.ChartGradientEnd,
    chartGrid = LedgerLensColors.ChartGrid,
    pillarSavings = LedgerLensColors.PillarSavings,
    pillarExpenses = LedgerLensColors.PillarExpenses,
    pillarInvestments = LedgerLensColors.PillarInvestments,
    pillarDebt = LedgerLensColors.PillarDebt
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
    isDark = true,
    // StitchUI additions
    insightCardBackground = LedgerLensColors.InsightCardBackground,
    onInsightCard = LedgerLensColors.OnInsightCard,
    riskLow = LedgerLensColors.RiskLow,
    riskMedium = LedgerLensColors.RiskMedium,
    riskHigh = LedgerLensColors.RiskHigh,
    chartLine = LedgerLensColors.PrimaryLight,
    chartGradientStart = Color(0x8060A5FA),
    chartGradientEnd = Color(0x0060A5FA),
    chartGrid = LedgerLensColors.ChartGridDark,
    pillarSavings = LedgerLensColors.PillarSavings,
    pillarExpenses = LedgerLensColors.PillarExpenses,
    pillarInvestments = LedgerLensColors.PillarInvestments,
    pillarDebt = LedgerLensColors.PillarDebt
)
