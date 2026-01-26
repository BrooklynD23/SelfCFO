package com.ledgerlens.ui.components.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.theme.LedgerLensTheme
import com.ledgerlens.ui.theme.ShapePatterns

/**
 * Receipt-style card with optional jagged bottom edge.
 * Used for bill splitting to mimic paper receipt appearance.
 */
@Composable
fun ReceiptStyleCard(
    modifier: Modifier = Modifier,
    showJaggedEdge: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LedgerLensTheme.colors

    Column(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = if (showJaggedEdge) {
                ShapePatterns.receiptCard.copy(
                    bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp),
                    bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp)
                )
            } else {
                ShapePatterns.receiptCard
            },
            color = colors.surface,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }

        if (showJaggedEdge) {
            JaggedEdge(
                modifier = Modifier.fillMaxWidth(),
                color = colors.surface
            )
        }
    }
}

/**
 * Jagged/torn paper edge effect for receipt cards.
 */
@Composable
fun JaggedEdge(
    modifier: Modifier = Modifier,
    color: Color = LedgerLensTheme.colors.surface,
    height: Float = 12f,
    teethCount: Int = 20
) {
    Canvas(
        modifier = modifier.height(height.dp)
    ) {
        drawJaggedEdge(
            width = size.width,
            height = size.height,
            color = color,
            teethCount = teethCount
        )
    }
}

private fun DrawScope.drawJaggedEdge(
    width: Float,
    height: Float,
    color: Color,
    teethCount: Int
) {
    val toothWidth = width / teethCount

    val path = Path().apply {
        moveTo(0f, 0f)

        for (i in 0 until teethCount) {
            val startX = i * toothWidth
            val midX = startX + toothWidth / 2
            val endX = startX + toothWidth

            lineTo(midX, height)
            lineTo(endX, 0f)
        }

        lineTo(width, height)
        lineTo(0f, height)
        close()
    }

    drawPath(
        path = path,
        color = color
    )
}

/**
 * Receipt header section with restaurant/store info.
 */
@Composable
fun ReceiptHeader(
    storeName: String,
    date: String,
    time: String? = null,
    address: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        androidx.compose.material3.Text(
            text = storeName,
            style = typography.titleMedium,
            color = colors.onSurface,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )

        address?.let {
            Spacer(modifier = Modifier.height(4.dp))
            androidx.compose.material3.Text(
                text = it,
                style = typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        val dateTimeText = if (time != null) "$date  $time" else date
        androidx.compose.material3.Text(
            text = dateTimeText,
            style = typography.labelMedium,
            color = colors.onSurfaceVariant
        )
    }
}

/**
 * Dashed divider line for receipt sections.
 */
@Composable
fun ReceiptDivider(
    modifier: Modifier = Modifier
) {
    val color = LedgerLensTheme.colors.outline

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
    ) {
        val dashWidth = 4.dp.toPx()
        val gapWidth = 4.dp.toPx()
        var x = 0f

        while (x < size.width) {
            drawLine(
                color = color,
                start = Offset(x, size.height / 2),
                end = Offset((x + dashWidth).coerceAtMost(size.width), size.height / 2),
                strokeWidth = 1.dp.toPx()
            )
            x += dashWidth + gapWidth
        }
    }
}

/**
 * Extension to copy a shape with modified corners.
 */
private fun androidx.compose.foundation.shape.RoundedCornerShape.copy(
    topStart: androidx.compose.foundation.shape.CornerSize = this.topStart,
    topEnd: androidx.compose.foundation.shape.CornerSize = this.topEnd,
    bottomEnd: androidx.compose.foundation.shape.CornerSize = this.bottomEnd,
    bottomStart: androidx.compose.foundation.shape.CornerSize = this.bottomStart
): androidx.compose.foundation.shape.RoundedCornerShape {
    return androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = topStart,
        topEnd = topEnd,
        bottomEnd = bottomEnd,
        bottomStart = bottomStart
    )
}
