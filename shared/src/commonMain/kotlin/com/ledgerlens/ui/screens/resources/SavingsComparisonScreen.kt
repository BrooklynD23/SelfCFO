package com.ledgerlens.ui.screens.resources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.components.DisclaimerFooter
import com.ledgerlens.ui.components.cards.ComparisonCard
import com.ledgerlens.ui.components.cards.ComparisonOption
import com.ledgerlens.ui.components.cards.RiskLevel
import com.ledgerlens.ui.components.charts.generateLinearProjectionPoints
import com.ledgerlens.ui.components.charts.generateProjectionPoints
import com.ledgerlens.ui.theme.LedgerLensTheme
import com.ledgerlens.ui.theme.ShapePatterns

/**
 * Savings Comparison screen with personalized projections.
 * Shows different options for where to put surplus money.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsComparisonScreen(
    surplusAmount: Double = 500.0,
    debtBalance: Double = 5000.0,
    debtApr: Double = 0.18,
    onNavigateBack: () -> Unit = {},
    onSelectOption: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    var selectedOptionId by remember { mutableStateOf<String?>(null) }

    val options = getComparisonOptions(
        surplusAmount = surplusAmount,
        debtBalance = debtBalance,
        debtApr = debtApr
    )

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
                title = {
                    Text(
                        text = "Compare Options",
                        style = typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with context
            item {
                ContextHeader(surplusAmount = surplusAmount)
            }

            // Comparison cards
            items(options) { option ->
                ComparisonCard(
                    option = option,
                    isSelected = option.title == selectedOptionId,
                    onClick = {
                        selectedOptionId = option.title
                        onSelectOption(option.title)
                    }
                )
            }

            // Methodology note
            item {
                MethodologyNote()
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
private fun ContextHeader(
    surplusAmount: Double,
    modifier: Modifier = Modifier
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ShapePatterns.insightCard,
        color = colors.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Your Monthly Surplus",
                style = typography.labelLarge,
                color = colors.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$${surplusAmount.toInt()}",
                style = typography.dataMedium,
                fontWeight = FontWeight.Bold,
                color = colors.success
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "See how this amount could grow with different strategies over 5 years.",
                style = typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MethodologyNote(
    modifier: Modifier = Modifier
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ShapePatterns.card,
        color = colors.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "How We Calculate",
                style = typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = colors.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Projections assume consistent monthly contributions and don't account for taxes, inflation, or market volatility. Actual returns may vary significantly.",
                style = typography.bodySmall,
                color = colors.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

private fun getComparisonOptions(
    surplusAmount: Double,
    debtBalance: Double,
    debtApr: Double
): List<ComparisonOption> {
    val monthlyContribution = surplusAmount
    val years = 5
    val totalContributions = monthlyContribution * 12 * years

    // High-Yield Savings: ~4.5% APY
    val hysSavingsRate = 0.045
    val hysProjection = calculateSavingsProjection(monthlyContribution, hysSavingsRate, years)
    val hysGrowth = hysProjection - totalContributions

    // Index Fund: ~8% average annual return (historical S&P 500)
    val indexFundRate = 0.08
    val indexProjection = calculateSavingsProjection(monthlyContribution, indexFundRate, years)
    val indexGrowth = indexProjection - totalContributions

    // Debt Repayment: Interest saved
    val interestSaved = calculateInterestSaved(monthlyContribution, debtBalance, debtApr, years)

    return listOf(
        ComparisonOption(
            title = "High-Yield Savings",
            rateText = "4.5%",
            rateDescription = "APY",
            riskLevel = RiskLevel.LOW,
            projectionPoints = generateProjectionPoints(
                principal = monthlyContribution,
                annualRate = hysSavingsRate,
                years = years
            ),
            projectionLabel = "~$${hysProjection.toInt()} in $years years",
            description = "FDIC insured, easy access to funds"
        ),
        ComparisonOption(
            title = "Index Fund",
            rateText = "~8%",
            rateDescription = "Avg. Annual",
            riskLevel = RiskLevel.MEDIUM,
            projectionPoints = generateProjectionPoints(
                principal = monthlyContribution,
                annualRate = indexFundRate,
                years = years
            ),
            projectionLabel = "~$${indexProjection.toInt()} in $years years",
            description = "Higher growth potential, market exposure"
        ),
        ComparisonOption(
            title = "Debt Repayment",
            rateText = "${(debtApr * 100).toInt()}%",
            rateDescription = "APR Saved",
            riskLevel = RiskLevel.GUARANTEED,
            projectionPoints = generateLinearProjectionPoints(
                startValue = debtBalance,
                endValue = (debtBalance - monthlyContribution * 12 * years).coerceAtLeast(0.0),
                points = 20
            ),
            projectionLabel = "~$${interestSaved.toInt()} interest saved",
            description = "Guaranteed return, reduces monthly obligations"
        )
    )
}

private fun calculateSavingsProjection(
    monthlyContribution: Double,
    annualRate: Double,
    years: Int
): Double {
    val monthlyRate = annualRate / 12
    val months = years * 12
    var total = 0.0

    for (month in 1..months) {
        total = (total + monthlyContribution) * (1 + monthlyRate)
    }

    return total
}

private fun calculateInterestSaved(
    monthlyPayment: Double,
    initialBalance: Double,
    apr: Double,
    years: Int
): Double {
    val monthlyRate = apr / 12
    var balance = initialBalance
    var totalInterestWithMinPayment = 0.0
    var totalInterestWithExtraPayment = 0.0

    // Calculate interest with minimum payment (assume 2% of balance)
    var balanceMin = initialBalance
    for (month in 1..(years * 12)) {
        val interest = balanceMin * monthlyRate
        totalInterestWithMinPayment += interest
        val minPayment = (balanceMin * 0.02).coerceAtLeast(25.0)
        balanceMin = (balanceMin + interest - minPayment).coerceAtLeast(0.0)
    }

    // Calculate interest with extra payment
    var balanceExtra = initialBalance
    for (month in 1..(years * 12)) {
        val interest = balanceExtra * monthlyRate
        totalInterestWithExtraPayment += interest
        val payment = monthlyPayment + (balanceExtra * 0.02).coerceAtLeast(25.0)
        balanceExtra = (balanceExtra + interest - payment).coerceAtLeast(0.0)
    }

    return totalInterestWithMinPayment - totalInterestWithExtraPayment
}
