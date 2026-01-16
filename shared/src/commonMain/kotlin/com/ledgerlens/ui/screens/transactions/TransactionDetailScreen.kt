package com.ledgerlens.ui.screens.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.components.LedgerLensCard
import com.ledgerlens.ui.components.LoadingIndicator
import com.ledgerlens.ui.components.MoneyTextLarge
import com.ledgerlens.ui.viewmodels.transactions.TransactionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    transactionId: String,
    viewModel: TransactionsViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val detailState by viewModel.detailState.collectAsState()
    
    LaunchedEffect(transactionId) { viewModel.loadTransactionDetail(transactionId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Details") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    if (!detailState.isEditing && detailState.transaction != null) {
                        IconButton(onClick = viewModel::startEditing) { Icon(Icons.Default.Edit, "Edit") }
                    }
                }
            )
        }
    ) { padding ->
        when {
            detailState.isLoading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { LoadingIndicator() }
            detailState.transaction == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { Text("Transaction not found") }
            else -> {
                val tx = detailState.transaction!!
                var selectedCategory by remember { mutableStateOf(tx.category?.id ?: "") }

                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Amount card
                    LedgerLensCard(modifier = Modifier.fillMaxWidth()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            MoneyTextLarge(money = tx.amount, colored = true)
                            Spacer(Modifier.height(4.dp))
                            Text(tx.displayMerchant, style = MaterialTheme.typography.titleMedium)
                            Text(tx.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Details card
                    LedgerLensCard(modifier = Modifier.fillMaxWidth()) {
                        DetailRow("Original Name", tx.merchantName)
                        DetailRow("Description", tx.description)
                        tx.accountName?.let { DetailRow("Account", it) }
                        tx.categoryConfidence?.let { DetailRow("Confidence", "${(it * 100).toInt()}%") }
                    }

                    // Category section
                    LedgerLensCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Category", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        
                        if (detailState.isEditing) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(detailState.availableCategories) { cat ->
                                    FilterChip(
                                        selected = cat.id == selectedCategory,
                                        onClick = { selectedCategory = cat.id },
                                        label = { Text(cat.name) }
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = viewModel::cancelEditing, modifier = Modifier.weight(1f)) { Text("Cancel") }
                                Button(
                                    onClick = { viewModel.updateTransactionCategory(selectedCategory) },
                                    modifier = Modifier.weight(1f),
                                    enabled = !detailState.isSaving
                                ) { Text(if (detailState.isSaving) "Saving..." else "Save") }
                            }
                        } else {
                            Text(tx.category?.name ?: "Uncategorized", style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    // Receipt indicator
                    if (tx.hasReceipt) {
                        LedgerLensCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Receipt, null, Modifier.size(24.dp), MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.size(8.dp))
                                Text("Receipt attached", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
