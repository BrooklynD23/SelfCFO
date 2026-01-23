package com.ledgerlens.ui.viewmodels.receipts

import com.ledgerlens.data.repositories.ReceiptEntity
import com.ledgerlens.data.repositories.ReceiptRepository
import com.ledgerlens.data.repositories.ReceiptWithItems
import com.ledgerlens.domain.Money
import com.ledgerlens.receipts.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI model for a receipt in the list.
 */
data class ReceiptUiModel(
    val id: String,
    val merchant: String,
    val date: String,
    val totalAmount: Money,
    val itemCount: Int,
    val linkedTransactionId: String? = null,
    val thumbnailPath: String? = null,
    val confidence: Double = 1.0
) {
    val isLinked: Boolean get() = linkedTransactionId != null
    val hasLowConfidence: Boolean get() = confidence < 0.7
}

/**
 * UI state for the receipts list screen.
 */
data class ReceiptsUiState(
    val receipts: List<ReceiptUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val filterLinked: Boolean? = null, // null = all, true = linked only, false = unlinked only
    val sortBy: ReceiptSortOption = ReceiptSortOption.DATE_DESC,
    val selectedReceiptIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val error: String? = null
) {
    val filteredReceipts: List<ReceiptUiModel>
        get() {
            var result = receipts
            
            if (searchQuery.isNotBlank()) {
                val query = searchQuery.lowercase()
                result = result.filter { 
                    it.merchant.lowercase().contains(query) ||
                    it.date.contains(query)
                }
            }
            
            filterLinked?.let { linked ->
                result = result.filter { it.isLinked == linked }
            }
            
            result = when (sortBy) {
                ReceiptSortOption.DATE_DESC -> result.sortedByDescending { it.date }
                ReceiptSortOption.DATE_ASC -> result.sortedBy { it.date }
                ReceiptSortOption.AMOUNT_DESC -> result.sortedByDescending { it.totalAmount.minorUnits }
                ReceiptSortOption.AMOUNT_ASC -> result.sortedBy { it.totalAmount.minorUnits }
                ReceiptSortOption.MERCHANT -> result.sortedBy { it.merchant.lowercase() }
            }
            
            return result
        }
    
    val selectedCount: Int get() = selectedReceiptIds.size
    val hasSelection: Boolean get() = selectedReceiptIds.isNotEmpty()
}

enum class ReceiptSortOption {
    DATE_DESC, DATE_ASC, AMOUNT_DESC, AMOUNT_ASC, MERCHANT
}

/**
 * UI state for receipt detail screen.
 */
data class ReceiptDetailUiState(
    val receipt: ExtractedReceipt? = null,
    val receiptId: String = "",
    val imagePath: String? = null,
    val linkedTransaction: LinkedTransactionInfo? = null,
    val participants: List<Participant> = emptyList(),
    val splitResult: SplitResult? = null,
    val isLoading: Boolean = false,
    val isSplitSheetVisible: Boolean = false,
    val error: String? = null
)

data class LinkedTransactionInfo(
    val transactionId: String,
    val description: String,
    val amount: Money,
    val date: String
)

/**
 * UI state for split receipt sheet.
 */
data class SplitReceiptUiState(
    val receipt: ExtractedReceipt? = null,
    val participants: List<Participant> = emptyList(),
    val itemAssignments: Map<Int, Set<String>> = emptyMap(), // itemIndex -> participantIds
    val splitType: SplitType = SplitType.BY_ITEM,
    val customAmounts: Map<String, Long> = emptyMap(), // participantId -> amount in cents
    val isCalculating: Boolean = false,
    val previewResult: SplitResult? = null
) {
    val canSplit: Boolean get() = participants.size >= 2 && receipt != null
    
    fun getParticipantTotal(participantId: String): Long {
        return when (splitType) {
            SplitType.BY_ITEM -> {
                receipt?.productItems?.mapIndexed { index, item ->
                    if (itemAssignments[index]?.contains(participantId) == true) {
                        val assignedCount = itemAssignments[index]?.size ?: 1
                        item.totalPrice.minorUnits / assignedCount
                    } else 0L
                }?.sum() ?: 0L
            }
            SplitType.CUSTOM -> customAmounts[participantId] ?: 0L
            SplitType.EQUAL -> {
                if (participants.isEmpty()) 0L
                else (receipt?.totalAmount?.minorUnits ?: 0L) / participants.size
            }
            SplitType.PERCENTAGE -> customAmounts[participantId] ?: 0L
        }
    }
}

/**
 * ViewModel for receipts screens.
 */
class ReceiptsViewModel(
    private val receiptRepository: ReceiptRepository
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _receiptsState = MutableStateFlow(ReceiptsUiState())
    val receiptsState: StateFlow<ReceiptsUiState> = _receiptsState.asStateFlow()

    private val _detailState = MutableStateFlow(ReceiptDetailUiState())
    val detailState: StateFlow<ReceiptDetailUiState> = _detailState.asStateFlow()

    private val _splitState = MutableStateFlow(SplitReceiptUiState())
    val splitState: StateFlow<SplitReceiptUiState> = _splitState.asStateFlow()

    init {
        loadReceipts()
    }
    
    // ========== Receipts List Actions ==========

    fun loadReceipts() {
        viewModelScope.launch {
            _receiptsState.update { it.copy(isLoading = true, error = null) }
            try {
                val receipts = receiptRepository.getAllReceipts().first()
                val uiModels = receipts.map { it.toUiModel() }
                _receiptsState.update { it.copy(
                    receipts = uiModels,
                    isLoading = false
                )}
            } catch (e: Exception) {
                _receiptsState.update { it.copy(
                    isLoading = false,
                    error = "Failed to load receipts: ${e.message}"
                )}
            }
        }
    }
    
    fun setSearchQuery(query: String) {
        _receiptsState.update { it.copy(searchQuery = query) }
    }
    
    fun setFilterLinked(linked: Boolean?) {
        _receiptsState.update { it.copy(filterLinked = linked) }
    }
    
    fun setSortOption(option: ReceiptSortOption) {
        _receiptsState.update { it.copy(sortBy = option) }
    }
    
    fun toggleSelectionMode() {
        _receiptsState.update { 
            if (it.isSelectionMode) {
                it.copy(isSelectionMode = false, selectedReceiptIds = emptySet())
            } else {
                it.copy(isSelectionMode = true)
            }
        }
    }
    
    fun toggleReceiptSelection(receiptId: String) {
        _receiptsState.update { state ->
            val newSelection = if (receiptId in state.selectedReceiptIds) {
                state.selectedReceiptIds - receiptId
            } else {
                state.selectedReceiptIds + receiptId
            }
            state.copy(selectedReceiptIds = newSelection)
        }
    }
    
    fun selectAllReceipts() {
        _receiptsState.update { state ->
            state.copy(selectedReceiptIds = state.filteredReceipts.map { it.id }.toSet())
        }
    }
    
    fun clearSelection() {
        _receiptsState.update { it.copy(selectedReceiptIds = emptySet()) }
    }
    
    fun deleteSelectedReceipts() {
        viewModelScope.launch {
            val toDelete = _receiptsState.value.selectedReceiptIds
            try {
                toDelete.forEach { receiptRepository.deleteReceipt(it) }
                _receiptsState.update { state ->
                    state.copy(
                        selectedReceiptIds = emptySet(),
                        isSelectionMode = false
                    )
                }
                loadReceipts() // Reload to refresh the list
            } catch (e: Exception) {
                _receiptsState.update { it.copy(error = "Failed to delete receipts: ${e.message}") }
            }
        }
    }
    
    // ========== Receipt Detail Actions ==========

    fun loadReceiptDetail(receiptId: String) {
        viewModelScope.launch {
            _detailState.update { it.copy(receiptId = receiptId, isLoading = true, error = null) }
            try {
                val receiptWithItems = receiptRepository.getReceiptWithItems(receiptId).first()
                if (receiptWithItems != null) {
                    _detailState.update { it.copy(
                        receipt = receiptWithItems.toExtractedReceipt(),
                        imagePath = receiptWithItems.receipt.imagePath,
                        participants = emptyList(), // Participant management kept local for now
                        isLoading = false
                    )}
                } else {
                    _detailState.update { it.copy(
                        isLoading = false,
                        error = "Receipt not found"
                    )}
                }
            } catch (e: Exception) {
                _detailState.update { it.copy(
                    isLoading = false,
                    error = "Failed to load receipt: ${e.message}"
                )}
            }
        }
    }

    fun linkToTransaction(transactionId: String) {
        viewModelScope.launch {
            try {
                val receiptId = _detailState.value.receiptId
                receiptRepository.linkToTransaction(receiptId, transactionId)
                _detailState.update { it.copy(
                    linkedTransaction = LinkedTransactionInfo(
                        transactionId = transactionId,
                        description = "Linked Transaction",
                        amount = Money.fromMinorUnits(0, "USD"),
                        date = ""
                    )
                )}
            } catch (e: Exception) {
                _detailState.update { it.copy(error = "Failed to link transaction: ${e.message}") }
            }
        }
    }

    fun unlinkTransaction() {
        viewModelScope.launch {
            try {
                val receiptId = _detailState.value.receiptId
                // Unlink by setting transaction to empty string
                receiptRepository.linkToTransaction(receiptId, "")
                _detailState.update { it.copy(linkedTransaction = null) }
            } catch (e: Exception) {
                _detailState.update { it.copy(error = "Failed to unlink transaction: ${e.message}") }
            }
        }
    }
    
    fun showSplitSheet() {
        val detail = _detailState.value
        _splitState.update { 
            SplitReceiptUiState(
                receipt = detail.receipt,
                participants = detail.participants
            )
        }
        _detailState.update { it.copy(isSplitSheetVisible = true) }
    }
    
    fun hideSplitSheet() {
        _detailState.update { it.copy(isSplitSheetVisible = false) }
    }
    
    // ========== Split Receipt Actions ==========
    
    fun addParticipant(participant: Participant) {
        _splitState.update { it.copy(participants = it.participants + participant) }
    }
    
    fun removeParticipant(participantId: String) {
        _splitState.update { state ->
            state.copy(
                participants = state.participants.filter { it.id != participantId },
                itemAssignments = state.itemAssignments.mapValues { (_, ids) -> ids - participantId },
                customAmounts = state.customAmounts - participantId
            )
        }
    }
    
    fun setSplitType(type: SplitType) {
        _splitState.update { it.copy(splitType = type) }
        recalculateSplit()
    }
    
    fun toggleItemAssignment(itemIndex: Int, participantId: String) {
        _splitState.update { state ->
            val currentAssignments = state.itemAssignments[itemIndex] ?: emptySet()
            val newAssignments = if (participantId in currentAssignments) {
                currentAssignments - participantId
            } else {
                currentAssignments + participantId
            }
            state.copy(
                itemAssignments = state.itemAssignments + (itemIndex to newAssignments)
            )
        }
        recalculateSplit()
    }
    
    fun setCustomAmount(participantId: String, amountCents: Long) {
        _splitState.update { 
            it.copy(customAmounts = it.customAmounts + (participantId to amountCents))
        }
        recalculateSplit()
    }
    
    fun assignAllItemsToParticipant(participantId: String) {
        val receipt = _splitState.value.receipt ?: return
        val assignments = receipt.productItems.indices.associateWith { 
            setOf(participantId) 
        }
        _splitState.update { it.copy(itemAssignments = assignments) }
        recalculateSplit()
    }
    
    fun splitEvenly() {
        _splitState.update { state ->
            val receipt = state.receipt ?: return@update state
            val allParticipantIds = state.participants.map { it.id }.toSet()
            val assignments = receipt.productItems.indices.associateWith { allParticipantIds }
            state.copy(
                splitType = SplitType.EQUAL,
                itemAssignments = assignments
            )
        }
        recalculateSplit()
    }
    
    private fun recalculateSplit() {
        viewModelScope.launch {
            _splitState.update { it.copy(isCalculating = true) }

            val state = _splitState.value
            val receipt = state.receipt

            if (receipt == null || state.participants.isEmpty()) {
                _splitState.update { it.copy(isCalculating = false, previewResult = null) }
                return@launch
            }

            val splitParticipants = state.participants.map { participant ->
                SplitParticipant.create(
                    participant = participant,
                    amount = state.getParticipantTotal(participant.id),
                    currencyCode = receipt.currency
                )
            }

            val result = SplitResult(
                participants = splitParticipants,
                totalAmount = receipt.totalAmount?.minorUnits ?: receipt.calculatedTotal.minorUnits,
                currencyCode = receipt.currency,
                splitType = state.splitType,
                remainder = (receipt.totalAmount?.minorUnits ?: 0L) - splitParticipants.sumOf { it.allocatedAmount }
            )

            _splitState.update { it.copy(isCalculating = false, previewResult = result) }
        }
    }

    fun confirmSplit() {
        viewModelScope.launch {
            val result = _splitState.value.previewResult ?: return@launch
            try {
                // Split result storage not yet implemented in repository
                hideSplitSheet()
                _detailState.update { it.copy(splitResult = result) }
            } catch (e: Exception) {
                _detailState.update { it.copy(error = "Failed to save split: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _receiptsState.update { it.copy(error = null) }
        _detailState.update { it.copy(error = null) }
    }

    // ========== Mapping Functions ==========

    // Extension function to map ReceiptEntity to ReceiptUiModel
    private fun ReceiptEntity.toUiModel(): ReceiptUiModel {
        return ReceiptUiModel(
            id = id,
            merchant = merchantName ?: "Unknown Merchant",
            date = receiptDate?.toString() ?: "",
            totalAmount = totalAmount ?: Money.zero("USD"),
            itemCount = 0, // Would need separate query for item count
            linkedTransactionId = linkedTransactionId,
            thumbnailPath = thumbnailPath,
            confidence = ocrConfidence?.toDouble() ?: 1.0
        )
    }

    // Extension function to map ReceiptWithItems to ExtractedReceipt
    private fun ReceiptWithItems.toExtractedReceipt(): ExtractedReceipt {
        val receiptItems = items.map { item ->
            ReceiptItem(
                name = item.name,
                quantity = item.quantity.toDouble(),
                unitPrice = Money.fromMinorUnits(item.unitPriceMinorUnits, item.currencyCode),
                totalPrice = Money.fromMinorUnits(
                    item.unitPriceMinorUnits * item.quantity,
                    item.currencyCode
                ),
                confidence = 1.0
            )
        }

        return ExtractedReceipt(
            items = receiptItems,
            merchant = receipt.merchantName ?: "Unknown",
            date = receipt.receiptDate?.toString(),
            currency = receipt.totalAmount?.currencyCode ?: "USD",
            subtotal = null,
            taxAmount = null,
            totalAmount = receipt.totalAmount,
            paymentMethod = null,
            rawText = receipt.ocrText,
            confidence = receipt.ocrConfidence?.toDouble() ?: 1.0
        )
    }
}
