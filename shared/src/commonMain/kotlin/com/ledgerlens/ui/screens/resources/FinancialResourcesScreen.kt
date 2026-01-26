package com.ledgerlens.ui.screens.resources

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.components.DisclaimerFooter
import com.ledgerlens.ui.theme.LedgerLensTheme
import com.ledgerlens.ui.theme.ShapePatterns

/**
 * Resource category for the hub screen.
 */
data class ResourceCategory(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val items: List<ResourceItem>
)

/**
 * Individual resource item.
 */
data class ResourceItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: ResourceType,
    val icon: ImageVector
)

enum class ResourceType {
    DEEP_DIVE,
    COMPARISON,
    TOOL,
    EXTERNAL
}

/**
 * Financial Resources hub screen.
 * Provides access to educational content, comparisons, and tools.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialResourcesScreen(
    onNavigateToIndexFunds: () -> Unit = {},
    onNavigateToSavingsComparison: () -> Unit = {},
    onNavigateToConcept: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    val resources = getDefaultResources()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Learn",
                            style = typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Financial education and tools",
                            style = typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Featured section
            item {
                FeaturedResourceCard(
                    title = "Compare Savings Options",
                    description = "See how your money could grow with different strategies",
                    icon = Icons.Default.Compare,
                    onClick = onNavigateToSavingsComparison
                )
            }

            // Resource categories
            items(resources) { category ->
                ResourceCategorySection(
                    category = category,
                    onItemClick = { item ->
                        when (item.id) {
                            "index-funds" -> onNavigateToIndexFunds()
                            "savings-comparison" -> onNavigateToSavingsComparison()
                            else -> onNavigateToConcept(item.id)
                        }
                    }
                )
            }

            // Disclaimer footer
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
private fun FeaturedResourceCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ShapePatterns.insightCard,
        color = colors.primary,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = typography.bodySmall,
                    color = colors.onPrimary.copy(alpha = 0.8f)
                )
            }

            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ResourceCategorySection(
    category: ResourceCategory,
    onItemClick: (ResourceItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = category.title,
                style = typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = category.description,
            style = typography.bodySmall,
            color = colors.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        category.items.forEach { item ->
            ResourceItemRow(
                item = item,
                onClick = { onItemClick(item) }
            )
            if (item != category.items.last()) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ResourceItemRow(
    item: ResourceItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ShapePatterns.card,
        color = colors.surface,
        shadowElevation = 1.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface
                )
                Text(
                    text = item.subtitle,
                    style = typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }

            // Type badge
            val badgeText = when (item.type) {
                ResourceType.DEEP_DIVE -> "Deep Dive"
                ResourceType.COMPARISON -> "Compare"
                ResourceType.TOOL -> "Tool"
                ResourceType.EXTERNAL -> "External"
            }
            Surface(
                shape = ShapePatterns.chipSmall,
                color = colors.surfaceVariant
            ) {
                Text(
                    text = badgeText,
                    style = typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun getDefaultResources(): List<ResourceCategory> = listOf(
    ResourceCategory(
        id = "investing",
        title = "Investing Basics",
        description = "Learn the fundamentals of growing your wealth",
        icon = Icons.Default.TrendingUp,
        items = listOf(
            ResourceItem(
                id = "index-funds",
                title = "Index Funds Explained",
                subtitle = "A simple way to invest in the market",
                type = ResourceType.DEEP_DIVE,
                icon = Icons.Default.AccountBalance
            ),
            ResourceItem(
                id = "compound-interest",
                title = "The Power of Compound Interest",
                subtitle = "How your money grows over time",
                type = ResourceType.DEEP_DIVE,
                icon = Icons.Default.TrendingUp
            )
        )
    ),
    ResourceCategory(
        id = "savings",
        title = "Savings Strategies",
        description = "Make the most of your savings",
        icon = Icons.Default.Savings,
        items = listOf(
            ResourceItem(
                id = "savings-comparison",
                title = "Compare Savings Options",
                subtitle = "High-yield savings vs investments",
                type = ResourceType.COMPARISON,
                icon = Icons.Default.Compare
            ),
            ResourceItem(
                id = "emergency-fund",
                title = "Building an Emergency Fund",
                subtitle = "How much you really need",
                type = ResourceType.DEEP_DIVE,
                icon = Icons.Default.Savings
            )
        )
    ),
    ResourceCategory(
        id = "concepts",
        title = "Financial Concepts",
        description = "Key terms and ideas explained",
        icon = Icons.Default.School,
        items = listOf(
            ResourceItem(
                id = "apy-vs-apr",
                title = "APY vs APR",
                subtitle = "Understanding interest rates",
                type = ResourceType.DEEP_DIVE,
                icon = Icons.Default.Lightbulb
            ),
            ResourceItem(
                id = "risk-tolerance",
                title = "Understanding Risk",
                subtitle = "Finding your comfort level",
                type = ResourceType.DEEP_DIVE,
                icon = Icons.Default.Lightbulb
            )
        )
    )
)
