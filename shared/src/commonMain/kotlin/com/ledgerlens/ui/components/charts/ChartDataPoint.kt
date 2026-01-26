package com.ledgerlens.ui.components.charts

import kotlinx.datetime.LocalDate

/**
 * Represents a single data point in a chart.
 */
data class ChartDataPoint(
    val date: LocalDate,
    val value: Double,
    val label: String? = null
)

/**
 * Time range options for chart display.
 */
enum class TimeRange(val label: String, val days: Int) {
    ONE_WEEK("1W", 7),
    ONE_MONTH("1M", 30),
    THREE_MONTHS("3M", 90),
    SIX_MONTHS("6M", 180),
    ONE_YEAR("1Y", 365),
    ALL_TIME("All", -1)
}

/**
 * Chart configuration options.
 */
data class ChartConfig(
    val showGridLines: Boolean = true,
    val showDataPoints: Boolean = true,
    val showGradientFill: Boolean = true,
    val animationDurationMs: Int = 500,
    val lineWidth: Float = 2f,
    val pointRadius: Float = 4f
)
