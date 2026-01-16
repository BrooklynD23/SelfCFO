package com.ledgerlens.ui.screens.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerlens.categorization.Category
import com.ledgerlens.ui.components.CategoryChip
import com.ledgerlens.ui.components.CategoryChipSize
import com.ledgerlens.ui.components.CategoryChipStyle
import com.ledgerlens.ui.components.ConfidenceBadge
import com.ledgerlens.ui.components.ErrorState
import com.ledgerlens.ui.components.LedgerLensCard
import com.ledgerlens.ui.components.LedgerLensElevatedCard
import com.ledgerlens.ui.components.LoadingIndicator
import com.ledgerlens.ui.components.MoneyTextLarge
import com.ledgerlens.ui.screens.dashboard.TransactionUiModel
import com.ledgerlens.ui.viewmodels.transactions.TransactionDetailUiState
import com.ledgerlens.ui.viewmodels.transactions.TransactionsViewModel

/**
 * Transaction detail screen showing full transaction information
 * with category editing capability.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    viewModel: TransactionsViewModel,
    transactionId: String,
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.detailState.collectAsState()

    LaunchedEffect(transactionId) {
        viewModel.loadTransactionDetail(transactionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (uiState.transaction != null && !uiState.isEditing) {
                        IconButton(onClick = viewModel::startEditing) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        TransactionDetailContent(
            uiState = uiState,
            onCategorySelected = viewModel::updateTransactionCategory,
            onCancelEdit = viewModel::cancelEditing,
            onRetry = { viewModel.loadTransactionDetail(transactionId) },
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@Composable
private fun TransactionDetailContent(
    uiState: TransactionDetailUiState,
    onCategorySelected: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        uiState.isLoading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator(message = "Loading transaction...")
            }
        }

        uiState.error != null && uiState.transaction == null -> {
            ErrorState(
                title = "Unable to Load Transaction",
                message = uiState.error,
                onRetry = onRetry,
                modifier = modifier.fillMaxSize()
            )
        }

        uiState.transaction != null -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Amount header
                AmountHeader(transaction = uiState.transaction)

                // Merchant info card
                MerchantInfoCard(transaction = uiState.transaction)

                // Category section
                CategorySection(
                    transaction = uiState.transaction,
                    availableCategories = uiState.availableCategories,
                    isEditing = uiState.isEditing,
                    isSaving = uiState.isSaving,
                    onCategorySelected = onCategorySelected,
                    onCancelEdit = onCancelEdit
                )

                // Additional details
                AdditionalDetailsCard(transaction = uiState.transaction)

                // Receipt section (if applicable)
                if (uiState.transaction.hasReceipt) {
                    ReceiptSection()
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun AmountHeader(
    transaction: TransactionUiModel,
    modifier: Modifier = Modifier
) {
    LedgerLensElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MoneyTextLarge(
                money = transaction.amount,
                showSign = true
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (transaction.isIncome) "Income" else "Expense",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MerchantInfoCard(
    transaction: TransactionUiModel,
    modifier: Modifier = Modifier
) {
    LedgerLensCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailRow(
                label = "Merchant",
                value = transaction.displayMerchant
            )

            if (transaction.normalizedMerchant != null &&
                transaction.normalizedMerchant != transaction.merchantName
            ) {
                DetailRow(
                    label = "Original Name",
                    value = transaction.merchantName,
                    isSecondary = true
                )
            }

            DetailRow(
                label = "Date",
                value = transaction.date
            )

            if (transaction.description.isNotBlank() &&
                transaction.description != transaction.merchantName
            ) {
                DetailRow(
                    label = "Description",
                    value = transaction.description
                )
            }

            if (transaction.accountName != null) {
                DetailRow(
                    label = "Account",
                    value = transaction.accountName
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySection(
    transaction: TransactionUiModel,
    availableCategories: List<Category>,
    isEditing: Boolean,
    isSaving: Boolean,
    onCategorySelected: (String) -> Unit,
    onCancelEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    LedgerLensCard(modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                if (transaction.needsReview) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Needs Review",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isEditing) {
                // Category selection grid
                Text(
                    text = "Select a category:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isSaving) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableCategories.forEach { category ->
                            CategoryChip(
                                category = category,
                                size = CategoryChipSize.MEDIUM,
                                style = if (category.id == transaction.category?.id) {
                                    CategoryChipStyle.FILLED
                                } else {
                                    CategoryChipStyle.OUTLINED
                                },
                                onClick = { onCategorySelected(category.id) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = onCancelEdit,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel")
                    }
                }
            } else {
                // Display current category
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (transaction.category != null) {
                        CategoryChip(
                            category = transaction.category,
                            size = CategoryChipSize.LARGE
                        )
                    } else {
                        CategoryChip(
                            categoryName = "Uncategorized",
                            size = CategoryChipSize.LARGE,
                            style = CategoryChipStyle.OUTLINED
                        )
                    }

                    if (transaction.categoryConfidence != null) {
                        ConfidenceBadge(
                            confidence = transaction.categoryConfidence
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdditionalDetailsCard(
    transaction: TransactionUiModel,
    modifier: Modifier = Modifier
) {
    if (transaction.tags.isEmpty()) return

    LedgerLensCard(modifier = modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "Tags",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                transaction.tags.forEach { tag ->
                    Text(
                        text = "#$tag",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiptSection(
    modifier: Modifier = Modifier
) {
    LedgerLensCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Receipt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Receipt Attached",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedButton(onClick = { /* TODO: View receipt */ }) {
                Text("View")
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    isSecondary: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = if (isSecondary) {
                MaterialTheme.typography.bodySmall
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = if (isSecondary) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontWeight = if (isSecondary) FontWeight.Normal else FontWeight.Medium
        )
    }
}
