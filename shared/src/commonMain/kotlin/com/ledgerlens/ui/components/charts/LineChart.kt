package com.ledgerlens.ui.components.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.theme.LedgerLensTheme

/**
 * Line chart component for visualizing time-series data like net worth.
 * Features gradient fill, smooth curves, and optional grid lines.
 */
@Composable
fun LineChart(
    dataPoints: List<ChartDataPoint>,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig(),
    lineColor: Color? = null,
    gradientStartColor: Color? = null,
    gradientEndColor: Color? = null
) {
    val colors = LedgerLensTheme.colors
    val actualLineColor = lineColor ?: colors.chartLine
    val actualGradientStart = gradientStartColor ?: colors.chartGradientStart
    val actualGradientEnd = gradientEndColor ?: colors.chartGradientEnd
    val gridColor = colors.chartGrid

    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(dataPoints) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = config.animationDurationMs)
        )
    }

    if (dataPoints.isEmpty()) {
        Box(modifier = modifier)
        return
    }

    Canvas(
        modifier = modifier
            .height(200.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val width = size.width
        val height = size.height
        val paddingTop = 16f
        val paddingBottom = 24f
        val chartHeight = height - paddingTop - paddingBottom

        val minValue = dataPoints.minOf { it.value }
        val maxValue = dataPoints.maxOf { it.value }
        val valueRange = (maxValue - minValue).coerceAtLeast(1.0)

        // Draw grid lines
        if (config.showGridLines) {
            drawGridLines(
                width = width,
                height = chartHeight,
                offsetY = paddingTop,
                gridColor = gridColor,
                lineCount = 4
            )
        }

        // Calculate points
        val points = dataPoints.mapIndexed { index, point ->
            val x = if (dataPoints.size > 1) {
                (index.toFloat() / (dataPoints.size - 1)) * width
            } else {
                width / 2
            }
            val normalizedValue = (point.value - minValue) / valueRange
            val y = paddingTop + chartHeight * (1 - normalizedValue.toFloat())
            Offset(x, y)
        }

        // Animate points
        val animatedPoints = points.mapIndexed { index, point ->
            val progress = (animationProgress.value * points.size - index).coerceIn(0f, 1f)
            val startY = paddingTop + chartHeight
            Offset(point.x, startY + (point.y - startY) * progress)
        }

        // Draw gradient fill
        if (config.showGradientFill && animatedPoints.size >= 2) {
            val fillPath = Path().apply {
                moveTo(animatedPoints.first().x, paddingTop + chartHeight)
                lineTo(animatedPoints.first().x, animatedPoints.first().y)
                for (i in 1 until animatedPoints.size) {
                    val prev = animatedPoints[i - 1]
                    val curr = animatedPoints[i]
                    val controlX = (prev.x + curr.x) / 2
                    cubicTo(controlX, prev.y, controlX, curr.y, curr.x, curr.y)
                }
                lineTo(animatedPoints.last().x, paddingTop + chartHeight)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(actualGradientStart, actualGradientEnd),
                    startY = paddingTop,
                    endY = paddingTop + chartHeight
                )
            )
        }

        // Draw line
        if (animatedPoints.size >= 2) {
            val linePath = Path().apply {
                moveTo(animatedPoints.first().x, animatedPoints.first().y)
                for (i in 1 until animatedPoints.size) {
                    val prev = animatedPoints[i - 1]
                    val curr = animatedPoints[i]
                    val controlX = (prev.x + curr.x) / 2
                    cubicTo(controlX, prev.y, controlX, curr.y, curr.x, curr.y)
                }
            }

            drawPath(
                path = linePath,
                color = actualLineColor,
                style = Stroke(
                    width = config.lineWidth.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        // Draw data points
        if (config.showDataPoints) {
            animatedPoints.forEachIndexed { index, point ->
                val progress = (animationProgress.value * points.size - index).coerceIn(0f, 1f)
                if (progress > 0.5f) {
                    // Outer circle (white)
                    drawCircle(
                        color = Color.White,
                        radius = (config.pointRadius + 2f).dp.toPx(),
                        center = point
                    )
                    // Inner circle (colored)
                    drawCircle(
                        color = actualLineColor,
                        radius = config.pointRadius.dp.toPx(),
                        center = point
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawGridLines(
    width: Float,
    height: Float,
    offsetY: Float,
    gridColor: Color,
    lineCount: Int
) {
    val spacing = height / lineCount
    for (i in 0..lineCount) {
        val y = offsetY + (spacing * i)
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
        )
    }
}

/**
 * Simple sparkline chart for inline display.
 */
@Composable
fun SparklineChart(
    dataPoints: List<ChartDataPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color? = null,
    showTrend: Boolean = true
) {
    val colors = LedgerLensTheme.colors
    val isPositive = dataPoints.size >= 2 &&
        dataPoints.last().value >= dataPoints.first().value
    val actualLineColor = lineColor ?: if (isPositive) colors.success else colors.error

    Canvas(modifier = modifier.fillMaxSize()) {
        if (dataPoints.size < 2) return@Canvas

        val width = size.width
        val height = size.height
        val padding = 2f

        val minValue = dataPoints.minOf { it.value }
        val maxValue = dataPoints.maxOf { it.value }
        val valueRange = (maxValue - minValue).coerceAtLeast(1.0)

        val points = dataPoints.mapIndexed { index, point ->
            val x = padding + (index.toFloat() / (dataPoints.size - 1)) * (width - 2 * padding)
            val normalizedValue = (point.value - minValue) / valueRange
            val y = padding + (height - 2 * padding) * (1 - normalizedValue.toFloat())
            Offset(x, y)
        }

        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val controlX = (prev.x + curr.x) / 2
                cubicTo(controlX, prev.y, controlX, curr.y, curr.x, curr.y)
            }
        }

        drawPath(
            path = path,
            color = actualLineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
