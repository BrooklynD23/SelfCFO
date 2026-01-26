package com.ledgerlens.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.theme.LedgerLensTheme
import com.ledgerlens.ui.theme.ShapePatterns

/**
 * Color-coded participant badge for bill splitting.
 * Shows participant name/initials with their assigned color.
 */
@Composable
fun ParticipantBadge(
    name: String,
    color: Color,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    showRemoveButton: Boolean = false,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    val backgroundColor = if (isSelected) color.copy(alpha = 0.2f) else colors.surface
    val borderColor = if (isSelected) color else colors.outline

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            ),
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Avatar indicator
            Avatar(
                name = name,
                color = color,
                size = AvatarSize.SMALL
            )

            // Name
            Text(
                text = name,
                style = typography.labelMedium,
                color = if (isSelected) color else colors.onSurface,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            )

            // Selected indicator or remove button
            if (isSelected && !showRemoveButton) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (showRemoveButton && onRemove != null) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(onClick = onRemove)
                )
            }
        }
    }
}

/**
 * Compact participant chip showing just initials.
 */
@Composable
fun ParticipantChip(
    name: String,
    color: Color,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    val backgroundColor = if (isSelected) color else colors.surfaceVariant
    val contentColor = if (isSelected) Color.White else colors.onSurfaceVariant

    Surface(
        modifier = modifier
            .clip(ShapePatterns.chipSmall)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            ),
        shape = ShapePatterns.chipSmall,
        color = backgroundColor
    ) {
        Text(
            text = getInitials(name),
            style = typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * Participant selector dock for bill splitting.
 * Shows all participants with selection state.
 */
@Composable
fun ParticipantSelectorRow(
    participants: List<Pair<String, Color>>,
    selectedParticipants: Set<String>,
    onParticipantToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        participants.forEach { (name, color) ->
            ParticipantBadge(
                name = name,
                color = color,
                isSelected = name in selectedParticipants,
                onClick = { onParticipantToggle(name) }
            )
        }
    }
}

/**
 * Assignment indicator for receipt items.
 */
sealed class AssignmentState {
    object Unassigned : AssignmentState()
    data class AssignedToOne(val participant: AvatarData) : AssignmentState()
    data class SplitMultiple(val participants: List<AvatarData>) : AssignmentState()
}

/**
 * Visual indicator showing item assignment state.
 */
@Composable
fun AssignmentIndicator(
    state: AssignmentState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    when (state) {
        is AssignmentState.Unassigned -> {
            EmptyAvatar(
                size = AvatarSize.MEDIUM,
                modifier = modifier.then(
                    if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
                )
            )
        }
        is AssignmentState.AssignedToOne -> {
            Avatar(
                name = state.participant.name,
                color = state.participant.color,
                size = AvatarSize.MEDIUM,
                modifier = modifier.then(
                    if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
                )
            )
        }
        is AssignmentState.SplitMultiple -> {
            AvatarGroup(
                participants = state.participants,
                size = AvatarSize.SMALL,
                modifier = modifier.then(
                    if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
                )
            )
        }
    }
}
