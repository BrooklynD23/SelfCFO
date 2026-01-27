package com.ledgerlens.ui.screens.receipts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledgerlens.receipts.Participant
import com.ledgerlens.receipts.ReceiptItem
import com.ledgerlens.ui.theme.LedgerLensColors
import com.ledgerlens.ui.theme.LedgerLensTheme
import com.ledgerlens.ui.theme.ShapePatterns
import com.ledgerlens.ui.viewmodels.receipts.ReceiptsViewModel
import com.ledgerlens.ui.viewmodels.receipts.SplitReceiptUiState

/**
 * Item assignment state for visual differentiation.
 */
sealed class ItemAssignmentState {
    data object Unassigned : ItemAssignmentState()
    data class AssignedToOne(val participant: Participant) : ItemAssignmentState()
    data class SplitMultiple(val participants: List<Participant>) : ItemAssignmentState()
}

/**
 * StitchUI-styled bottom sheet for splitting a receipt among participants.
 * Features receipt-paper aesthetic with jagged bottom edge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitReceiptSheet(viewModel: ReceiptsViewModel, onDismiss: () -> Unit) {
    val state by viewModel.splitState.collectAsState()
    var showAddParticipantDialog by remember { mutableStateOf(false) }
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceVariant,
        dragHandle = null
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Main scrollable content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 200.dp) // Space for fixed bottom dock
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                BillSplitHeader(onDismiss = onDismiss)

                Spacer(modifier = Modifier.height(16.dp))

                // Summary Card
                BillSummaryCard(
                    state = state,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Receipt Paper Card
                ReceiptPaperCard(
                    state = state,
                    onItemClick = { index ->
                        // Toggle assignment for selected participants
                        state.participants.filter { it.isSelf || state.selectedParticipantIds.contains(it.id) }
                            .forEach { participant ->
                                viewModel.toggleItemAssignment(index, participant.id)
                            }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Fixed bottom dock
            ParticipantDock(
                state = state,
                onAddParticipant = { showAddParticipantDialog = true },
                onParticipantSelect = viewModel::toggleParticipantSelection,
                onSplitRemaining = viewModel::splitRemainingEqually,
                onDone = {
                    viewModel.confirmSplit()
                    onDismiss()
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // Add participant dialog
    if (showAddParticipantDialog) {
        AddParticipantDialog(
            existingParticipants = state.participants,
            onDismiss = { showAddParticipantDialog = false },
            onAdd = { participant ->
                viewModel.addParticipant(participant)
                showAddParticipantDialog = false
            }
        )
    }
}

@Composable
private fun BillSplitHeader(onDismiss: () -> Unit) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = colors.onSurface
            )
        }

        Text(
            text = "Bill Split",
            style = typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface
        )

        // Spacer for balance
        Spacer(modifier = Modifier.size(48.dp))
    }
}

@Composable
private fun BillSummaryCard(
    state: SplitReceiptUiState,
    modifier: Modifier = Modifier
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography
    val receipt = state.receipt

    val totalAmount = receipt?.totalAmount?.minorUnits ?: receipt?.calculatedTotal?.minorUnits ?: 0L
    val taxAmount = receipt?.taxAmount?.minorUnits ?: 0L
    val tipAmount = state.tipAmount

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ShapePatterns.insightCard,
        color = colors.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "TOTAL BILL",
                    style = typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = formatCurrency(totalAmount),
                    style = typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Tax chip
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = colors.surfaceVariant
                    ) {
                        Text(
                            text = "Tax: ${formatCurrency(taxAmount)}",
                            style = typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Tip chip
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = colors.primaryContainer.copy(alpha = 0.3f)
                    ) {
                        Text(
                            text = "Tip: ${formatCurrency(tipAmount)}",
                            style = typography.labelSmall,
                            color = colors.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Receipt icon
            Surface(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(12.dp),
                color = colors.primaryContainer.copy(alpha = 0.2f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Receipt,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiptPaperCard(
    state: SplitReceiptUiState,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography
    val receipt = state.receipt

    Column(modifier = modifier.fillMaxWidth()) {
        // Receipt paper with rounded top
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Column {
                // Receipt header
                ReceiptHeader(
                    merchantName = receipt?.merchant ?: "Restaurant",
                    date = receipt?.date ?: "Today",
                    time = receipt?.time ?: ""
                )

                Divider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = colors.outlineVariant,
                    thickness = 1.dp
                )

                // Items list
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    receipt?.productItems?.forEachIndexed { index, item ->
                        val assignedParticipants = state.itemAssignments[index] ?: emptySet()
                        val assignmentState = when {
                            assignedParticipants.isEmpty() -> ItemAssignmentState.Unassigned
                            assignedParticipants.size == 1 -> {
                                val participant = state.participants.find { it.id == assignedParticipants.first() }
                                if (participant != null) ItemAssignmentState.AssignedToOne(participant)
                                else ItemAssignmentState.Unassigned
                            }
                            else -> {
                                val participants = state.participants.filter { it.id in assignedParticipants }
                                ItemAssignmentState.SplitMultiple(participants)
                            }
                        }

                        ReceiptItemRow(
                            item = item,
                            assignmentState = assignmentState,
                            onClick = { onItemClick(index) }
                        )
                    }
                }

                // Subtotal footer
                Divider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = colors.outlineVariant,
                    thickness = 1.dp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SUBTOTAL",
                        style = typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = formatCurrency(receipt?.subtotal?.minorUnits ?: 0L),
                        style = typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }

        // Jagged bottom edge
        JaggedEdge(
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ReceiptHeader(
    merchantName: String,
    date: String,
    time: String
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Text(
                text = merchantName,
                style = typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111418)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = buildString {
                    append(date)
                    if (time.isNotEmpty()) {
                        append(" • ")
                        append(time)
                    }
                },
                style = typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }

        Surface(
            shape = CircleShape,
            color = colors.surfaceVariant
        ) {
            Icon(
                Icons.Outlined.Receipt,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .padding(8.dp)
                    .size(20.dp)
            )
        }
    }
}

@Composable
private fun ReceiptItemRow(
    item: ReceiptItem,
    assignmentState: ItemAssignmentState,
    onClick: () -> Unit
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    val isAssigned = assignmentState !is ItemAssignmentState.Unassigned
    val backgroundColor = if (isAssigned) colors.primaryContainer.copy(alpha = 0.15f) else Color.Transparent
    val borderColor = if (isAssigned) colors.primaryContainer else Color.Transparent

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = if (isAssigned) {
            androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Assignment indicator
                AssignmentIndicator(assignmentState)

                Column {
                    Text(
                        text = "${item.quantity}x ${item.name}",
                        style = typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF111418),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when (assignmentState) {
                            is ItemAssignmentState.Unassigned -> "Unassigned"
                            is ItemAssignmentState.AssignedToOne -> "Assigned to ${assignmentState.participant.initials}"
                            is ItemAssignmentState.SplitMultiple -> "Split ${assignmentState.participants.size} ways"
                        },
                        style = typography.bodySmall,
                        color = if (isAssigned) colors.primary else colors.onSurfaceVariant
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Participant avatars for assigned items
                when (assignmentState) {
                    is ItemAssignmentState.AssignedToOne -> {
                        ParticipantMiniAvatar(assignmentState.participant)
                    }
                    is ItemAssignmentState.SplitMultiple -> {
                        OverlappingAvatars(assignmentState.participants.take(3))
                    }
                    else -> {}
                }

                Text(
                    text = formatCurrency(item.totalPrice.minorUnits),
                    style = typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111418)
                )
            }
        }
    }
}

@Composable
private fun AssignmentIndicator(state: ItemAssignmentState) {
    val colors = LedgerLensTheme.colors
    val isAssigned = state !is ItemAssignmentState.Unassigned

    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (isAssigned) colors.primary else Color.Transparent)
            .border(
                width = 2.dp,
                color = if (isAssigned) colors.primary else colors.outlineVariant,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isAssigned) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Assigned",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun ParticipantMiniAvatar(participant: Participant) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(parseColor(participant.color)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = participant.initials,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun OverlappingAvatars(participants: List<Participant>) {
    Row(
        modifier = Modifier.width((24 + (participants.size - 1) * 16).dp),
        horizontalArrangement = Arrangement.Start
    ) {
        participants.forEachIndexed { index, participant ->
            Box(
                modifier = Modifier
                    .offset(x = (index * -8).dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(parseColor(participant.color))
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = participant.initials,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun JaggedEdge(
    color: Color,
    modifier: Modifier = Modifier
) {
    // Simplified jagged edge using a gradient fade
    Box(
        modifier = modifier
            .height(16.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(color, Color.Transparent)
                )
            )
    )
}

@Composable
private fun ParticipantDock(
    state: SplitReceiptUiState,
    onAddParticipant: () -> Unit,
    onParticipantSelect: (String) -> Unit,
    onSplitRemaining: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colors.surface,
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // "Assign to:" header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Assign to:",
                    style = typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )

                TextButton(
                    onClick = onAddParticipant,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        Icons.Default.GroupAdd,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Add Friend",
                        style = typography.labelMedium,
                        color = colors.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Participant avatars row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                state.participants.forEach { participant ->
                    val isSelected = participant.isSelf || state.selectedParticipantIds.contains(participant.id)

                    ParticipantAvatar(
                        participant = participant,
                        isSelected = isSelected,
                        onClick = { onParticipantSelect(participant.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Split Remaining button
                OutlinedButton(
                    onClick = onSplitRemaining,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = colors.onSurfaceVariant
                    )
                ) {
                    Text(
                        text = "Split Remaining",
                        fontWeight = FontWeight.Bold
                    )
                }

                // Done button
                Button(
                    onClick = onDone,
                    modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    ),
                    enabled = state.canSplit
                ) {
                    Text(
                        text = "Done",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Bottom safe area
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ParticipantAvatar(
    participant: Participant,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LedgerLensTheme.colors
    val typography = LedgerLensTheme.typography
    val participantColor = parseColor(participant.color)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (participant.isSelf && isSelected) colors.primary
                        else participantColor.copy(alpha = if (isSelected) 1f else 0.4f)
                    )
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                width = 4.dp,
                                color = if (participant.isSelf) colors.primaryContainer else participantColor.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (participant.isSelf) "ME" else participant.initials,
                    style = typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Selected checkmark
            if (isSelected) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(18.dp),
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 2.dp
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = colors.primary,
                        modifier = Modifier.padding(1.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (participant.isSelf) "You" else participant.name.take(6),
            style = typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            color = if (isSelected) colors.primary else colors.onSurfaceVariant
        )
    }
}

@Composable
private fun AddParticipantDialog(
    existingParticipants: List<Participant>,
    onDismiss: () -> Unit,
    onAdd: (Participant) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedColorIndex by remember { mutableStateOf(existingParticipants.size % Participant.DEFAULT_COLORS.size) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Participant") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Color",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Participant.DEFAULT_COLORS.take(5).forEachIndexed { index, colorHex ->
                        val color = parseColor(colorHex)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (index == selectedColorIndex) 3.dp else 0.dp,
                                    color = if (index == selectedColorIndex) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColorIndex = index }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Participant.DEFAULT_COLORS.drop(5).forEachIndexed { index, colorHex ->
                        val actualIndex = index + 5
                        val color = parseColor(colorHex)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (actualIndex == selectedColorIndex) 3.dp else 0.dp,
                                    color = if (actualIndex == selectedColorIndex) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColorIndex = actualIndex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onAdd(Participant.quickAdd(name, selectedColorIndex))
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Helper function to parse hex color
private fun parseColor(hexColor: String): Color {
    return try {
        val hex = hexColor.removePrefix("#")
        Color(
            red = hex.substring(0, 2).toInt(16) / 255f,
            green = hex.substring(2, 4).toInt(16) / 255f,
            blue = hex.substring(4, 6).toInt(16) / 255f
        )
    } catch (e: Exception) {
        Color.Gray
    }
}

private fun formatCurrency(minorUnits: Long): String {
    val dollars = minorUnits / 100
    val cents = (minorUnits % 100).toString().padStart(2, '0')
    return "$$dollars.$cents"
}
