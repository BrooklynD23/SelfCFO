package com.ledgerlens.ui.screens.dashboard

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.components.ActivityItem
import com.ledgerlens.ui.components.ActivityItemData
import com.ledgerlens.ui.components.ActivitySectionHeader
import com.ledgerlens.ui.components.Avatar
import com.ledgerlens.ui.components.AvatarSize
import com.ledgerlens.ui.components.EmptyState
import com.ledgerlens.ui.components.ErrorState
import com.ledgerlens.ui.components.LoadingIndicator
import com.ledgerlens.ui.components.TimeRangeSelector
import com.ledgerlens.ui.components.cards.PillarCard
import com.ledgerlens.ui.components.cards.WeeklyInsightCard
import com.ledgerlens.ui.components.charts.LineChart
import com.ledgerlens.ui.components.charts.TimeRange
import com.ledgerlens.ui.components.getEmojiForCategory
import com.ledgerlens.ui.theme.LedgerLensTheme
import com.ledgerlens.ui.viewmodels.dashboard.DashboardUiState
import com.ledgerlens.ui.viewmodels.dashboard.DashboardViewModel

/**
 * Dashboard screen with StitchUI design.
 * Shows greeting, weekly insight, net worth chart, pillars, and recent activity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToImport: () -> Unit = {},
    onNavigateToReview: () -> Unit = {},
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToTransactionDetail: (String) -> Unit = {},
    onNavigateToCategory: (String) -> Unit = {},
    onNavigateToResources: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold { paddingValues ->
        DashboardContent(
            uiState = uiState,
            onRefresh = viewModel::refreshData,
            onImportClick = onNavigateToImport,
            onReviewClick = onNavigateToReview,
            onViewAllTransactionsClick = onNavigateToTransactions,
            onTransactionClick = onNavigateToTransactionDetail,
            onCategoryClick = onNavigateToCategory,
            onTimeRangeSelected = viewModel::selectTimeRange,
            onRetry = viewModel::loadDashboardData,
            onDismissError = viewModel::dismissError,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardContent(
    uiState: DashboardUiState,
    onRefresh: () -> Unit,
    onImportClick: () -> Unit,
    onReviewClick: () -> Unit,
    onViewAllTransactionsClick: () -> Unit,
    onTransactionClick: (String) -> Unit,
    onCategoryClick: (String) -> Unit,
    onTimeRangeSelected: (TimeRange) -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography
    val spacing = LedgerLensTheme.spacing

    when {
        uiState.isLoading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator(message = "Loading dashboard...")
            }
        }

        uiState.error != null && !uiState.hasRecentTransactions -> {
            ErrorState(
                title = "Unable to Load Dashboard",
                message = uiState.error,
                onRetry = onRetry,
                onDismiss = onDismissError,
                modifier = modifier.fillMaxSize()
            )
        }

        else -> {
            Box(modifier = modifier.fillMaxSize()) {
                if (uiState.isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                            .size(24.dp),
                        strokeWidth = 2.dp
                    )
                }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 1. Greeting Section
                    item {
                        GreetingSection(
                            greeting = uiState.greeting,
                            userName = uiState.userName
                        )
                    }

                    // 2. Weekly Insight Card
                    uiState.weeklyInsight?.let { insight ->
                        item {
                            WeeklyInsightCard(
                                insight = insight,
                                onClick = onReviewClick
                            )
                        }
                    }

                    // 3. Net Worth Section
                    item {
                        NetWorthSection(
                            totalNetWorth = uiState.totalNetWorth.formatted(),
                            netWorthHistory = uiState.netWorthHistory,
                            selectedTimeRange = uiState.selectedTimeRange,
                            onTimeRangeSelected = onTimeRangeSelected
                        )
                    }

                    // 4. Pillars Section
                    if (uiState.pillars.isNotEmpty()) {
                        item {
                            PillarsSection(
                                pillars = uiState.pillars
                            )
                        }
                    }

                    // 5. Recent Activity Section
                    item {
                        ActivitySectionHeader(
                            title = "Recent Activity",
                            action = {
                                TextButton(onClick = onViewAllTransactionsClick) {
                                    Text("View All")
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                    }

                    if (uiState.recentTransactions.isEmpty()) {
                        item {
                            EmptyState(
                                title = "No Transactions Yet",
                                description = "Import your bank statements to get started",
                                actionLabel = "Import Transactions",
                                onAction = onImportClick
                            )
                        }
                    } else {
                        items(
                            items = uiState.recentTransactions,
                            key = { it.id }
                        ) { transaction ->
                            val activityData = ActivityItemData(
                                id = transaction.id,
                                emoji = getEmojiForCategory(transaction.category?.name ?: "unknown"),
                                title = transaction.normalizedMerchant ?: transaction.merchantName,
                                subtitle = transaction.category?.name ?: "Uncategorized",
                                amount = transaction.amount.formatted(),
                                isPositive = transaction.amount.isPositive,
                                timestamp = transaction.date
                            )
                            ActivityItem(
                                data = activityData,
                                onClick = { onTransactionClick(transaction.id) }
                            )
                        }
                    }

                    // Bottom spacing for bottom nav
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GreetingSection(
    greeting: String,
    userName: String,
    modifier: Modifier = Modifier
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "$greeting,",
                style = typography.titleMedium,
                color = colors.onSurfaceVariant
            )
            Text(
                text = userName,
                style = typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
        }

        Avatar(
            name = userName,
            size = AvatarSize.LARGE
        )
    }
}

@Composable
private fun NetWorthSection(
    totalNetWorth: String,
    netWorthHistory: List<com.ledgerlens.ui.components.charts.ChartDataPoint>,
    selectedTimeRange: TimeRange,
    onTimeRangeSelected: (TimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    Column(modifier = modifier.fillMaxWidth()) {
        // Header with amount and time range selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = "Net Worth",
                    style = typography.labelLarge,
                    color = colors.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = totalNetWorth,
                    style = typography.dataLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
            }

            TimeRangeSelector(
                selectedRange = selectedTimeRange,
                onRangeSelected = onTimeRangeSelected
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Chart
        if (netWorthHistory.isNotEmpty()) {
            LineChart(
                dataPoints = netWorthHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }
    }
}

@Composable
private fun PillarsSection(
    pillars: List<com.ledgerlens.ui.components.cards.PillarData>,
    modifier: Modifier = Modifier
) {
    val typography = LedgerLensTheme.typography
    val colors = LedgerLensTheme.colors

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Financial Pillars",
            style = typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            pillars.forEach { pillar ->
                PillarCard(data = pillar)
            }
        }
    }
}

