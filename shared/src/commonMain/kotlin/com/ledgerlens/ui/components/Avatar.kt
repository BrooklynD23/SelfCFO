package com.ledgerlens.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledgerlens.ui.theme.LedgerLensColors
import com.ledgerlens.ui.theme.LedgerLensTheme

/**
 * Avatar size presets.
 */
enum class AvatarSize(val size: Dp, val fontSize: Int) {
    SMALL(24.dp, 10),
    MEDIUM(32.dp, 12),
    LARGE(40.dp, 16),
    EXTRA_LARGE(56.dp, 20)
}

/**
 * Circular avatar displaying initials with a colored background.
 * Used for participant display in bill splitting.
 */
@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: AvatarSize = AvatarSize.MEDIUM,
    color: Color? = null,
    showBorder: Boolean = false
) {
    val colors = LedgerLensTheme.colors
    val initials = getInitials(name)
    val avatarColor = color ?: getAvatarColorForName(name)

    Box(
        modifier = modifier
            .size(size.size)
            .clip(CircleShape)
            .background(avatarColor)
            .then(
                if (showBorder) {
                    Modifier.border(2.dp, colors.surface, CircleShape)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontSize = size.fontSize.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Avatar with explicit initials instead of name.
 */
@Composable
fun AvatarWithInitials(
    initials: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: AvatarSize = AvatarSize.MEDIUM,
    showBorder: Boolean = false
) {
    val colors = LedgerLensTheme.colors

    Box(
        modifier = modifier
            .size(size.size)
            .clip(CircleShape)
            .background(color)
            .then(
                if (showBorder) {
                    Modifier.border(2.dp, colors.surface, CircleShape)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials.take(2).uppercase(),
            color = Color.White,
            fontSize = size.fontSize.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Empty/unassigned avatar (hollow circle).
 */
@Composable
fun EmptyAvatar(
    modifier: Modifier = Modifier,
    size: AvatarSize = AvatarSize.MEDIUM
) {
    val colors = LedgerLensTheme.colors

    Box(
        modifier = modifier
            .size(size.size)
            .clip(CircleShape)
            .border(2.dp, colors.outline, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Empty - hollow circle
    }
}

/**
 * Extract initials from a name.
 */
fun getInitials(name: String): String {
    val parts = name.trim().split(" ")
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts.first().first()}${parts.last().first()}".uppercase()
    }
}

/**
 * Get a consistent avatar color based on name.
 */
fun getAvatarColorForName(name: String): Color {
    val colors = LedgerLensColors.AvatarColors
    val index = name.hashCode().let { kotlin.math.abs(it) } % colors.size
    return colors[index]
}
