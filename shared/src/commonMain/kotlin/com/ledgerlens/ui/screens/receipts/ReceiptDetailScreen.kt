package com.ledgerlens.ui.screens.receipts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledgerlens.domain.Money
import com.ledgerlens.receipts.ExtractedReceipt
import com.ledgerlens.receipts.Participant
import com.ledgerlens.receipts.ReceiptItem
import com.ledgerlens.receipts.ReceiptItemType
import com.ledgerlens.receipts.SplitParticipant
import com.ledgerlens.receipts.SplitResult
import com.ledgerlens.ui.viewmodels.receipts.LinkedTransactionInfo
import com.ledgerlens.ui.viewmodels.receipts.ReceiptDetailUiState
import com.ledgerlens.ui.viewmodels.receipts.ReceiptsViewModel

/**
 * Receipt detail screen showing image, OCR items, and split functionality.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptDetailScreen(
    viewModel: ReceiptsViewModel,
    receiptId: String,
    onNavigateBack: () -> Unit,
    onLinkTransaction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.detailState.collectAsState()
    
    LaunchedEffect(receiptId) {
        viewModel.loadReceiptDetail(receiptId)
    }
    
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.receipt?.merchant ?: "Receipt Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: Share receipt */ }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { /* TODO: Edit receipt */ }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                ErrorContent(
                    error = state.error!!,
                    onRetry = { viewModel.loadReceiptDetail(receiptId) },
                    onDismiss = viewModel::clearError,
                    modifier = Modifier.padding(padding)
                )
            }
            state.receipt != null -> {
                ReceiptDetailContent(
                    state = state,
                    onLinkTransaction = onLinkTransaction,
                    onUnlinkTransaction = viewModel::unlinkTransaction,
                    onShowSplitSheet = viewModel::showSplitSheet,
                    modifier = Modifier.padding(padding)
                )
            }
            else -> {
                // Empty state - receipt not found
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Receipt,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Receipt not found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // Split bottom sheet
        if (state.isSplitSheetVisible) {
            SplitReceiptSheet(
                viewModel = viewModel,
                onDismiss = viewModel::hideSplitSheet
            )
        }
    }
}

@Composable
private fun ReceiptDetailContent(
    state: ReceiptDetailUiState,
    onLinkTransaction: () -> Unit,
    onUnlinkTransaction: () -> Unit,
    onShowSplitSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val receipt = state.receipt ?: return
    
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Receipt image
        item {
            ReceiptImageSection(imagePath = state.imagePath)
        }
        
        // Receipt summary
        item {
            ReceiptSummaryCard(receipt = receipt)
        }
        
        // Linked transaction
        item {
            LinkedTransactionCard(
                linkedTransaction = state.linkedTransaction,
                onLink = onLinkTransaction,
                onUnlink = onUnlinkTransaction
            )
        }
        
        // Split result (if exists)
        state.splitResult?.let { splitResult ->
            item {
                SplitResultCard(
                    splitResult = splitResult,
                    onEditSplit = onShowSplitSheet
                )
            }
        }
        
        // Items section header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Items (${receipt.productItems.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                if (state.splitResult == null && state.participants.isNotEmpty()) {
                    TextButton(onClick = onShowSplitSheet) {
                        Icon(
                            Icons.Default.CallSplit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Split")
                    }
                }
            }
        }
        
        // Items list
        itemsIndexed(receipt.items) { index, item ->
            ReceiptItemCard(
                item = item,
                index = index
            )
        }
        
        // Totals section
        item {
            TotalsSection(receipt = receipt)
        }
        
        // Confidence warning
        if (receipt.confidence < 0.8) {
            item {
                ConfidenceWarning(confidence = receipt.confidence)
            }
        }
        
        // Bottom spacer for FAB
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun ReceiptImageSection(imagePath: String?) {
    // TODO: Replace with LedgerLensCard
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (imagePath != null) {
                // TODO: Load actual image
                Text("Receipt Image", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No image available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiptSummaryCard(receipt: ExtractedReceipt) {
    // TODO: Replace with LedgerLensCard
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = receipt.merchant ?: "Unknown Merchant",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                receipt.date?.let { date ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = date,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                receipt.time?.let { time ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = time,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            receipt.paymentMethod?.let { method ->
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Payment,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = method,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkedTransactionCard(
    linkedTransaction: LinkedTransactionInfo?,
    onLink: () -> Unit,
    onUnlink: () -> Unit
) {
    // TODO: Replace with LedgerLensCard
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (linkedTransaction != null) 
                // TODO: Replace with LedgerLensTheme.colors.successContainer
                Color(0xFF4CAF50).copy(alpha = 0.1f)
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    if (linkedTransaction != null) Icons.Default.Link else Icons.Default.LinkOff,
                    contentDescription = null,
                    tint = if (linkedTransaction != null) 
                        // TODO: Replace with LedgerLensTheme.colors.success
                        Color(0xFF4CAF50)
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = if (linkedTransaction != null) "Linked Transaction" else "Not Linked",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    
                    if (linkedTransaction != null) {
                        Text(
                            text = linkedTransaction.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = "Link to a bank transaction",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            if (linkedTransaction != null) {
                IconButton(onClick = onUnlink) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Unlink",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                FilledTonalButton(onClick = onLink) {
                    Text("Link")
                }
            }
        }
    }
}

@Composable
private fun SplitResultCard(
    splitResult: SplitResult,
    onEditSplit: () -> Unit
) {
    // TODO: Replace with LedgerLensCard
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CallSplit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Split Summary",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                TextButton(onClick = onEditSplit) {
                    Text("Edit")
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            splitResult.participants.forEach { participant ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Participant avatar
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    // TODO: Use participant color
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = participant.displayName.take(1).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = participant.displayName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // TODO: Replace with MoneyText
                        Text(
                            text = "$${participant.allocatedAmount / 100}.${(participant.allocatedAmount % 100).toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // Status indicator
                        val statusIcon = when {
                            participant.isSettled -> Icons.Default.CheckCircle
                            participant.status == com.ledgerlens.receipts.SettlementStatus.PARTIAL -> Icons.Default.HourglassTop
                            else -> Icons.Default.Schedule
                        }
                        val statusColor = when {
                            participant.isSettled -> Color(0xFF4CAF50)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        
                        Icon(
                            statusIcon,
                            contentDescription = participant.status.name,
                            modifier = Modifier.size(18.dp),
                            tint = statusColor
                        )
                    }
                }
            }
            
            if (!splitResult.isBalanced) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Remainder: ${splitResult.remainder}¢",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ReceiptItemCard(
    item: ReceiptItem,
    index: Int
) {
    val backgroundColor = when (item.type) {
        ReceiptItemType.PRODUCT -> MaterialTheme.colorScheme.surface
        ReceiptItemType.TAX -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ReceiptItemType.TIP -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ReceiptItemType.DISCOUNT -> Color(0xFF4CAF50).copy(alpha = 0.1f)
        ReceiptItemType.FEE -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surface
    }
    
    // TODO: Replace with LedgerLensCard
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (item.type != ReceiptItemType.PRODUCT) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (item.type == ReceiptItemType.DISCOUNT) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Discount,
                            contentDescription = "Discount",
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF4CAF50)
                        )
                    }
                }
                
                if (item.quantity > 1) {
                    Text(
                        text = "Qty: ${item.quantity.toInt()} × ${formatMoney(item.effectiveUnitPrice)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                item.category?.let { category ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // TODO: Replace with MoneyText
            Text(
                text = if (item.type == ReceiptItemType.DISCOUNT) "-${formatMoney(item.totalPrice)}" else formatMoney(item.totalPrice),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (item.type == ReceiptItemType.DISCOUNT) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TotalsSection(receipt: ExtractedReceipt) {
    // TODO: Replace with LedgerLensCard
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            TotalRow(label = "Subtotal", amount = receipt.subtotal ?: receipt.calculatedSubtotal)
            
            if ((receipt.taxAmount?.minorUnits ?: 0) > 0 || receipt.calculatedTax.minorUnits > 0) {
                TotalRow(label = "Tax", amount = receipt.taxAmount ?: receipt.calculatedTax)
            }
            
            if ((receipt.tipAmount?.minorUnits ?: 0) > 0 || receipt.calculatedTip.minorUnits > 0) {
                TotalRow(label = "Tip", amount = receipt.tipAmount ?: receipt.calculatedTip)
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatMoney(receipt.totalAmount ?: receipt.calculatedTotal),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun TotalRow(label: String, amount: Money) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatMoney(amount),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ConfidenceWarning(confidence: Double) {
    // TODO: Replace with LedgerLensCard
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            // TODO: Replace with LedgerLensTheme.colors.warningContainer
            containerColor = Color(0xFFFF9800).copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                // TODO: Replace with LedgerLensTheme.colors.warning
                tint = Color(0xFFFF9800)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = "Low Confidence (${(confidence * 100).toInt()}%)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Some items may need manual review",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss) {
                Text("Dismiss")
            }
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

private fun formatMoney(money: Money): String {
    val dollars = money.minorUnits / 100
    val cents = (money.minorUnits % 100).toString().padStart(2, '0')
    return "$${dollars}.${cents}"
}
