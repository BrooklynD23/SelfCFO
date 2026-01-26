package com.ledgerlens.ui.screens.review

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.viewmodels.review.ReviewItemStatus
import com.ledgerlens.ui.viewmodels.review.ReviewItemUi
import com.ledgerlens.ui.viewmodels.review.ReviewType

/**
 * Card component displaying a review item with action buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewItemCard(
    item: ReviewItemUi,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onEdit: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isProcessed = item.status != ReviewItemStatus.PENDING

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                item.status == ReviewItemStatus.ACCEPTED -> Color(0xFFE8F5E9).copy(alpha = 0.5f) // TODO: Use theme
                item.status == ReviewItemStatus.REJECTED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                item.isPossibleDuplicate -> Color(0xFFFFF3E0).copy(alpha = 0.5f) // TODO: Use theme
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row with merchant and amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = item.normalizedMerchant,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        ReviewTypeBadge(reviewType = item.reviewType)
                    }
                    if (item.merchantName != item.normalizedMerchant) {
                        Text(
                            text = item.merchantName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = item.formattedAmount,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (item.amount < 0) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        Color(0xFF4CAF50) // TODO: Use LedgerLensTheme.colors.moneyPositive
                    }
                )
            }

            // Date and description
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).padding(start = 16.dp)
                    )
                }
            }

            // Suggested category with confidence
            if (item.hasSuggestion) {
                SuggestedCategoryRow(
                    categoryName = item.suggestedCategoryName!!,
                    confidence = item.confidence,
                    explanation = item.explanation
                )
            } else {
                Text(
                    text = "No category suggestion available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Duplicate warning if applicable
            if (item.isPossibleDuplicate && item.duplicateOf != null) {
                DuplicateWarning(
                    similarity = item.duplicateOf.similarityPercent,
                    existingDate = item.duplicateOf.date
                )
            }

            // Status indicator for processed items
            if (isProcessed) {
                ProcessedStatusRow(status = item.status)
            } else {
                // Action buttons for pending items
                ActionButtonsRow(
                    onAccept = onAccept,
                    onReject = onReject,
                    onEdit = onEdit,
                    hasSuggestion = item.hasSuggestion
                )
            }
        }
    }
}

@Composable
private fun ReviewTypeBadge(reviewType: ReviewType, modifier: Modifier = Modifier) {
    val (text, color) = when (reviewType) {
        ReviewType.LOW_CONFIDENCE -> "Low Confidence" to Color(0xFFFFA726) // TODO: Use theme
        ReviewType.POSSIBLE_DUPLICATE -> "Duplicate?" to Color(0xFFE57373) // TODO: Use theme
        ReviewType.UNCATEGORIZED -> "Uncategorized" to MaterialTheme.colorScheme.error
    }

    Surface(
        color = color.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun SuggestedCategoryRow(
    categoryName: String,
    confidence: Float,
    explanation: String,
    modifier: Modifier = Modifier
) {
    val confidenceColor = when {
        confidence >= 0.7f -> Color(0xFF4CAF50) // TODO: Use theme
        confidence >= 0.4f -> Color(0xFFFFA726) // TODO: Use theme
        else -> MaterialTheme.colorScheme.error
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Suggested:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                // Info icon - tooltip content available via explanation parameter
                IconButton(
                    onClick = { /* Could show explanation in a dialog */ },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = explanation.ifBlank { "Why this category" },
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "${(confidence * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = confidenceColor
            )
        }

        // Confidence bar
        LinearProgressIndicator(
            progress = confidence,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = confidenceColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun DuplicateWarning(similarity: Int, existingDate: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color(0xFFFFF3E0), // TODO: Use theme warningContainer
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFFF57C00) // TODO: Use theme warning
            )
            Text(
                text = "$similarity% similar to transaction on $existingDate",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFE65100) // TODO: Use theme onWarningContainer
            )
        }
    }
}

@Composable
private fun ProcessedStatusRow(status: ReviewItemStatus, modifier: Modifier = Modifier) {
    val (text, color) = when (status) {
        ReviewItemStatus.ACCEPTED -> "Accepted" to Color(0xFF4CAF50)
        ReviewItemStatus.REJECTED -> "Rejected" to MaterialTheme.colorScheme.error
        ReviewItemStatus.DEFERRED -> "Deferred" to Color(0xFFFFA726)
        ReviewItemStatus.DISMISSED -> "Dismissed" to MaterialTheme.colorScheme.onSurfaceVariant
        ReviewItemStatus.PENDING -> "Pending" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

@Composable
private fun ActionButtonsRow(
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onEdit: () -> Unit,
    hasSuggestion: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Edit/Categorize button
        FilledTonalIconButton(
            onClick = onEdit,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit category",
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Reject button
        IconButton(
            onClick = onReject,
            modifier = Modifier.size(40.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Reject suggestion",
                modifier = Modifier.size(20.dp)
            )
        }

        if (hasSuggestion) {
            Spacer(modifier = Modifier.width(8.dp))

            // Accept button
            IconButton(
                onClick = onAccept,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color(0xFFC8E6C9), // TODO: Use theme successContainer
                    contentColor = Color(0xFF2E7D32) // TODO: Use theme onSuccessContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Accept suggestion",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
