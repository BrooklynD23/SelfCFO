package com.ledgerlens.ui.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.components.CategoryChip
import com.ledgerlens.ui.components.CategoryChipSize
import com.ledgerlens.ui.components.CategoryChipStyle
import com.ledgerlens.ui.components.MoneyText
import com.ledgerlens.ui.components.MoneyTextSize
import com.ledgerlens.ui.components.MoneyTextStyle
import com.ledgerlens.ui.screens.dashboard.TransactionUiModel

/**
 * Transaction list item component showing date, merchant, amount, and category.
 */
@Composable
fun TransactionItem(
    transaction: TransactionUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = true,
    showCategory: Boolean = true,
    compact: Boolean = false
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (transaction.needsReview) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 12.dp else 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side: merchant info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = transaction.displayMerchant,
                        style = if (compact) {
                            MaterialTheme.typography.bodyMedium
                        } else {
                            MaterialTheme.typography.titleSmall
                        },
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Indicators
                    if (transaction.hasReceipt) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = "Has receipt",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (transaction.needsReview) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Needs review",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (!compact) {
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (showDate) {
                            Text(
                                text = transaction.date,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (showCategory && transaction.category != null) {
                            CategoryChip(
                                category = transaction.category,
                                size = CategoryChipSize.SMALL,
                                style = CategoryChipStyle.TONAL
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right side: amount
            Column(
                horizontalAlignment = Alignment.End
            ) {
                MoneyText(
                    money = transaction.amount,
                    size = if (compact) MoneyTextSize.SMALL else MoneyTextSize.MEDIUM,
                    style = MoneyTextStyle.COLORED
                )

                if (!compact && transaction.accountName != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = transaction.accountName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Compact transaction item for summary lists.
 */
@Composable
fun TransactionItemCompact(
    transaction: TransactionUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TransactionItem(
        transaction = transaction,
        onClick = onClick,
        modifier = modifier,
        showDate = false,
        showCategory = false,
        compact = true
    )
}

/**
 * Transaction item with date header for grouped lists.
 */
@Composable
fun TransactionItemWithDateHeader(
    transaction: TransactionUiModel,
    dateHeader: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (dateHeader != null) {
            Text(
                text = dateHeader,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        TransactionItem(
            transaction = transaction,
            onClick = onClick,
            showDate = dateHeader == null
        )
    }
}

/**
 * Selectable transaction item for bulk operations.
 */
@Composable
fun SelectableTransactionItem(
    transaction: TransactionUiModel,
    selected: Boolean,
    onSelectionChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Selection indicator
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable { onSelectionChange(!selected) },
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Text(
                    text = "✓",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        TransactionItem(
            transaction = transaction,
            onClick = onClick,
            modifier = Modifier.weight(1f)
        )
    }
}
