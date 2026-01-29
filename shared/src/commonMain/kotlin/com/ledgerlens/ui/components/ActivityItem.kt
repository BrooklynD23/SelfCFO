package com.ledgerlens.ui.components

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.theme.LedgerLensTheme
import com.ledgerlens.ui.theme.ShapePatterns

/**
 * Emoji icons for different transaction categories.
 */
object CategoryEmojis {
    const val FOOD = "\uD83C\uDF54" // burger
    const val COFFEE = "\u2615" // coffee
    const val GROCERIES = "\uD83D\uDED2" // shopping cart
    const val TRANSPORT = "\uD83D\uDE97" // car
    const val GAS = "\u26FD" // fuel pump
    const val ENTERTAINMENT = "\uD83C\uDFAC" // clapper board
    const val SHOPPING = "\uD83D\uDECD\uFE0F" // shopping bags
    const val BILLS = "\uD83D\uDCB3" // credit card
    const val HEALTH = "\uD83C\uDFE5" // hospital
    const val INCOME = "\uD83D\uDCB0" // money bag
    const val TRANSFER = "\uD83D\uDD04" // arrows
    const val SUBSCRIPTION = "\uD83D\uDCF1" // phone
    const val UNKNOWN = "\uD83D\uDCB8" // money with wings
}

/**
 * Data for an activity/transaction item.
 */
data class ActivityItemData(
    val id: String,
    val emoji: String,
    val title: String,
    val subtitle: String,
    val amount: String,
    val isPositive: Boolean,
    val timestamp: String? = null
)

/**
 * Recent activity item showing a transaction with emoji icon.
 */
@Composable
fun ActivityItem(
    data: ActivityItemData,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    val amountColor = if (data.isPositive) colors.moneyPositive else colors.onSurface

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            ),
        shape = ShapePatterns.activityItem,
        color = colors.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(ShapePatterns.avatar)
                    .background(colors.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = data.emoji,
                    style = typography.titleLarge
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title and subtitle
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = data.title,
                    style = typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = data.subtitle,
                    style = typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Amount and timestamp
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = if (data.isPositive) "+${data.amount}" else data.amount,
                    style = typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor
                )
                data.timestamp?.let { time ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = time,
                        style = typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Section header for activity groups.
 */
@Composable
fun ActivitySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface
        )
        action?.invoke()
    }
}

/**
 * Get emoji for a category name.
 */
fun getEmojiForCategory(category: String): String {
    return when (category.lowercase()) {
        "food", "restaurant", "dining" -> CategoryEmojis.FOOD
        "coffee", "cafe" -> CategoryEmojis.COFFEE
        "groceries", "supermarket" -> CategoryEmojis.GROCERIES
        "transport", "uber", "lyft", "taxi" -> CategoryEmojis.TRANSPORT
        "gas", "fuel", "petrol" -> CategoryEmojis.GAS
        "entertainment", "movies", "streaming" -> CategoryEmojis.ENTERTAINMENT
        "shopping", "retail", "amazon" -> CategoryEmojis.SHOPPING
        "bills", "utilities" -> CategoryEmojis.BILLS
        "health", "pharmacy", "medical" -> CategoryEmojis.HEALTH
        "income", "salary", "paycheck" -> CategoryEmojis.INCOME
        "transfer", "payment" -> CategoryEmojis.TRANSFER
        "subscription" -> CategoryEmojis.SUBSCRIPTION
        else -> CategoryEmojis.UNKNOWN
    }
}
