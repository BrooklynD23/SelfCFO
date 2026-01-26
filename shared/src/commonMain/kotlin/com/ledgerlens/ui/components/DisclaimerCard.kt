package com.ledgerlens.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.theme.LedgerLensTheme
import com.ledgerlens.ui.theme.ShapePatterns

/**
 * Standard disclaimer text for Financial Resources.
 */
object DisclaimerText {
    const val SHORT = "Educational information only. Not financial advice. Consult a qualified advisor."

    const val FULL = "LedgerLens provides educational information only. This is not financial advice. " +
        "The information presented here is for informational purposes and should not be construed " +
        "as personalized investment advice or a recommendation to buy or sell any security. " +
        "Past performance does not guarantee future results. Consult a qualified financial advisor " +
        "before making investment decisions."

    const val ACKNOWLEDGMENT = "I understand this is educational information, not financial advice"
}

/**
 * Compact disclaimer footer for resource screens.
 */
@Composable
fun DisclaimerFooter(
    modifier: Modifier = Modifier,
    text: String = DisclaimerText.SHORT,
    onLearnMore: (() -> Unit)? = null
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surfaceVariant.copy(alpha = 0.5f),
        shape = ShapePatterns.card
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            onLearnMore?.let {
                TextButton(onClick = it) {
                    Text(
                        text = "Learn more",
                        style = typography.labelSmall,
                        color = colors.primary
                    )
                }
            }
        }
    }
}

/**
 * Full disclaimer card with acknowledgment checkbox.
 * Used for first-time access to Financial Resources.
 */
@Composable
fun DisclaimerCard(
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    var isChecked by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ShapePatterns.card,
        color = colors.surface,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = colors.warning,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Important Notice",
                    style = typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Disclaimer text
            Text(
                text = DisclaimerText.FULL,
                style = typography.bodyMedium,
                color = colors.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Checkbox
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { isChecked = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = DisclaimerText.ACKNOWLEDGMENT,
                    style = typography.bodyMedium,
                    color = colors.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Continue button
            Button(
                onClick = onAcknowledge,
                enabled = isChecked,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                    disabledContainerColor = colors.surfaceVariant,
                    disabledContentColor = colors.onSurfaceVariant
                ),
                shape = ShapePatterns.button
            ) {
                Text(
                    text = "Continue",
                    style = typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Inline disclaimer for contextual warnings.
 */
@Composable
fun InlineDisclaimer(
    text: String,
    modifier: Modifier = Modifier
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = colors.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = typography.bodySmall,
            color = colors.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}
