package com.ledgerlens.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.zIndex
import com.ledgerlens.ui.theme.LedgerLensTheme

/**
 * Data for a participant in an avatar group.
 */
data class AvatarData(
    val name: String,
    val color: Color? = null
)

/**
 * Group of overlapping avatars for displaying multiple participants.
 * Used when a bill item is split between multiple people.
 */
@Composable
fun AvatarGroup(
    participants: List<AvatarData>,
    modifier: Modifier = Modifier,
    size: AvatarSize = AvatarSize.SMALL,
    maxVisible: Int = 3,
    overlapFraction: Float = 0.3f
) {
    val colors = LedgerLensTheme.colors

    if (participants.isEmpty()) return

    val visibleParticipants = participants.take(maxVisible)
    val remainingCount = participants.size - maxVisible
    val overlap = size.size * overlapFraction

    Box(modifier = modifier) {
        visibleParticipants.forEachIndexed { index, participant ->
            Avatar(
                name = participant.name,
                color = participant.color,
                size = size,
                showBorder = true,
                modifier = Modifier
                    .offset(x = overlap * index)
                    .zIndex((visibleParticipants.size - index).toFloat())
            )
        }

        if (remainingCount > 0) {
            // "+N" badge for remaining participants
            Box(
                modifier = Modifier
                    .offset(x = overlap * visibleParticipants.size)
                    .size(size.size)
                    .clip(CircleShape)
                    .background(colors.surfaceVariant)
                    .border(2.dp, colors.surface, CircleShape)
                    .zIndex(0f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$remainingCount",
                    color = colors.onSurfaceVariant,
                    fontSize = (size.fontSize - 2).sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Calculate the total width of an avatar group.
 */
fun calculateAvatarGroupWidth(
    participantCount: Int,
    size: AvatarSize,
    maxVisible: Int = 3,
    overlapFraction: Float = 0.3f
): Dp {
    val visibleCount = participantCount.coerceAtMost(maxVisible)
    val hasRemaining = participantCount > maxVisible
    val totalSlots = visibleCount + if (hasRemaining) 1 else 0

    if (totalSlots == 0) return 0.dp
    if (totalSlots == 1) return size.size

    val overlap = size.size * overlapFraction
    return size.size + (overlap * (totalSlots - 1))
}

/**
 * Stacked avatar group (vertical instead of horizontal).
 */
@Composable
fun VerticalAvatarGroup(
    participants: List<AvatarData>,
    modifier: Modifier = Modifier,
    size: AvatarSize = AvatarSize.SMALL,
    maxVisible: Int = 3,
    overlapFraction: Float = 0.3f
) {
    val colors = LedgerLensTheme.colors

    if (participants.isEmpty()) return

    val visibleParticipants = participants.take(maxVisible)
    val remainingCount = participants.size - maxVisible
    val overlap = size.size * overlapFraction

    Box(modifier = modifier) {
        visibleParticipants.forEachIndexed { index, participant ->
            Avatar(
                name = participant.name,
                color = participant.color,
                size = size,
                showBorder = true,
                modifier = Modifier
                    .offset(y = overlap * index)
                    .zIndex((visibleParticipants.size - index).toFloat())
            )
        }

        if (remainingCount > 0) {
            Box(
                modifier = Modifier
                    .offset(y = overlap * visibleParticipants.size)
                    .size(size.size)
                    .clip(CircleShape)
                    .background(colors.surfaceVariant)
                    .border(2.dp, colors.surface, CircleShape)
                    .zIndex(0f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$remainingCount",
                    color = colors.onSurfaceVariant,
                    fontSize = (size.fontSize - 2).sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
