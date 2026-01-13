# 03: Dashboard Screen

## Overview

Implement the home dashboard with spending summaries, category breakdowns, charts, and quick action shortcuts.

---

## Implementation Steps

### Step 1: Dashboard State

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/dashboard/DashboardState.kt
package com.ledgerlens.ui.screens.dashboard

import com.ledgerlens.domain.Money
import kotlinx.datetime.LocalDate

data class DashboardState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val period: DashboardPeriod = DashboardPeriod.THIS_MONTH,
    val summary: SpendingSummary? = null,
    val categoryBreakdown: List<CategorySpending> = emptyList(),
    val recentTransactions: List<TransactionSummary> = emptyList(),
    val reviewCount: Int = 0,
    val accountBalances: List<AccountBalance> = emptyList()
)

data class SpendingSummary(
    val totalIncome: Money,
    val totalExpenses: Money,
    val netCashFlow: Money,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val comparisonToPrevious: Float? = null // Percentage change
)

data class CategorySpending(
    val categoryId: String,
    val categoryName: String,
    val categoryIcon: String?,
    val categoryColor: String,
    val amount: Money,
    val percentage: Float,
    val transactionCount: Int
)

data class TransactionSummary(
    val id: String,
    val description: String,
    val amount: Money,
    val date: LocalDate,
    val categoryName: String?,
    val categoryIcon: String?
)

data class AccountBalance(
    val accountId: String,
    val accountName: String,
    val balance: Money,
    val lastUpdated: LocalDate?
)

enum class DashboardPeriod(val label: String) {
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    THIS_YEAR("This Year"),
    ALL_TIME("All Time")
}
```

### Step 2: Dashboard ViewModel

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/viewmodel/DashboardViewModel.kt
package com.ledgerlens.viewmodel

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val reviewInboxService: ReviewInboxService
) : BaseViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val _period = MutableStateFlow(DashboardPeriod.THIS_MONTH)

    init {
        loadDashboard()
        observePeriodChanges()
    }

    fun setPeriod(period: DashboardPeriod) {
        _period.value = period
    }

    fun refresh() {
        loadDashboard()
    }

    private fun observePeriodChanges() {
        viewModelScope.launch {
            _period.collect { period ->
                _state.update { it.copy(period = period) }
                loadDashboard()
            }
        }
    }

    private fun loadDashboard() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                val dateRange = _period.value.toDateRange()

                // Load all data in parallel
                val summaryDeferred = async { loadSummary(dateRange) }
                val categoryDeferred = async { loadCategoryBreakdown(dateRange) }
                val recentDeferred = async { loadRecentTransactions() }
                val reviewDeferred = async { loadReviewCount() }
                val accountsDeferred = async { loadAccountBalances() }

                _state.update {
                    it.copy(
                        isLoading = false,
                        summary = summaryDeferred.await(),
                        categoryBreakdown = categoryDeferred.await(),
                        recentTransactions = recentDeferred.await(),
                        reviewCount = reviewDeferred.await(),
                        accountBalances = accountsDeferred.await()
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = e.message)
                }
            }
        }
    }

    private suspend fun loadSummary(dateRange: ClosedRange<LocalDate>): SpendingSummary {
        val transactions = transactionRepository.getInDateRange(dateRange)

        val income = transactions
            .filter { it.amount.minorUnits > 0 }
            .sumOf { it.amount.minorUnits }

        val expenses = transactions
            .filter { it.amount.minorUnits < 0 }
            .sumOf { kotlin.math.abs(it.amount.minorUnits) }

        return SpendingSummary(
            totalIncome = Money(income, "USD"),
            totalExpenses = Money(expenses, "USD"),
            netCashFlow = Money(income - expenses, "USD"),
            periodStart = dateRange.start,
            periodEnd = dateRange.endInclusive
        )
    }

    private suspend fun loadCategoryBreakdown(
        dateRange: ClosedRange<LocalDate>
    ): List<CategorySpending> {
        val transactions = transactionRepository.getInDateRange(dateRange)
            .filter { it.amount.minorUnits < 0 } // Expenses only

        val totalExpenses = transactions.sumOf { kotlin.math.abs(it.amount.minorUnits) }

        return transactions
            .groupBy { it.categoryId ?: "uncategorized" }
            .map { (categoryId, txns) ->
                val category = categoryRepository.getById(categoryId)
                val amount = txns.sumOf { kotlin.math.abs(it.amount.minorUnits) }

                CategorySpending(
                    categoryId = categoryId,
                    categoryName = category?.name ?: "Uncategorized",
                    categoryIcon = category?.icon,
                    categoryColor = category?.color ?: "#808080",
                    amount = Money(amount, "USD"),
                    percentage = if (totalExpenses > 0) amount.toFloat() / totalExpenses else 0f,
                    transactionCount = txns.size
                )
            }
            .sortedByDescending { it.amount.minorUnits }
    }

    private suspend fun loadRecentTransactions(): List<TransactionSummary> {
        return transactionRepository.getRecent(limit = 5).map { tx ->
            val category = tx.categoryId?.let { categoryRepository.getById(it) }
            TransactionSummary(
                id = tx.id,
                description = tx.merchantNormalized,
                amount = Money(tx.amountMinorUnits, tx.currencyCode),
                date = tx.postedDate,
                categoryName = category?.name,
                categoryIcon = category?.icon
            )
        }
    }

    private suspend fun loadReviewCount(): Int {
        return reviewInboxService.getReviewCounts().values.sum()
    }

    private suspend fun loadAccountBalances(): List<AccountBalance> {
        return accountRepository.getAll().map { account ->
            AccountBalance(
                accountId = account.id,
                accountName = account.displayName,
                balance = Money(account.currentBalanceMinorUnits ?: 0, account.currencyCode),
                lastUpdated = account.lastImportDate
            )
        }
    }
}
```

### Step 3: Dashboard Screen

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/dashboard/DashboardScreen.kt
package com.ledgerlens.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DashboardScreen(
    onNavigateToTransactions: () -> Unit,
    onNavigateToReview: () -> Unit,
    onNavigateToImport: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            DashboardTopBar(
                period = state.period,
                onPeriodChange = viewModel::setPeriod
            )
        }
    ) { padding ->
        when {
            state.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            state.error != null -> ErrorState(
                message = state.error!!,
                onRetry = viewModel::refresh,
                modifier = Modifier.padding(padding)
            )
            else -> DashboardContent(
                state = state,
                onNavigateToTransactions = onNavigateToTransactions,
                onNavigateToReview = onNavigateToReview,
                onNavigateToImport = onNavigateToImport,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun DashboardTopBar(
    period: DashboardPeriod,
    onPeriodChange: (DashboardPeriod) -> Unit
) {
    var showPeriodMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text("LedgerLens") },
        actions = {
            TextButton(onClick = { showPeriodMenu = true }) {
                Text(period.label)
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(
                expanded = showPeriodMenu,
                onDismissRequest = { showPeriodMenu = false }
            ) {
                DashboardPeriod.entries.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(p.label) },
                        onClick = {
                            onPeriodChange(p)
                            showPeriodMenu = false
                        }
                    )
                }
            }
        }
    )
}

@Composable
private fun DashboardContent(
    state: DashboardState,
    onNavigateToTransactions: () -> Unit,
    onNavigateToReview: () -> Unit,
    onNavigateToImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LedgerLensTheme.spacing

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        // Summary Card
        item {
            state.summary?.let { summary ->
                SummaryCard(summary = summary)
            }
        }

        // Review Alert
        if (state.reviewCount > 0) {
            item {
                ReviewAlertCard(
                    count = state.reviewCount,
                    onClick = onNavigateToReview
                )
            }
        }

        // Category Breakdown
        if (state.categoryBreakdown.isNotEmpty()) {
            item {
                CategoryBreakdownCard(
                    categories = state.categoryBreakdown,
                    onCategoryClick = { /* Navigate to filtered transactions */ }
                )
            }
        }

        // Recent Transactions
        if (state.recentTransactions.isNotEmpty()) {
            item {
                RecentTransactionsCard(
                    transactions = state.recentTransactions,
                    onViewAll = onNavigateToTransactions,
                    onTransactionClick = { /* Navigate to detail */ }
                )
            }
        }

        // Quick Actions
        item {
            QuickActionsCard(
                onImport = onNavigateToImport,
                onSplitReceipt = { /* Navigate to receipts */ }
            )
        }

        // Account Balances
        if (state.accountBalances.isNotEmpty()) {
            item {
                AccountBalancesCard(balances = state.accountBalances)
            }
        }
    }
}
```

### Step 4: Dashboard Cards

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/dashboard/DashboardCards.kt
package com.ledgerlens.ui.screens.dashboard

@Composable
fun SummaryCard(summary: SpendingSummary) {
    LedgerCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Cash Flow",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Income", style = MaterialTheme.typography.labelSmall)
                    MoneyText(
                        money = summary.totalIncome,
                        style = MoneyMedium,
                        colorBySign = true
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Expenses", style = MaterialTheme.typography.labelSmall)
                    MoneyText(
                        money = summary.totalExpenses.negate(),
                        style = MoneyMedium,
                        colorBySign = true
                    )
                }
            }

            Divider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Net", style = MaterialTheme.typography.titleSmall)
                MoneyText(
                    money = summary.netCashFlow,
                    style = MoneyLarge,
                    showSign = true,
                    colorBySign = true
                )
            }
        }
    }
}

@Composable
fun ReviewAlertCard(
    count: Int,
    onClick: () -> Unit
) {
    LedgerCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Pending
                )
                Column {
                    Text(
                        text = "$count items need review",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Tap to categorize",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
fun CategoryBreakdownCard(
    categories: List<CategorySpending>,
    onCategoryClick: (String) -> Unit
) {
    LedgerCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Spending by Category",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Pie chart or bar chart would go here
            // Using simple bars for now
            categories.take(5).forEach { category ->
                CategorySpendingRow(
                    category = category,
                    onClick = { onCategoryClick(category.categoryId) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CategorySpendingRow(
    category: CategorySpending,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Category icon/color
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = category.categoryColor.toColor(),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = category.categoryIcon ?: category.categoryName.take(1),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
        }

        // Name and bar
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.categoryName,
                style = MaterialTheme.typography.bodyMedium
            )
            LinearProgressIndicator(
                progress = { category.percentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = category.categoryColor.toColor()
            )
        }

        // Amount
        MoneyText(
            money = category.amount.negate(),
            style = MoneySmall
        )
    }
}

@Composable
fun RecentTransactionsCard(
    transactions: List<TransactionSummary>,
    onViewAll: () -> Unit,
    onTransactionClick: (String) -> Unit
) {
    LedgerCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = onViewAll) {
                    Text("View All")
                }
            }

            transactions.forEach { tx ->
                TransactionRow(
                    transaction = tx,
                    onClick = { onTransactionClick(tx.id) }
                )
            }
        }
    }
}

@Composable
private fun TransactionRow(
    transaction: TransactionSummary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = transaction.categoryName ?: "Uncategorized",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        MoneyText(
            money = transaction.amount,
            style = MoneySmall,
            colorBySign = true
        )
    }
}

@Composable
fun QuickActionsCard(
    onImport: () -> Unit,
    onSplitReceipt: () -> Unit
) {
    LedgerCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QuickActionButton(
                icon = Icons.Default.Upload,
                label = "Import",
                onClick = onImport
            )
            QuickActionButton(
                icon = Icons.Default.Receipt,
                label = "Split Receipt",
                onClick = onSplitReceipt
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        FilledTonalIconButton(onClick = onClick) {
            Icon(icon, contentDescription = label)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
```

---

## Acceptance Criteria

- [ ] Summary shows income, expenses, net cash flow
- [ ] Period selector changes data range
- [ ] Category breakdown shows top spending categories
- [ ] Review alert shows count of items needing review
- [ ] Recent transactions list with navigation
- [ ] Quick action buttons functional
- [ ] Loading, error, and empty states handled
- [ ] Pull-to-refresh works

---

## Estimated Complexity

**High** - Multiple data sources, charts, and interactive elements.

