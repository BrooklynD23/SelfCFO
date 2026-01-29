package com.ledgerlens.ui.components.cards

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.theme.LedgerLensTheme
import com.ledgerlens.ui.theme.ShapePatterns

/**
 * Represents different types of weekly insights.
 */
sealed class WeeklyInsight {
    abstract val headline: String
    abstract val body: String
    abstract val icon: ImageVector
    abstract val isPositive: Boolean

    data class UnderBudget(val amount: String) : WeeklyInsight() {
        override val headline = "Great progress!"
        override val body = "You're $amount under budget this week"
        override val icon = Icons.Default.TrendingDown
        override val isPositive = true
    }

    data class OverBudget(val amount: String) : WeeklyInsight() {
        override val headline = "Heads up"
        override val body = "You're $amount over budget this week"
        override val icon = Icons.Default.TrendingUp
        override val isPositive = false
    }

    data class SavingsGoal(val percentage: Int) : WeeklyInsight() {
        override val headline = "On track!"
        override val body = "You've reached $percentage% of your savings goal"
        override val icon = Icons.Default.TrendingUp
        override val isPositive = true
    }

    data class Custom(
        override val headline: String,
        override val body: String,
        override val icon: ImageVector,
        override val isPositive: Boolean
    ) : WeeklyInsight()
}

/**
 * Weekly insight card displayed prominently at the top of the dashboard.
 * Dark blue background with white text for emphasis.
 */
@Composable
fun WeeklyInsightCard(
    insight: WeeklyInsight,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography
    val spacing = LedgerLensTheme.spacing

    val backgroundColor = colors.insightCardBackground
    val contentColor = colors.onInsightCard
    val accentColor = if (insight.isPositive) colors.success else colors.warning

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ShapePatterns.insightCard,
        color = backgroundColor,
        onClick = onClick ?: {}
    ) {
        Row(
            modifier = Modifier.padding(spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with accent background
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(ShapePatterns.avatar)
                    .background(accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = insight.icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(spacing.medium))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = insight.headline,
                    style = typography.insightHeadline,
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = insight.body,
                    style = typography.insightBody,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * Compact insight badge for smaller displays.
 */
@Composable
fun InsightBadge(
    text: String,
    isPositive: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LedgerLensTheme.colors
    val backgroundColor = if (isPositive) colors.success else colors.warning

    Surface(
        modifier = modifier,
        shape = ShapePatterns.riskBadge,
        color = backgroundColor.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                contentDescription = null,
                tint = backgroundColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                style = LedgerLensTheme.typography.labelSmall,
                color = backgroundColor
            )
        }
    }
}
