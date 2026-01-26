package com.ledgerlens.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.theme.LedgerLensTheme

/**
 * Mini projection chart for comparison cards showing growth over time.
 * Used in SavingsComparison screen to show 5-year projections.
 */
@Composable
fun MiniProjectionChart(
    projectionPoints: List<Double>,
    modifier: Modifier = Modifier,
    lineColor: Color? = null,
    showGradient: Boolean = true
) {
    val colors = LedgerLensTheme.colors
    val actualLineColor = lineColor ?: colors.success

    if (projectionPoints.isEmpty()) {
        return
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val padding = 4f

        val chartWidth = width - 2 * padding
        val chartHeight = height - 2 * padding

        val minValue = projectionPoints.minOrNull() ?: 0.0
        val maxValue = projectionPoints.maxOrNull() ?: 1.0
        val valueRange = (maxValue - minValue).coerceAtLeast(1.0)

        val points = projectionPoints.mapIndexed { index, value ->
            val x = padding + if (projectionPoints.size > 1) {
                (index.toFloat() / (projectionPoints.size - 1)) * chartWidth
            } else {
                chartWidth / 2
            }
            val normalizedValue = (value - minValue) / valueRange
            val y = padding + chartHeight * (1 - normalizedValue.toFloat())
            Offset(x, y)
        }

        if (points.size < 2) return@Canvas

        // Create smooth path
        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val controlX = (prev.x + curr.x) / 2
                cubicTo(controlX, prev.y, controlX, curr.y, curr.x, curr.y)
            }
        }

        // Draw gradient fill
        if (showGradient) {
            val fillPath = Path().apply {
                moveTo(points.first().x, height - padding)
                lineTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val curr = points[i]
                    val controlX = (prev.x + curr.x) / 2
                    cubicTo(controlX, prev.y, controlX, curr.y, curr.x, curr.y)
                }
                lineTo(points.last().x, height - padding)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        actualLineColor.copy(alpha = 0.3f),
                        actualLineColor.copy(alpha = 0.0f)
                    )
                )
            )
        }

        // Draw line
        drawPath(
            path = linePath,
            color = actualLineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * Generates projection points for compound growth visualization.
 *
 * @param principal Initial amount
 * @param annualRate Annual growth rate (e.g., 0.08 for 8%)
 * @param years Number of years to project
 * @param pointsPerYear Number of data points per year
 */
fun generateProjectionPoints(
    principal: Double,
    annualRate: Double,
    years: Int,
    pointsPerYear: Int = 4
): List<Double> {
    val totalPoints = years * pointsPerYear + 1
    val ratePerPeriod = annualRate / pointsPerYear

    return (0 until totalPoints).map { period ->
        principal * Math.pow(1 + ratePerPeriod, period.toDouble())
    }
}

/**
 * Generates linear projection points (for debt repayment visualization).
 */
fun generateLinearProjectionPoints(
    startValue: Double,
    endValue: Double,
    points: Int = 20
): List<Double> {
    if (points < 2) return listOf(startValue)
    val step = (endValue - startValue) / (points - 1)
    return (0 until points).map { startValue + step * it }
}
