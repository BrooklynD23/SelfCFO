package com.ledgerlens.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.theme.LedgerLensTheme
import com.ledgerlens.ui.theme.SpacingPatterns

@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(SpacingPatterns.screenPaddingHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = iconTint)
            Spacer(modifier = Modifier.height(LedgerLensTheme.spacing.medium))
        }
        Text(text = title, style = LedgerLensTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(LedgerLensTheme.spacing.small))
        Text(text = description, style = LedgerLensTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(LedgerLensTheme.spacing.large))
            Button(onClick = onAction) { Text(text = actionLabel) }
        }
    }
}

@Composable
fun EmptyTransactionsState(modifier: Modifier = Modifier, icon: ImageVector? = null, onImport: (() -> Unit)? = null) {
    EmptyState(title = "No Transactions", description = "Import your bank statement or add transactions manually to get started.",
        modifier = modifier, icon = icon, actionLabel = if (onImport != null) "Import Statement" else null, onAction = onImport)
}

@Composable
fun EmptySearchState(query: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClearSearch: (() -> Unit)? = null) {
    EmptyState(title = "No Results", description = "No transactions found matching \"$query\". Try a different search term.",
        modifier = modifier, icon = icon, actionLabel = if (onClearSearch != null) "Clear Search" else null, onAction = onClearSearch)
}

@Composable
fun EmptyFilterState(modifier: Modifier = Modifier, icon: ImageVector? = null, onClearFilters: (() -> Unit)? = null) {
    EmptyState(title = "No Matching Transactions", description = "No transactions match your current filters. Try adjusting your filters.",
        modifier = modifier, icon = icon, actionLabel = if (onClearFilters != null) "Clear Filters" else null, onAction = onClearFilters)
}

@Composable
fun EmptyCategoriesState(modifier: Modifier = Modifier, icon: ImageVector? = null, onCreateCategory: (() -> Unit)? = null) {
    EmptyState(title = "No Custom Categories", description = "Create custom categories to organize your transactions your way.",
        modifier = modifier, icon = icon, actionLabel = if (onCreateCategory != null) "Create Category" else null, onAction = onCreateCategory)
}

@Composable
fun EmptyStateCompact(message: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Column(modifier = modifier.fillMaxWidth().padding(LedgerLensTheme.spacing.medium), horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(LedgerLensTheme.spacing.small))
        }
        Text(text = message, style = LedgerLensTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
