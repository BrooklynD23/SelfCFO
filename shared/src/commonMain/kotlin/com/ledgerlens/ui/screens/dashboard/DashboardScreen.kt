package com.ledgerlens.ui.screens.dashboard

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.components.CategoryChip
import com.ledgerlens.ui.components.CategoryChipSize
import com.ledgerlens.ui.components.EmptyState
import com.ledgerlens.ui.components.ErrorState
import com.ledgerlens.ui.components.LedgerLensCard
import com.ledgerlens.ui.components.LedgerLensElevatedCard
import com.ledgerlens.ui.components.LoadingIndicator
import com.ledgerlens.ui.components.MoneyText
import com.ledgerlens.ui.components.MoneyTextLarge
import com.ledgerlens.ui.components.MoneyTextSize
import com.ledgerlens.ui.screens.transactions.TransactionItem
import com.ledgerlens.ui.viewmodels.dashboard.DashboardUiState
import com.ledgerlens.ui.viewmodels.dashboard.DashboardViewModel

/**
 * Dashboard screen showing monthly spending summary, recent transactions,
 * category breakdown, and quick actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToImport: () -> Unit = {},
    onNavigateToReview: () -> Unit = {},
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToTransactionDetail: (String) -> Unit = {},
    onNavigateToCategory: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") }
            )
        }
    ) { paddingValues ->
        DashboardContent(
            uiState = uiState,
            onRefresh = viewModel::refreshData,
            onImportClick = onNavigateToImport,
            onReviewClick = onNavigateToReview,
            onViewAllTransactionsClick = onNavigateToTransactions,
            onTransactionClick = onNavigateToTransactionDetail,
            onCategoryClick = onNavigateToCategory,
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
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = modifier.fillMaxSize()
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Monthly Summary Card
                    item {
                        MonthlySummaryCard(
                            monthLabel = uiState.currentMonthLabel,
                            totalSpending = uiState.totalSpendingThisMonth,
                            totalIncome = uiState.totalIncomeThisMonth,
                            netChange = uiState.netChangeThisMonth
                        )
                    }

                    // Quick Actions
                    item {
                        QuickActionsRow(
                            pendingReviewCount = uiState.pendingReviewCount,
                            uncategorizedCount = uiState.uncategorizedCount,
                            onImportClick = onImportClick,
                            onReviewClick = onReviewClick
                        )
                    }

                    // Category Breakdown
                    if (uiState.categoryBreakdown.isNotEmpty()) {
                        item {
                            CategoryBreakdownSection(
                                categories = uiState.categoryBreakdown,
                                onCategoryClick = onCategoryClick
                            )
                        }
                    }

                    // Recent Transactions Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent Transactions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
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
                    }

                    // Recent Transactions List
                    if (uiState.recentTransactions.isEmpty()) {
                        item {
                            EmptyState(
                                title = "No Transactions Yet",
                                message = "Import your bank statements to get started",
                                actionLabel = "Import Transactions",
                                onAction = onImportClick
                            )
                        }
                    } else {
                        items(
                            items = uiState.recentTransactions,
                            key = { it.id }
                        ) { transaction ->
                            TransactionItem(
                                transaction = transaction,
                                onClick = { onTransactionClick(transaction.id) }
                            )
                        }
                    }

                    // Bottom spacing
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlySummaryCard(
    monthLabel: String,
    totalSpending: com.ledgerlens.domain.Money,
    totalIncome: com.ledgerlens.domain.Money,
    netChange: com.ledgerlens.domain.Money,
    modifier: Modifier = Modifier
) {
    LedgerLensElevatedCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = monthLabel,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Spending
            Column {
                Text(
                    text = "Spent",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MoneyTextLarge(
                    money = totalSpending.abs(),
                    colored = false
                )
            }

            // Income
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Income",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MoneyTextLarge(
                    money = totalIncome,
                    colored = true
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Net Change
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Net: ",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            MoneyText(
                money = netChange,
                size = MoneyTextSize.LARGE,
                showSign = true
            )
        }
    }
}

@Composable
private fun QuickActionsRow(
    pendingReviewCount: Int,
    uncategorizedCount: Int,
    onImportClick: () -> Unit,
    onReviewClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Import Button
        Button(
            onClick = onImportClick,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Import")
        }

        // Review Button with badge
        val totalPending = pendingReviewCount + uncategorizedCount
        if (totalPending > 0) {
            BadgedBox(
                badge = {
                    Badge {
                        Text(totalPending.toString())
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                FilledTonalButton(
                    onClick = onReviewClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Review")
                }
            }
        } else {
            OutlinedButton(
                onClick = onReviewClick,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("All Clear")
            }
        }
    }
}

@Composable
private fun CategoryBreakdownSection(
    categories: List<CategoryBreakdownUiModel>,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Spending by Category",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Category chips in horizontal scroll
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = categories,
                key = { it.category.id }
            ) { breakdown ->
                CategoryBreakdownChip(
                    breakdown = breakdown,
                    onClick = { onCategoryClick(breakdown.category.id) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Simple bar representation
        LedgerLensCard(modifier = Modifier.fillMaxWidth()) {
            categories.forEach { breakdown ->
                CategoryProgressRow(breakdown = breakdown)
                if (breakdown != categories.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdownChip(
    breakdown: CategoryBreakdownUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CategoryChip(
        category = breakdown.category,
        size = CategoryChipSize.MEDIUM,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun CategoryProgressRow(
    breakdown: CategoryBreakdownUiModel,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = breakdown.category.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(breakdown.percentage * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        MoneyText(
            money = breakdown.amount.abs(),
            size = MoneyTextSize.SMALL
        )
    }
}
