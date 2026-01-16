package com.ledgerlens.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ledgerlens.categorization.ConfidenceLevel
import com.ledgerlens.ui.theme.LedgerLensColors
import com.ledgerlens.ui.theme.LedgerLensTheme
import com.ledgerlens.ui.theme.ShapePatterns
import com.ledgerlens.ui.theme.SpacingPatterns

/**
 * Display style for confidence badge.
 */
enum class ConfidenceBadgeStyle {
    DOT,
    PILL,
    TEXT_ONLY,
    FULL
}

/**
 * Confidence level indicator component.
 * Shows visual feedback for classification confidence.
 *
 * @param confidenceLevel The confidence level to display.
 * @param modifier Modifier for the badge.
 * @param style Display style (DOT, PILL, TEXT_ONLY, FULL).
 * @param showLabel Whether to show text label (for DOT and PILL styles).
 */
@Composable
fun ConfidenceBadge(
    confidenceLevel: ConfidenceLevel,
    modifier: Modifier = Modifier,
    style: ConfidenceBadgeStyle = ConfidenceBadgeStyle.PILL,
    showLabel: Boolean = true
) {
    val color = getConfidenceColor(confidenceLevel)

    when (style) {
        ConfidenceBadgeStyle.DOT -> {
            ConfidenceDot(
                color = color,
                modifier = modifier
            )
        }
        ConfidenceBadgeStyle.PILL -> {
            ConfidencePill(
                confidenceLevel = confidenceLevel,
                color = color,
                showLabel = showLabel,
                modifier = modifier
            )
        }
        ConfidenceBadgeStyle.TEXT_ONLY -> {
            ConfidenceText(
                confidenceLevel = confidenceLevel,
                color = color,
                modifier = modifier
            )
        }
        ConfidenceBadgeStyle.FULL -> {
            ConfidenceFull(
                confidenceLevel = confidenceLevel,
                color = color,
                modifier = modifier
            )
        }
    }
}

/**
 * Simple confidence indicator from a score (0.0 to 1.0).
 */
@Composable
fun ConfidenceBadge(
    score: Float,
    modifier: Modifier = Modifier,
    style: ConfidenceBadgeStyle = ConfidenceBadgeStyle.PILL,
    showLabel: Boolean = true
) {
    val confidenceLevel = ConfidenceLevel.fromScore(score)
    ConfidenceBadge(
        confidenceLevel = confidenceLevel,
        modifier = modifier,
        style = style,
        showLabel = showLabel
    )
}

@Composable
private fun ConfidenceDot(
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun ConfidencePill(
    confidenceLevel: ConfidenceLevel,
    color: Color,
    showLabel: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(ShapePatterns.badge)
            .background(color.copy(alpha = 0.15f))
            .padding(
                horizontal = SpacingPatterns.chipPaddingHorizontal / 2,
                vertical = SpacingPatterns.chipPaddingVertical / 2
            ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ConfidenceDot(color = color)
        
        if (showLabel) {
            Text(
                text = confidenceLevel.displayName,
                style = LedgerLensTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

@Composable
private fun ConfidenceText(
    confidenceLevel: ConfidenceLevel,
    color: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = confidenceLevel.displayName,
        modifier = modifier,
        style = LedgerLensTheme.typography.labelSmall,
        color = color
    )
}

@Composable
private fun ConfidenceFull(
    confidenceLevel: ConfidenceLevel,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(ShapePatterns.chip)
            .background(color.copy(alpha = 0.15f))
            .padding(
                horizontal = SpacingPatterns.chipPaddingHorizontal,
                vertical = SpacingPatterns.chipPaddingVertical
            ),
        horizontalArrangement = Arrangement.spacedBy(SpacingPatterns.iconTextGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ConfidenceBar(confidenceLevel = confidenceLevel, color = color)
        
        Text(
            text = "${confidenceLevel.displayName} (${(confidenceLevel.minScore * 100).toInt()}%+)",
            style = LedgerLensTheme.typography.labelMedium,
            color = color
        )
    }
}

@Composable
private fun ConfidenceBar(
    confidenceLevel: ConfidenceLevel,
    color: Color,
    modifier: Modifier = Modifier
) {
    val segments = when (confidenceLevel) {
        ConfidenceLevel.VERY_HIGH -> 5
        ConfidenceLevel.HIGH -> 4
        ConfidenceLevel.MEDIUM -> 3
        ConfidenceLevel.LOW -> 2
        ConfidenceLevel.VERY_LOW -> 1
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(5) { index ->
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 12.dp)
                    .clip(ShapePatterns.badge)
                    .background(
                        if (index < segments) color else color.copy(alpha = 0.2f)
                    )
            )
        }
    }
}

/**
 * Get semantic color for confidence level.
 */
fun getConfidenceColor(confidenceLevel: ConfidenceLevel): Color {
    return when (confidenceLevel) {
        ConfidenceLevel.VERY_HIGH -> LedgerLensColors.ConfidenceVeryHigh
        ConfidenceLevel.HIGH -> LedgerLensColors.ConfidenceHigh
        ConfidenceLevel.MEDIUM -> LedgerLensColors.ConfidenceMedium
        ConfidenceLevel.LOW -> LedgerLensColors.ConfidenceLow
        ConfidenceLevel.VERY_LOW -> LedgerLensColors.ConfidenceVeryLow
    }
}
