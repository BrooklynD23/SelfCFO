package com.ledgerlens.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.theme.Elevation
import com.ledgerlens.ui.theme.LedgerLensTheme
import com.ledgerlens.ui.theme.ShapePatterns
import com.ledgerlens.ui.theme.SpacingPatterns

/**
 * Styled card component for LedgerLens.
 * Provides consistent elevation, shape, and padding.
 */
@Composable
fun LedgerLensCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = ShapePatterns.card,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    elevation: Dp = Elevation.card,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Card(
        modifier = cardModifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = border
    ) {
        Column(
            modifier = Modifier.padding(SpacingPatterns.cardPadding),
            content = content
        )
    }
}

/**
 * Outlined card variant for secondary content.
 */
@Composable
fun LedgerLensOutlinedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = ShapePatterns.card,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    content: @Composable ColumnScope.() -> Unit
) {
    LedgerLensCard(
        modifier = modifier,
        onClick = onClick,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = Elevation.none,
        border = BorderStroke(1.dp, borderColor),
        content = content
    )
}

/**
 * Elevated card variant for prominent content.
 */
@Composable
fun LedgerLensElevatedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = ShapePatterns.card,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    LedgerLensCard(
        modifier = modifier,
        onClick = onClick,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = Elevation.level2,
        content = content
    )
}

/**
 * Transaction card with semantic styling for income/expense.
 */
@Composable
fun TransactionCard(
    modifier: Modifier = Modifier,
    isIncome: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val accentColor = if (isIncome) {
        LedgerLensTheme.colors.moneyPositive
    } else {
        MaterialTheme.colorScheme.surface
    }

    LedgerLensCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = accentColor.copy(alpha = 0.05f),
        content = content
    )
}
