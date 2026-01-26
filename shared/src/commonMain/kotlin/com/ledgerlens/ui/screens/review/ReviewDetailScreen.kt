package com.ledgerlens.ui.screens.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.viewmodels.review.CategoryAlternative
import com.ledgerlens.ui.viewmodels.review.DuplicateInfo
import com.ledgerlens.ui.viewmodels.review.ReviewItemUi
import com.ledgerlens.ui.viewmodels.review.ReviewType
import com.ledgerlens.ui.viewmodels.review.ReviewViewModel

/**
 * Full review detail screen for a single review item.
 * Allows category selection, duplicate comparison, and confirm/skip actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewDetailScreen(
    viewModel: ReviewViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCategories: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedItem by viewModel.selectedItem.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    // Track selected category for rejection
    var selectedCategoryId by remember(selectedItem?.id) {
        mutableStateOf(selectedItem?.suggestedCategoryId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Transaction") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearSelection()
                        onNavigateBack()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Progress indicator
                    Text(
                        text = "${uiState.stats.processedCount + 1}/${uiState.stats.totalItems}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        if (selectedItem == null) {
            // No item selected - show empty state or navigate back
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No item selected")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onNavigateBack) {
                    Text("Go Back")
                }
            }
        } else {
            val item = selectedItem!!

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Transaction details card
                TransactionDetailsCard(item = item)

                // Duplicate comparison if applicable
                if (item.isPossibleDuplicate && item.duplicateOf != null) {
                    DuplicateComparisonCard(
                        current = item,
                        duplicate = item.duplicateOf
                    )
                }

                // Category explanation
                ExplanationCard(
                    explanation = item.explanation,
                    reviewType = item.reviewType
                )

                // Category selection
                CategorySelectionCard(
                    suggestedCategory = item.suggestedCategoryId to item.suggestedCategoryName,
                    alternatives = item.alternatives,
                    selectedCategoryId = selectedCategoryId,
                    onCategorySelected = { selectedCategoryId = it },
                    onBrowseCategories = onNavigateToCategories
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Action buttons
                ActionButtons(
                    item = item,
                    selectedCategoryId = selectedCategoryId,
                    onAccept = {
                        viewModel.acceptSuggestion(item.id)
                    },
                    onReject = {
                        selectedCategoryId?.let { categoryId ->
                            viewModel.rejectSuggestion(item.id, categoryId)
                        }
                    },
                    onSkip = {
                        viewModel.deferItem(item.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun TransactionDetailsCard(item: ReviewItemUi, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Merchant name with badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.normalizedMerchant,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (item.merchantName != item.normalizedMerchant) {
                        Text(
                            text = "Raw: ${item.merchantName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = item.formattedAmount,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (item.amount < 0) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        Color(0xFF4CAF50) // TODO: Use theme
                    }
                )
            }

            Divider()

            // Details grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DetailItem(label = "Date", value = item.date)
                DetailItem(label = "Transaction ID", value = item.transactionId)
            }

            if (item.description.isNotBlank()) {
                DetailItem(label = "Description", value = item.description)
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun DuplicateComparisonCard(current: ReviewItemUi, duplicate: DuplicateInfo, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3E0) // TODO: Use theme warningContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = Color(0xFFF57C00) // TODO: Use theme warning
                )
                Text(
                    text = "Possible Duplicate (${duplicate.similarityPercent}% similar)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE65100) // TODO: Use theme
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Current transaction
                ComparisonColumn(
                    title = "New",
                    date = current.date,
                    amount = current.formattedAmount,
                    modifier = Modifier.weight(1f)
                )

                // Existing transaction
                ComparisonColumn(
                    title = "Existing",
                    date = duplicate.date,
                    amount = formatAmount(duplicate.amount),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ComparisonColumn(title: String, date: String, amount: String, modifier: Modifier = Modifier) {
    OutlinedCard(
        modifier = modifier,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatAmount(amountCents: Long): String {
    val absAmount = kotlin.math.abs(amountCents)
    val dollars = absAmount / 100
    val cents = absAmount % 100
    val sign = if (amountCents < 0) "-" else "+"
    return "$sign$$dollars.${cents.toString().padStart(2, '0')}"
}

@Composable
private fun ExplanationCard(explanation: String, reviewType: ReviewType, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Why This Needs Review",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                color = when (reviewType) {
                    ReviewType.LOW_CONFIDENCE -> Color(0xFFFFA726).copy(alpha = 0.2f)
                    ReviewType.POSSIBLE_DUPLICATE -> Color(0xFFE57373).copy(alpha = 0.2f)
                    ReviewType.UNCATEGORIZED -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                },
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = when (reviewType) {
                        ReviewType.LOW_CONFIDENCE -> "Low confidence classification"
                        ReviewType.POSSIBLE_DUPLICATE -> "Possible duplicate transaction"
                        ReviewType.UNCATEGORIZED -> "Unable to categorize automatically"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun CategorySelectionCard(
    suggestedCategory: Pair<String?, String?>,
    alternatives: List<CategoryAlternative>,
    selectedCategoryId: String?,
    onCategorySelected: (String) -> Unit,
    onBrowseCategories: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Select Category",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            // Suggested category option
            if (suggestedCategory.first != null && suggestedCategory.second != null) {
                CategoryOption(
                    categoryId = suggestedCategory.first!!,
                    categoryName = suggestedCategory.second!!,
                    isSelected = selectedCategoryId == suggestedCategory.first,
                    isSuggested = true,
                    onClick = { onCategorySelected(suggestedCategory.first!!) }
                )
            }

            // Alternative categories
            alternatives.forEach { alt ->
                CategoryOption(
                    categoryId = alt.categoryId,
                    categoryName = "${alt.categoryName} (${alt.confidencePercent}%)",
                    isSelected = selectedCategoryId == alt.categoryId,
                    isSuggested = false,
                    onClick = { onCategorySelected(alt.categoryId) }
                )
            }

            // Browse all categories button
            OutlinedButton(
                onClick = onBrowseCategories,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Browse All Categories")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryOption(
    categoryId: String,
    categoryName: String,
    isSelected: Boolean,
    isSuggested: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
                if (isSuggested) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = "Suggested",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ActionButtons(
    item: ReviewItemUi,
    selectedCategoryId: String?,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Accept suggestion (only if has suggestion and it's selected)
        if (item.hasSuggestion && selectedCategoryId == item.suggestedCategoryId) {
            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50) // TODO: Use theme success
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Accept Suggestion")
            }
        }

        // Confirm with selected category (if different from suggestion)
        if (selectedCategoryId != null && selectedCategoryId != item.suggestedCategoryId) {
            Button(
                onClick = onReject,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Confirm Selection")
            }
        }

        // Skip / Defer
        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Skip for Now")
        }

        // Dismiss as not duplicate (for duplicate review type)
        if (item.isPossibleDuplicate) {
            OutlinedButton(
                onClick = onAccept, // Accept means "not a duplicate, keep it"
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Not a Duplicate")
            }
        }
    }
}
