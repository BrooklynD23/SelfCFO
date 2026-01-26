package com.ledgerlens.ui.components.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.components.charts.MiniProjectionChart
import com.ledgerlens.ui.theme.LedgerLensTheme
import com.ledgerlens.ui.theme.ShapePatterns

/**
 * Risk level for financial options.
 */
enum class RiskLevel(val label: String) {
    LOW("Low Risk"),
    MEDIUM("Medium Risk"),
    HIGH("High Risk"),
    GUARANTEED("Guaranteed")
}

/**
 * Data for a comparison card in the savings comparison screen.
 */
data class ComparisonOption(
    val title: String,
    val rateText: String,
    val rateDescription: String,
    val riskLevel: RiskLevel,
    val projectionPoints: List<Double>,
    val projectionLabel: String,
    val description: String? = null
)

/**
 * Comparison card for financial options showing rate, risk, and projection.
 */
@Composable
fun ComparisonCard(
    option: ComparisonOption,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    isSelected: Boolean = false
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography
    val spacing = LedgerLensTheme.spacing

    val riskColor = when (option.riskLevel) {
        RiskLevel.LOW, RiskLevel.GUARANTEED -> colors.riskLow
        RiskLevel.MEDIUM -> colors.riskMedium
        RiskLevel.HIGH -> colors.riskHigh
    }

    val borderColor = if (isSelected) colors.primary else colors.outline

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ShapePatterns.comparisonCard,
        color = colors.surface,
        shadowElevation = if (isSelected) 4.dp else 1.dp,
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, colors.primary)
        } else null,
        onClick = onClick ?: {}
    ) {
        Column(
            modifier = Modifier.padding(spacing.medium)
        ) {
            // Header row with title and risk badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = option.title,
                    style = typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
                RiskBadge(
                    riskLevel = option.riskLevel,
                    color = riskColor
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Rate display
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = option.rateText,
                    style = typography.dataMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = option.rateDescription,
                    style = typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // Description
            option.description?.let { desc ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = desc,
                    style = typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mini chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                MiniProjectionChart(
                    projectionPoints = option.projectionPoints,
                    lineColor = colors.success
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Projection label
            Text(
                text = option.projectionLabel,
                style = typography.labelMedium,
                color = colors.success,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Risk badge showing the risk level with appropriate color.
 */
@Composable
fun RiskBadge(
    riskLevel: RiskLevel,
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    val colors = LedgerLensTheme.colors
    val badgeColor = color ?: when (riskLevel) {
        RiskLevel.LOW, RiskLevel.GUARANTEED -> colors.riskLow
        RiskLevel.MEDIUM -> colors.riskMedium
        RiskLevel.HIGH -> colors.riskHigh
    }

    Surface(
        modifier = modifier,
        shape = ShapePatterns.riskBadge,
        color = badgeColor.copy(alpha = 0.1f)
    ) {
        Text(
            text = riskLevel.label,
            style = LedgerLensTheme.typography.labelSmall,
            color = badgeColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * Simplified comparison card for quick overview.
 */
@Composable
fun ComparisonCardCompact(
    title: String,
    value: String,
    subtext: String,
    riskLevel: RiskLevel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    Surface(
        modifier = modifier,
        shape = ShapePatterns.card,
        color = colors.surface,
        shadowElevation = 1.dp,
        onClick = onClick ?: {}
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = typography.labelMedium,
                    color = colors.onSurfaceVariant
                )
                RiskBadge(riskLevel = riskLevel)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
            Text(
                text = subtext,
                style = typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}
