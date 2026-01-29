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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
 * Types of financial pillars displayed on the dashboard.
 */
enum class PillarType(
    val title: String,
    val icon: ImageVector
) {
    SAVINGS("Savings", Icons.Default.Savings),
    EXPENSES("Expenses", Icons.Default.CreditCard),
    INVESTMENTS("Investments", Icons.Default.TrendingUp),
    DEBT("Debt", Icons.Default.AccountBalance)
}

/**
 * Data for a financial pillar card.
 */
data class PillarData(
    val type: PillarType,
    val currentAmount: String,
    val targetAmount: String? = null,
    val progress: Float? = null,
    val changeText: String? = null,
    val isPositiveChange: Boolean = true
)

/**
 * Pillar card showing a financial metric with optional progress.
 * Used in the dashboard's horizontal scroll section.
 */
@Composable
fun PillarCard(
    data: PillarData,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography
    val spacing = LedgerLensTheme.spacing

    val pillarColor = when (data.type) {
        PillarType.SAVINGS -> colors.pillarSavings
        PillarType.EXPENSES -> colors.pillarExpenses
        PillarType.INVESTMENTS -> colors.pillarInvestments
        PillarType.DEBT -> colors.pillarDebt
    }

    Surface(
        modifier = modifier.width(160.dp),
        shape = ShapePatterns.pillarCard,
        color = colors.surface,
        shadowElevation = 2.dp,
        onClick = onClick ?: {}
    ) {
        Column(
            modifier = Modifier.padding(spacing.medium)
        ) {
            // Header with icon and title
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(ShapePatterns.avatar)
                        .background(pillarColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = data.type.icon,
                        contentDescription = data.type.title,
                        tint = pillarColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = data.type.title,
                    style = typography.labelMedium,
                    color = colors.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Amount
            Text(
                text = data.currentAmount,
                style = typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )

            // Change indicator
            data.changeText?.let { change ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = change,
                    style = typography.labelSmall,
                    color = if (data.isPositiveChange) colors.success else colors.error
                )
            }

            // Progress bar (if target exists)
            data.progress?.let { progress ->
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = progress.coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(ShapePatterns.full),
                    color = pillarColor,
                    trackColor = pillarColor.copy(alpha = 0.2f)
                )
                data.targetAmount?.let { target ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "of $target",
                        style = typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Row of pillar cards for horizontal scrolling.
 */
@Composable
fun PillarsRow(
    pillars: List<PillarData>,
    modifier: Modifier = Modifier,
    onPillarClick: ((PillarType) -> Unit)? = null
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        pillars.forEach { pillar ->
            PillarCard(
                data = pillar,
                onClick = onPillarClick?.let { { it(pillar.type) } }
            )
        }
    }
}
