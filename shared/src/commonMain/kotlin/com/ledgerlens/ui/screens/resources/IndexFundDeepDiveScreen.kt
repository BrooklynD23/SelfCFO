package com.ledgerlens.ui.screens.resources

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.components.DisclaimerFooter
import com.ledgerlens.ui.components.charts.LineChart
import com.ledgerlens.ui.components.charts.ChartDataPoint
import com.ledgerlens.ui.theme.LedgerLensTheme
import com.ledgerlens.ui.theme.ShapePatterns
import kotlinx.datetime.LocalDate

/**
 * Index Fund educational deep-dive screen.
 * Editorial-style content explaining index funds with comparisons and charts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndexFundDeepDiveScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToResources: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                title = { }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Deep Dive badge
            item {
                Surface(
                    shape = ShapePatterns.chipSmall,
                    color = colors.primary.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "Deep Dive",
                        style = typography.labelMedium,
                        color = colors.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Editorial headline
            item {
                Text(
                    text = "What Are Index Funds?",
                    style = typography.editorialHeadline,
                    color = colors.onSurface
                )
            }

            // Subtitle
            item {
                Text(
                    text = "A simple, low-cost way to invest in the stock market",
                    style = typography.editorialSubheadline,
                    color = colors.onSurfaceVariant
                )
            }

            // Hero section with icon
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(ShapePatterns.card)
                        .background(colors.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            // Introduction
            item {
                Text(
                    text = "An index fund is a type of mutual fund or ETF that aims to match the performance of a specific market index, like the S&P 500. Instead of trying to beat the market, index funds simply track it.",
                    style = typography.editorialBody,
                    color = colors.onSurface
                )
            }

            // How it Works section
            item {
                SectionHeader(title = "How It Works")
            }

            item {
                Text(
                    text = "When you buy shares in an index fund, you're automatically investing in all the companies that make up that index. For example, an S&P 500 index fund holds shares in 500 of the largest U.S. companies.",
                    style = typography.editorialBody,
                    color = colors.onSurface
                )
            }

            // Comparison section
            item {
                SectionHeader(title = "Single Stock vs Index Fund")
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ComparisonBox(
                        title = "Single Stock",
                        points = listOf(
                            "Higher potential return" to true,
                            "Higher risk" to false,
                            "Requires research" to false,
                            "Less diversification" to false
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    ComparisonBox(
                        title = "Index Fund",
                        points = listOf(
                            "Market-average return" to true,
                            "Lower risk" to true,
                            "Simple to understand" to true,
                            "Built-in diversification" to true
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Historical Performance section
            item {
                SectionHeader(title = "Historical Performance")
            }

            item {
                Text(
                    text = "The S&P 500 has historically returned about 10% annually over the long term, though past performance doesn't guarantee future results.",
                    style = typography.editorialBody,
                    color = colors.onSurface
                )
            }

            // Sample chart
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapePatterns.chartContainer,
                    color = colors.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "S&P 500 Growth (Simplified)",
                            style = typography.labelMedium,
                            color = colors.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LineChart(
                            dataPoints = getSampleSP500Data(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        )
                    }
                }
            }

            // Is it right for you section
            item {
                SectionHeader(title = "Is It Right For You?")
            }

            item {
                ProConList(
                    pros = listOf(
                        "Great for long-term investors",
                        "Low fees compared to active funds",
                        "No need to pick individual stocks",
                        "Automatic diversification"
                    ),
                    cons = listOf(
                        "Won't beat the market",
                        "Still subject to market downturns",
                        "Less exciting than stock picking"
                    )
                )
            }

            // Key takeaway
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapePatterns.insightCard,
                    color = colors.secondaryContainer
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Key Takeaway",
                            style = typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Index funds are an excellent choice for most investors, especially beginners. They offer broad market exposure at low cost with minimal effort.",
                            style = typography.bodyMedium,
                            color = colors.onSecondaryContainer
                        )
                    }
                }
            }

            // CTA
            item {
                Button(
                    onClick = onNavigateToResources,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapePatterns.button
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Market Resources")
                }
            }

            // Disclaimer
            item {
                DisclaimerFooter()
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    val typography = LedgerLensTheme.typography
    val colors = LedgerLensTheme.colors

    Text(
        text = title,
        style = typography.editorialSubheadline,
        fontWeight = FontWeight.SemiBold,
        color = colors.onSurface,
        modifier = modifier
    )
}

@Composable
private fun ComparisonBox(
    title: String,
    points: List<Pair<String, Boolean>>,
    modifier: Modifier = Modifier
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    Surface(
        modifier = modifier,
        shape = ShapePatterns.card,
        color = colors.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            points.forEach { (text, isPositive) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isPositive) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (isPositive) colors.success else colors.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = text,
                        style = typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ProConList(
    pros: List<String>,
    cons: List<String>,
    modifier: Modifier = Modifier
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    Column(modifier = modifier.fillMaxWidth()) {
        // Pros
        Text(
            text = "Pros",
            style = typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = colors.success
        )
        Spacer(modifier = Modifier.height(8.dp))
        pros.forEach { pro ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = colors.success,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = pro,
                    style = typography.bodyMedium,
                    color = colors.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Cons
        Text(
            text = "Cons",
            style = typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = colors.warning
        )
        Spacer(modifier = Modifier.height(8.dp))
        cons.forEach { con ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = colors.warning,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = con,
                    style = typography.bodyMedium,
                    color = colors.onSurface
                )
            }
        }
    }
}

private fun getSampleSP500Data(): List<ChartDataPoint> {
    // Simplified sample data showing general upward trend
    return listOf(
        ChartDataPoint(LocalDate(2019, 1, 1), 2500.0),
        ChartDataPoint(LocalDate(2019, 6, 1), 2900.0),
        ChartDataPoint(LocalDate(2020, 1, 1), 3200.0),
        ChartDataPoint(LocalDate(2020, 4, 1), 2400.0), // COVID dip
        ChartDataPoint(LocalDate(2020, 12, 1), 3700.0),
        ChartDataPoint(LocalDate(2021, 6, 1), 4200.0),
        ChartDataPoint(LocalDate(2021, 12, 1), 4700.0),
        ChartDataPoint(LocalDate(2022, 6, 1), 3800.0), // 2022 correction
        ChartDataPoint(LocalDate(2022, 12, 1), 3800.0),
        ChartDataPoint(LocalDate(2023, 6, 1), 4400.0),
        ChartDataPoint(LocalDate(2023, 12, 1), 4700.0),
        ChartDataPoint(LocalDate(2024, 6, 1), 5200.0)
    )
}
