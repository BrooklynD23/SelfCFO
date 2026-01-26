package com.ledgerlens.ui.screens.receipts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledgerlens.domain.Money
import com.ledgerlens.receipts.Participant
import com.ledgerlens.receipts.ReceiptItem
import com.ledgerlens.receipts.SplitType
import com.ledgerlens.ui.viewmodels.receipts.ReceiptsViewModel
import com.ledgerlens.ui.viewmodels.receipts.SplitReceiptUiState

/**
 * Bottom sheet for splitting a receipt among participants.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitReceiptSheet(viewModel: ReceiptsViewModel, onDismiss: () -> Unit) {
    val state by viewModel.splitState.collectAsState()
    var showAddParticipantDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Split Receipt",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Participants section
            ParticipantsSection(
                participants = state.participants,
                onAddParticipant = { showAddParticipantDialog = true },
                onRemoveParticipant = viewModel::removeParticipant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Split type selector
            SplitTypeSelector(
                selectedType = state.splitType,
                onTypeSelected = viewModel::setSplitType
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Content based on split type
            when (state.splitType) {
                SplitType.BY_ITEM -> {
                    ItemAssignmentList(
                        state = state,
                        onToggleAssignment = viewModel::toggleItemAssignment
                    )
                }
                SplitType.EQUAL -> {
                    EqualSplitInfo(state = state)
                }
                SplitType.CUSTOM -> {
                    CustomAmountsList(
                        state = state,
                        onAmountChange = viewModel::setCustomAmount
                    )
                }
                SplitType.PERCENTAGE -> {
                    PercentageSplitList(
                        state = state,
                        onPercentageChange = viewModel::setCustomAmount
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Split summary
            SplitSummary(state = state)

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = viewModel::confirmSplit,
                    modifier = Modifier.weight(1f),
                    enabled = state.canSplit && state.previewResult?.isBalanced == true
                ) {
                    Text("Confirm Split")
                }
            }
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
private fun ParticipantsSection(
    participants: List<Participant>,
    onAddParticipant: () -> Unit,
    onRemoveParticipant: (String) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Participants (${participants.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )

            TextButton(onClick = onAddParticipant) {
                Icon(
                    Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (participants.isEmpty()) {
            // TODO: Replace with LedgerLensCard
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.GroupAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add at least 2 participants to split",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(participants, key = { it.id }) { participant ->
                    ParticipantChip(
                        participant = participant,
                        onRemove = { onRemoveParticipant(participant.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ParticipantChip(participant: Participant, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = parseColor(participant.color).copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, parseColor(participant.color).copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(parseColor(participant.color)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = participant.initials,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = participant.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!participant.isSelf) {
                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove ${participant.name}",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SplitTypeSelector(selectedType: SplitType, onTypeSelected: (SplitType) -> Unit) {
    Column {
        Text(
            text = "Split Method",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SplitTypeChip(
                label = "By Item",
                icon = Icons.Default.FormatListBulleted,
                selected = selectedType == SplitType.BY_ITEM,
                onClick = { onTypeSelected(SplitType.BY_ITEM) },
                modifier = Modifier.weight(1f)
            )

            SplitTypeChip(
                label = "Equal",
                icon = Icons.Default.Balance,
                selected = selectedType == SplitType.EQUAL,
                onClick = { onTypeSelected(SplitType.EQUAL) },
                modifier = Modifier.weight(1f)
            )

            SplitTypeChip(
                label = "Custom",
                icon = Icons.Default.Edit,
                selected = selectedType == SplitType.CUSTOM,
                onClick = { onTypeSelected(SplitType.CUSTOM) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SplitTypeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ItemAssignmentList(state: SplitReceiptUiState, onToggleAssignment: (Int, String) -> Unit) {
    val receipt = state.receipt ?: return
    val items = receipt.productItems

    if (items.isEmpty()) {
        Text(
            text = "No items to split",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Column {
        Text(
            text = "Assign items to participants",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(items) { index, item ->
                ItemAssignmentRow(
                    item = item,
                    itemIndex = index,
                    participants = state.participants,
                    assignedParticipants = state.itemAssignments[index] ?: emptySet(),
                    onToggle = { participantId -> onToggleAssignment(index, participantId) }
                )
            }
        }
    }
}

@Composable
private fun ItemAssignmentRow(
    item: ReceiptItem,
    itemIndex: Int,
    participants: List<Participant>,
    assignedParticipants: Set<String>,
    onToggle: (String) -> Unit
) {
    // TODO: Replace with LedgerLensCard
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // TODO: Replace with MoneyText
                Text(
                    text = formatMoney(item.totalPrice),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (participants.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    participants.forEach { participant ->
                        val isAssigned = participant.id in assignedParticipants

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isAssigned) {
                                        parseColor(participant.color)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                                .border(
                                    width = 2.dp,
                                    color = if (isAssigned) {
                                        parseColor(participant.color)
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    },
                                    shape = CircleShape
                                )
                                .clickable { onToggle(participant.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = participant.initials,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isAssigned) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EqualSplitInfo(state: SplitReceiptUiState) {
    val receipt = state.receipt ?: return
    val total = receipt.totalAmount?.minorUnits ?: receipt.calculatedTotal.minorUnits
    val participantCount = state.participants.size

    if (participantCount == 0) {
        Text(
            text = "Add participants to see the split",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val perPerson = total / participantCount
    val remainder = total % participantCount

    // TODO: Replace with LedgerLensCard
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Each person pays",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // TODO: Replace with MoneyText
            Text(
                text = "$${perPerson / 100}.${(perPerson % 100).toString().padStart(2, '0')}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (remainder > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Remainder: $remainder¢ (assigned to first $remainder participants)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CustomAmountsList(state: SplitReceiptUiState, onAmountChange: (String, Long) -> Unit) {
    if (state.participants.isEmpty()) {
        Text(
            text = "Add participants to assign custom amounts",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Column {
        Text(
            text = "Enter custom amounts",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        state.participants.forEach { participant ->
            var amountText by remember(participant.id) {
                mutableStateOf(
                    state.customAmounts[participant.id]?.let {
                        "%.2f".format(it / 100.0)
                    } ?: ""
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Participant avatar
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(parseColor(participant.color)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = participant.initials,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = participant.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { newValue ->
                        // Only allow valid decimal input
                        if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            amountText = newValue
                            val cents = ((newValue.toDoubleOrNull() ?: 0.0) * 100).toLong()
                            onAmountChange(participant.id, cents)
                        }
                    },
                    modifier = Modifier.width(120.dp),
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun PercentageSplitList(state: SplitReceiptUiState, onPercentageChange: (String, Long) -> Unit) {
    val receipt = state.receipt ?: return
    val total = receipt.totalAmount?.minorUnits ?: receipt.calculatedTotal.minorUnits

    if (state.participants.isEmpty()) {
        Text(
            text = "Add participants to set percentages",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Column {
        Text(
            text = "Set percentages",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        state.participants.forEach { participant ->
            val currentAmount = state.customAmounts[participant.id] ?: 0L
            val percentage = if (total > 0) (currentAmount * 100.0 / total).toInt() else 0

            var sliderValue by remember(participant.id) { mutableStateOf(percentage.toFloat()) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(parseColor(participant.color)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = participant.initials,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = participant.name,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Text(
                        text = "${sliderValue.toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Slider(
                    value = sliderValue,
                    onValueChange = { newValue ->
                        sliderValue = newValue
                        val newAmount = (total * newValue / 100).toLong()
                        onPercentageChange(participant.id, newAmount)
                    },
                    valueRange = 0f..100f,
                    steps = 19, // 5% increments
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SplitSummary(state: SplitReceiptUiState) {
    val receipt = state.receipt ?: return
    val total = receipt.totalAmount?.minorUnits ?: receipt.calculatedTotal.minorUnits
    val allocated = state.participants.sumOf { state.getParticipantTotal(it.id) }
    val remaining = total - allocated

    // TODO: Replace with LedgerLensCard
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (remaining == 0L) {
                Color(0xFF4CAF50).copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$${total / 100}.${(total % 100).toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Allocated",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$${allocated / 100}.${(allocated % 100).toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (remaining == 0L) "Balanced ✓" else "Remaining",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (remaining == 0L) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                )

                if (remaining != 0L) {
                    Text(
                        text = "$${kotlin.math.abs(remaining) / 100}.${(kotlin.math.abs(remaining) % 100).toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
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

private fun formatMoney(money: com.ledgerlens.domain.Money): String {
    val dollars = money.minorUnits / 100
    val cents = (money.minorUnits % 100).toString().padStart(2, '0')
    return "$$dollars.$cents"
}
