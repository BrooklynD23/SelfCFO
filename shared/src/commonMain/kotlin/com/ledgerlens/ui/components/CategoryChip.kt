package com.ledgerlens.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ledgerlens.categorization.Category
import com.ledgerlens.ui.theme.LedgerLensColors
import com.ledgerlens.ui.theme.LedgerLensTheme
import com.ledgerlens.ui.theme.ShapePatterns
import com.ledgerlens.ui.theme.SpacingPatterns

/**
 * Size variants for category chips.
 */
enum class CategoryChipSize {
    SMALL,
    MEDIUM,
    LARGE
}

/**
 * Style variants for category chips.
 */
enum class CategoryChipStyle {
    FILLED,
    OUTLINED,
    TONAL
}

/**
 * Category display chip component.
 * Shows category name with optional icon and colored background.
 *
 * @param categoryName Name of the category to display.
 * @param modifier Modifier for the chip.
 * @param categoryColor Color for the chip (derived from category if not provided).
 * @param icon Optional icon to show before the name.
 * @param size Size variant (SMALL, MEDIUM, LARGE).
 * @param style Style variant (FILLED, OUTLINED, TONAL).
 * @param onClick Optional click handler.
 */
@Composable
fun CategoryChip(
    categoryName: String,
    modifier: Modifier = Modifier,
    categoryColor: Color? = null,
    icon: ImageVector? = null,
    size: CategoryChipSize = CategoryChipSize.MEDIUM,
    style: CategoryChipStyle = CategoryChipStyle.TONAL,
    onClick: (() -> Unit)? = null
) {
    val chipColor = categoryColor ?: getCategoryColor(categoryName)
    
    val (backgroundColor, contentColor, borderColor) = when (style) {
        CategoryChipStyle.FILLED -> Triple(
            chipColor,
            Color.White,
            Color.Transparent
        )
        CategoryChipStyle.OUTLINED -> Triple(
            Color.Transparent,
            chipColor,
            chipColor
        )
        CategoryChipStyle.TONAL -> Triple(
            chipColor.copy(alpha = 0.15f),
            chipColor,
            Color.Transparent
        )
    }

    val (horizontalPadding, verticalPadding, iconSize, textStyle) = when (size) {
        CategoryChipSize.SMALL -> Quadruple(
            8.dp,
            4.dp,
            12.dp,
            LedgerLensTheme.typography.labelSmall
        )
        CategoryChipSize.MEDIUM -> Quadruple(
            SpacingPatterns.chipPaddingHorizontal,
            SpacingPatterns.chipPaddingVertical,
            16.dp,
            LedgerLensTheme.typography.labelMedium
        )
        CategoryChipSize.LARGE -> Quadruple(
            16.dp,
            8.dp,
            20.dp,
            LedgerLensTheme.typography.labelLarge
        )
    }

    val chipModifier = modifier
        .clip(ShapePatterns.chip)
        .background(backgroundColor)
        .then(
            if (borderColor != Color.Transparent) {
                Modifier.border(1.dp, borderColor, ShapePatterns.chip)
            } else {
                Modifier
            }
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )
        .padding(horizontal = horizontalPadding, vertical = verticalPadding)

    Row(
        modifier = chipModifier,
        horizontalArrangement = Arrangement.spacedBy(SpacingPatterns.iconTextGap / 2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = contentColor
            )
        }

        Text(
            text = categoryName,
            style = textStyle,
            color = contentColor,
            maxLines = 1
        )
    }
}

/**
 * Category chip using Category data class.
 */
@Composable
fun CategoryChip(
    category: Category,
    modifier: Modifier = Modifier,
    size: CategoryChipSize = CategoryChipSize.MEDIUM,
    style: CategoryChipStyle = CategoryChipStyle.TONAL,
    onClick: (() -> Unit)? = null
) {
    val color = category.color?.let { parseHexColor(it) }
    
    CategoryChip(
        categoryName = category.name,
        modifier = modifier,
        categoryColor = color,
        size = size,
        style = style,
        onClick = onClick
    )
}

/**
 * Selectable category chip for filters.
 */
@Composable
fun SelectableCategoryChip(
    categoryName: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    categoryColor: Color? = null,
    onClick: () -> Unit
) {
    CategoryChip(
        categoryName = categoryName,
        modifier = modifier,
        categoryColor = categoryColor,
        style = if (selected) CategoryChipStyle.FILLED else CategoryChipStyle.OUTLINED,
        onClick = onClick
    )
}

/**
 * Get semantic color for a category name.
 */
fun getCategoryColor(categoryName: String): Color {
    return when (categoryName.lowercase()) {
        "food", "dining", "restaurants", "groceries" -> LedgerLensColors.CategoryFood
        "transport", "transportation", "travel", "gas" -> LedgerLensColors.CategoryTransport
        "shopping", "retail" -> LedgerLensColors.CategoryShopping
        "entertainment", "fun" -> LedgerLensColors.CategoryEntertainment
        "bills", "utilities" -> LedgerLensColors.CategoryBills
        "health", "healthcare", "medical" -> LedgerLensColors.CategoryHealth
        "income", "salary", "wages" -> LedgerLensColors.CategoryIncome
        "transfer", "transfers" -> LedgerLensColors.CategoryTransfer
        "uncategorized" -> LedgerLensColors.CategoryUncategorized
        else -> LedgerLensColors.Primary
    }
}

private fun parseHexColor(hex: String): Color? {
    return try {
        val colorInt = hex.removePrefix("#").toLong(16)
        Color(colorInt or 0xFF000000)
    } catch (e: Exception) {
        null
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
