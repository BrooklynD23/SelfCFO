package com.ledgerlens.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.components.charts.TimeRange
import com.ledgerlens.ui.theme.LedgerLensTheme
import com.ledgerlens.ui.theme.ShapePatterns

/**
 * Time range selector for chart views.
 * Toggle buttons showing 1W, 1M, 3M, 6M, 1Y, All options.
 */
@Composable
fun TimeRangeSelector(
    selectedRange: TimeRange,
    onRangeSelected: (TimeRange) -> Unit,
    modifier: Modifier = Modifier,
    availableRanges: List<TimeRange> = listOf(
        TimeRange.ONE_WEEK,
        TimeRange.ONE_MONTH,
        TimeRange.THREE_MONTHS,
        TimeRange.ONE_YEAR
    )
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    Surface(
        modifier = modifier,
        shape = ShapePatterns.timeRangeButton,
        color = colors.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            availableRanges.forEach { range ->
                TimeRangeButton(
                    range = range,
                    isSelected = range == selectedRange,
                    onClick = { onRangeSelected(range) }
                )
            }
        }
    }
}

@Composable
private fun TimeRangeButton(
    range: TimeRange,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) colors.surface else colors.surfaceVariant.copy(alpha = 0f),
        label = "backgroundColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) colors.primary else colors.onSurfaceVariant,
        label = "contentColor"
    )

    Box(
        modifier = Modifier
            .clip(ShapePatterns.chipSmall)
            .background(backgroundColor)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = range.label,
            style = typography.labelMedium,
            color = contentColor,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/**
 * Compact time range selector for smaller spaces.
 */
@Composable
fun TimeRangeSelectorCompact(
    selectedRange: TimeRange,
    onRangeSelected: (TimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    TimeRangeSelector(
        selectedRange = selectedRange,
        onRangeSelected = onRangeSelected,
        modifier = modifier,
        availableRanges = listOf(
            TimeRange.ONE_WEEK,
            TimeRange.ONE_MONTH,
            TimeRange.ONE_YEAR
        )
    )
}
