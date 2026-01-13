# 07: Receipts Screen

## Overview

Implement the receipt splitting UI including capture, item allocation, participant management, and settlement view.

---

## Implementation Steps

### Step 1: Receipts State

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/receipts/ReceiptsState.kt
package com.ledgerlens.ui.screens.receipts

data class ReceiptsListState(
    val isLoading: Boolean = true,
    val receipts: List<ReceiptSplitSummary> = emptyList(),
    val activeReceipts: List<ReceiptSplitSummary> = emptyList(),
    val completedReceipts: List<ReceiptSplitSummary> = emptyList()
)

data class ReceiptSplitSummary(
    val id: String,
    val merchantName: String?,
    val date: LocalDate?,
    val totalAmount: Money,
    val participantCount: Int,
    val isFullySettled: Boolean,
    val pendingAmount: Money
)

data class ReceiptSplitState(
    val isLoading: Boolean = true,
    val split: ReceiptSplitDisplay? = null,
    val items: List<ReceiptItemDisplay> = emptyList(),
    val participants: List<ParticipantDisplay> = emptyList(),
    val fees: ReceiptFeesDisplay? = null,
    val selectedItemId: String? = null,
    val allocationMode: AllocationMode = AllocationMode.NONE
)

data class ReceiptSplitDisplay(
    val id: String,
    val merchantName: String?,
    val date: String?,
    val subtotal: Money,
    val total: Money,
    val paidByParticipantId: String?
)

data class ReceiptItemDisplay(
    val id: String,
    val description: String,
    val quantity: Int,
    val unitPrice: Money,
    val totalPrice: Money,
    val allocations: List<AllocationDisplay>,
    val isFullyAllocated: Boolean
)

data class AllocationDisplay(
    val participantId: String,
    val participantName: String,
    val participantColor: String,
    val amount: Money,
    val percentage: Float
)

data class ParticipantDisplay(
    val id: String,
    val name: String,
    val color: String,
    val isCurrentUser: Boolean,
    val itemsSubtotal: Money,
    val feesShare: Money,
    val totalOwed: Money
)

data class ReceiptFeesDisplay(
    val tax: Money?,
    val tip: Money?,
    val serviceFee: Money?,
    val total: Money
)

enum class AllocationMode {
    NONE,
    EQUAL_SPLIT,
    SOLE_OWNER,
    CUSTOM
}
```

### Step 2: Receipt Split ViewModel

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/viewmodel/ReceiptSplitViewModel.kt
package com.ledgerlens.viewmodel

class ReceiptSplitViewModel(
    private val splitId: String,
    private val receiptSplitService: ReceiptSplitService,
    private val itemAllocator: ItemAllocator,
    private val participantService: ParticipantService,
    private val feeDistributionService: FeeDistributionService,
    private val settlementCalculator: SettlementCalculator
) : BaseViewModel() {

    private val _state = MutableStateFlow(ReceiptSplitState())
    val state: StateFlow<ReceiptSplitState> = _state.asStateFlow()

    init {
        loadSplit()
    }

    fun addParticipant(name: String) {
        viewModelScope.launch {
            participantService.createParticipant(name, splitId)
            loadSplit()
        }
    }

    fun removeParticipant(participantId: String) {
        viewModelScope.launch {
            participantService.removeFromSplit(participantId, splitId)
            loadSplit()
        }
    }

    fun selectItem(itemId: String?) {
        _state.update { it.copy(selectedItemId = itemId) }
    }

    fun setAllocationMode(mode: AllocationMode) {
        _state.update { it.copy(allocationMode = mode) }
    }

    fun allocateItemEqual(itemId: String, participantIds: List<String>) {
        viewModelScope.launch {
            val item = _state.value.items.find { it.id == itemId } ?: return@launch
            itemAllocator.allocateEqual(item.toReceiptItem(), participantIds)
            loadSplit()
        }
    }

    fun allocateItemSole(itemId: String, participantId: String) {
        viewModelScope.launch {
            val item = _state.value.items.find { it.id == itemId } ?: return@launch
            itemAllocator.allocateSole(item.toReceiptItem(), participantId)
            loadSplit()
        }
    }

    fun allocateAllEqual() {
        viewModelScope.launch {
            val participantIds = _state.value.participants.map { it.id }
            val items = _state.value.items.map { it.toReceiptItem() }
            itemAllocator.quickAllocateEqualAll(items, participantIds)
            loadSplit()
        }
    }

    fun setTip(amount: Long) {
        viewModelScope.launch {
            receiptSplitService.updateFees(
                splitId = splitId,
                tip = Money(amount, "USD")
            )
            loadSplit()
        }
    }

    fun setTipPercentage(percentage: Float) {
        val subtotal = _state.value.split?.subtotal ?: return
        val tipAmount = (subtotal.minorUnits * percentage / 100).toLong()
        setTip(tipAmount)
    }

    fun setPaidBy(participantId: String) {
        viewModelScope.launch {
            receiptSplitService.setPaidBy(splitId, participantId)
            loadSplit()
        }
    }

    private fun loadSplit() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            try {
                val split = receiptSplitService.getSplit(splitId)
                val items = receiptSplitService.getItems(splitId)
                val participants = participantService.getParticipantsForSplit(splitId)
                val fees = receiptSplitService.getFees(splitId)

                // Calculate participant totals
                val participantDisplays = calculateParticipantTotals(
                    participants, items, fees
                )

                _state.update {
                    it.copy(
                        isLoading = false,
                        split = split.toDisplay(),
                        items = items.map { item -> item.toDisplay() },
                        participants = participantDisplays,
                        fees = fees?.toDisplay()
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun calculateParticipantTotals(
        participants: List<Participant>,
        items: List<ReceiptItem>,
        fees: ReceiptFees?
    ): List<ParticipantDisplay> {
        val feeDistribution = fees?.let {
            feeDistributionService.distributeFees(
                split = receiptSplitService.getSplit(splitId),
                fees = it
            )
        }

        return participants.map { participant ->
            val itemsTotal = items
                .flatMap { it.allocations }
                .filter { it.participantId == participant.id }
                .sumOf { it.amount.minorUnits }

            val feesTotal = feeDistribution?.getTotalForParticipant(participant.id)?.minorUnits ?: 0L

            ParticipantDisplay(
                id = participant.id,
                name = participant.name,
                color = participant.color.hex,
                isCurrentUser = participant.isCurrentUser,
                itemsSubtotal = Money(itemsTotal, "USD"),
                feesShare = Money(feesTotal, "USD"),
                totalOwed = Money(itemsTotal + feesTotal, "USD")
            )
        }
    }
}
```

### Step 3: Receipt Split Screen

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/receipts/ReceiptSplitScreen.kt
package com.ledgerlens.ui.screens.receipts

@Composable
fun ReceiptSplitScreen(
    splitId: String,
    onBack: () -> Unit,
    onViewSettlement: () -> Unit,
    viewModel: ReceiptSplitViewModel = koinViewModel { parametersOf(splitId) }
) {
    val state by viewModel.state.collectAsState()
    var showAddParticipant by remember { mutableStateOf(false) }
    var showTipDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.split?.merchantName ?: "Split Receipt") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddParticipant = true }) {
                        Icon(Icons.Default.PersonAdd, "Add participant")
                    }
                }
            )
        },
        bottomBar = {
            SplitBottomBar(
                totalAmount = state.split?.total ?: Money(0, "USD"),
                onViewSettlement = onViewSettlement
            )
        }
    ) { padding ->
        if (state.isLoading) {
            LoadingState(modifier = Modifier.padding(padding))
        } else {
            ReceiptSplitContent(
                state = state,
                onItemClick = viewModel::selectItem,
                onAllocateEqual = { itemId, participantIds ->
                    viewModel.allocateItemEqual(itemId, participantIds)
                },
                onAllocateSole = { itemId, participantId ->
                    viewModel.allocateItemSole(itemId, participantId)
                },
                onAllocateAllEqual = viewModel::allocateAllEqual,
                onEditTip = { showTipDialog = true },
                onSetPaidBy = viewModel::setPaidBy,
                modifier = Modifier.padding(padding)
            )
        }
    }

    // Add participant dialog
    if (showAddParticipant) {
        AddParticipantDialog(
            onAdd = { name ->
                viewModel.addParticipant(name)
                showAddParticipant = false
            },
            onDismiss = { showAddParticipant = false }
        )
    }

    // Tip dialog
    if (showTipDialog) {
        TipDialog(
            subtotal = state.split?.subtotal ?: Money(0, "USD"),
            currentTip = state.fees?.tip,
            onSetTip = { amount ->
                viewModel.setTip(amount)
                showTipDialog = false
            },
            onSetPercentage = { percentage ->
                viewModel.setTipPercentage(percentage)
                showTipDialog = false
            },
            onDismiss = { showTipDialog = false }
        )
    }
}

@Composable
private fun ReceiptSplitContent(
    state: ReceiptSplitState,
    onItemClick: (String) -> Unit,
    onAllocateEqual: (String, List<String>) -> Unit,
    onAllocateSole: (String, String) -> Unit,
    onAllocateAllEqual: () -> Unit,
    onEditTip: () -> Unit,
    onSetPaidBy: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Participants row
        item {
            ParticipantsRow(
                participants = state.participants,
                paidByParticipantId = state.split?.paidByParticipantId,
                onSetPaidBy = onSetPaidBy
            )
        }

        // Quick actions
        item {
            QuickAllocationButtons(
                onAllocateAllEqual = onAllocateAllEqual
            )
        }

        // Items
        item {
            Text("Items", style = MaterialTheme.typography.titleMedium)
        }

        items(state.items, key = { it.id }) { item ->
            ReceiptItemCard(
                item = item,
                participants = state.participants,
                isSelected = item.id == state.selectedItemId,
                onClick = { onItemClick(item.id) },
                onAllocateEqual = { participantIds ->
                    onAllocateEqual(item.id, participantIds)
                },
                onAllocateSole = { participantId ->
                    onAllocateSole(item.id, participantId)
                }
            )
        }

        // Fees section
        item {
            FeesSection(
                fees = state.fees,
                subtotal = state.split?.subtotal ?: Money(0, "USD"),
                onEditTip = onEditTip
            )
        }

        // Per-person totals
        item {
            Text("Per Person", style = MaterialTheme.typography.titleMedium)
        }

        items(state.participants, key = { it.id }) { participant ->
            ParticipantTotalCard(participant = participant)
        }
    }
}

@Composable
private fun ParticipantsRow(
    participants: List<ParticipantDisplay>,
    paidByParticipantId: String?,
    onSetPaidBy: (String) -> Unit
) {
    Column {
        Text("Splitting with", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(participants, key = { it.id }) { participant ->
                ParticipantChip(
                    participant = participant,
                    isPayer = participant.id == paidByParticipantId,
                    onClick = { onSetPaidBy(participant.id) }
                )
            }
        }
    }
}

@Composable
private fun ReceiptItemCard(
    item: ReceiptItemDisplay,
    participants: List<ParticipantDisplay>,
    isSelected: Boolean,
    onClick: () -> Unit,
    onAllocateEqual: (List<String>) -> Unit,
    onAllocateSole: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    LedgerCard(onClick = { expanded = !expanded }) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Item header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (item.quantity > 1) {
                        Text(
                            text = "${item.quantity} × ${item.unitPrice.formatForDisplay()}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                MoneyText(money = item.totalPrice, style = MoneyMedium)
            }

            // Allocation indicators
            if (item.allocations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                AllocationIndicator(allocations = item.allocations)
            }

            // Expanded allocation controls
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text("Assign to:", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Participant selection
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        participants.forEach { participant ->
                            val isAllocated = item.allocations.any {
                                it.participantId == participant.id
                            }
                            FilterChip(
                                selected = isAllocated,
                                onClick = {
                                    if (isAllocated) {
                                        // Toggle off - remove from allocation
                                        val remaining = item.allocations
                                            .filter { it.participantId != participant.id }
                                            .map { it.participantId }
                                        if (remaining.isNotEmpty()) {
                                            onAllocateEqual(remaining)
                                        }
                                    } else {
                                        // Toggle on - add to allocation
                                        val current = item.allocations.map { it.participantId }
                                        onAllocateEqual(current + participant.id)
                                    }
                                },
                                label = { Text(participant.name) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(
                                                participant.color.toColor(),
                                                CircleShape
                                            )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AllocationIndicator(allocations: List<AllocationDisplay>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        allocations.forEach { allocation ->
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(allocation.participantColor.toColor(), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = allocation.participantName.first().toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
        if (allocations.size == 1) {
            Text(
                text = allocations[0].participantName,
                style = MaterialTheme.typography.labelSmall
            )
        } else {
            Text(
                text = "Split ${allocations.size} ways",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
```

---

## Acceptance Criteria

- [ ] Receipts list shows active and completed splits
- [ ] Add/remove participants works
- [ ] Item allocation to single person works
- [ ] Item allocation to multiple (equal split) works
- [ ] Quick "split all equally" action works
- [ ] Tip entry with percentage shortcuts
- [ ] Per-person totals calculated correctly
- [ ] Payer selection works
- [ ] Settlement view navigation works

---

## Estimated Complexity

**High** - Complex allocation UI with multiple interaction modes.
