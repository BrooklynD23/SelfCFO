package com.ledgerlens.ui.viewmodels.corrections

import com.ledgerlens.categorization.*
import com.ledgerlens.categorization.pipeline.ReviewQueueItem
import com.ledgerlens.categorization.pipeline.ReviewStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CorrectionUiState(
    val isLoading: Boolean = false,
    val pendingCorrections: List<PendingCorrectionUiModel> = emptyList(),
    val availableCategories: List<Category> = emptyList(),
    val selectedCorrections: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val isBatchProcessing: Boolean = false,
    val batchProgress: BatchProgressState? = null,
    val suggestedRules: List<SuggestedRuleUiModel> = emptyList(),
    val stats: CorrectionStatsUiModel = CorrectionStatsUiModel(),
    val error: String? = null,
    val successMessage: String? = null
) {
    val hasPendingCorrections: Boolean get() = pendingCorrections.isNotEmpty()
    val selectedCount: Int get() = selectedCorrections.size
    val canBatchProcess: Boolean get() = selectedCorrections.isNotEmpty() && !isBatchProcessing
}

data class PendingCorrectionUiModel(
    val id: String,
    val transactionId: String,
    val merchantName: String,
    val description: String,
    val amount: String,
    val oldCategoryId: String,
    val oldCategoryName: String,
    val newCategoryId: String,
    val newCategoryName: String,
    val originalConfidence: Float,
    val classifierUsed: String,
    val features: TransactionFeatures
) {
    val isLowConfidence: Boolean get() = originalConfidence < 0.5f
    val confidencePercent: Int get() = (originalConfidence * 100).toInt()
}

data class SuggestedRuleUiModel(
    val merchantPattern: String,
    val targetCategoryId: String,
    val targetCategoryName: String,
    val supportingCount: Int,
    val confidence: Float,
    val reason: String
) {
    val confidencePercent: Int get() = (confidence * 100).toInt()
}

data class BatchProgressState(
    val total: Int,
    val processed: Int,
    val succeeded: Int,
    val failed: Int
) {
    val progressPercent: Float get() = if (total > 0) processed.toFloat() / total else 0f
    val isComplete: Boolean get() = processed >= total
}

data class CorrectionStatsUiModel(
    val totalCorrections: Int = 0,
    val todayCorrections: Int = 0,
    val merchantCorrectionRate: Float = 0f,
    val averageLearningImpact: Float = 0f
)

sealed class CorrectionEvent {
    data class ShowSnackbar(val message: String) : CorrectionEvent()
    data class NavigateToTransaction(val transactionId: String) : CorrectionEvent()
    data class RuleSuggestionAccepted(val merchantPattern: String, val categoryId: String) : CorrectionEvent()
    object BatchProcessingComplete : CorrectionEvent()
}

class CorrectionViewModel(
    private val correctionProcessor: CorrectionProcessor? = null,
    private val correctionRepository: CorrectionRepository? = null
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(CorrectionUiState())
    val uiState: StateFlow<CorrectionUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<CorrectionEvent?>(null)
    val events: StateFlow<CorrectionEvent?> = _events.asStateFlow()

    private val pendingQueue = mutableListOf<PendingCorrection>()
    private var nextId = 1

    init {
        loadCategories()
    }

    fun loadPendingCorrections() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                delay(200)
                val uiModels = pendingQueue.mapIndexed { index, pending ->
                    pending.toUiModel("pending_$index", _uiState.value.availableCategories)
                }
                _uiState.update { it.copy(isLoading = false, pendingCorrections = uiModels) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load corrections") }
            }
        }
    }

    fun addPendingCorrection(
        transactionId: String,
        features: TransactionFeatures,
        oldCategoryId: String,
        newCategoryId: String,
        originalConfidence: Float,
        classifierUsed: String
    ) {
        val pending = PendingCorrection(
            transactionId = transactionId,
            features = features,
            oldCategoryId = oldCategoryId,
            newCategoryId = newCategoryId,
            originalConfidence = originalConfidence,
            classifierUsed = classifierUsed
        )
        pendingQueue.add(pending)
        
        val uiModel = pending.toUiModel("pending_${nextId++}", _uiState.value.availableCategories)
        _uiState.update { state ->
            state.copy(pendingCorrections = state.pendingCorrections + uiModel)
        }
    }

    fun addFromReviewQueue(item: ReviewQueueItem, newCategoryId: String) {
        addPendingCorrection(
            transactionId = item.transactionId,
            features = item.features,
            oldCategoryId = item.categoryId,
            newCategoryId = newCategoryId,
            originalConfidence = item.confidence,
            classifierUsed = item.suggestedCategory.explanation.classifierUsed
        )
    }

    fun removePendingCorrection(id: String) {
        val index = _uiState.value.pendingCorrections.indexOfFirst { it.id == id }
        if (index >= 0 && index < pendingQueue.size) {
            pendingQueue.removeAt(index)
        }
        _uiState.update { state ->
            state.copy(
                pendingCorrections = state.pendingCorrections.filter { it.id != id },
                selectedCorrections = state.selectedCorrections - id
            )
        }
    }

    fun updateCorrectionCategory(id: String, newCategoryId: String) {
        val index = _uiState.value.pendingCorrections.indexOfFirst { it.id == id }
        if (index >= 0 && index < pendingQueue.size) {
            val old = pendingQueue[index]
            pendingQueue[index] = old.copy(newCategoryId = newCategoryId)
        }
        
        val categories = _uiState.value.availableCategories
        val category = categories.find { it.id == newCategoryId }
        
        _uiState.update { state ->
            state.copy(
                pendingCorrections = state.pendingCorrections.map { correction ->
                    if (correction.id == id) {
                        correction.copy(
                            newCategoryId = newCategoryId,
                            newCategoryName = category?.name ?: newCategoryId
                        )
                    } else correction
                }
            )
        }
    }

    fun processAllCorrections() {
        if (pendingQueue.isEmpty()) return
        processBatch(pendingQueue.toList())
    }

    fun processSelectedCorrections() {
        val selected = _uiState.value.selectedCorrections
        if (selected.isEmpty()) return
        
        val toProcess = pendingQueue.filterIndexed { index, _ ->
            "pending_$index" in selected || _uiState.value.pendingCorrections.any { 
                it.id in selected && it.transactionId == pendingQueue.getOrNull(index)?.transactionId 
            }
        }
        processBatch(toProcess)
    }

    private fun processBatch(corrections: List<PendingCorrection>) {
        viewModelScope.launch {
            _uiState.update { it.copy(
                isBatchProcessing = true,
                batchProgress = BatchProgressState(corrections.size, 0, 0, 0)
            )}

            var succeeded = 0
            var failed = 0

            for ((index, correction) in corrections.withIndex()) {
                try {
                    if (correctionProcessor != null) {
                        correctionProcessor.processCorrection(
                            transactionId = correction.transactionId,
                            features = correction.features,
                            oldCategoryId = correction.oldCategoryId,
                            newCategoryId = correction.newCategoryId,
                            originalConfidence = correction.originalConfidence,
                            classifierUsed = correction.classifierUsed
                        )
                    }
                    succeeded++
                    pendingQueue.remove(correction)
                } catch (e: Exception) {
                    failed++
                }
                
                _uiState.update { it.copy(
                    batchProgress = BatchProgressState(corrections.size, index + 1, succeeded, failed)
                )}
                delay(50) // Brief delay for UI feedback
            }

            val uiModels = pendingQueue.mapIndexed { idx, pending ->
                pending.toUiModel("pending_$idx", _uiState.value.availableCategories)
            }
            
            _uiState.update { it.copy(
                isBatchProcessing = false,
                batchProgress = null,
                pendingCorrections = uiModels,
                selectedCorrections = emptySet(),
                isSelectionMode = false,
                successMessage = "Processed $succeeded corrections" + if (failed > 0) " ($failed failed)" else ""
            )}
            
            _events.value = CorrectionEvent.BatchProcessingComplete
            loadSuggestedRules()
        }
    }

    fun toggleSelectionMode() {
        _uiState.update { it.copy(
            isSelectionMode = !it.isSelectionMode,
            selectedCorrections = emptySet()
        )}
    }

    fun toggleCorrectionSelection(id: String) {
        _uiState.update { state ->
            val selected = state.selectedCorrections
            state.copy(selectedCorrections = if (id in selected) selected - id else selected + id)
        }
    }

    fun selectAllCorrections() {
        _uiState.update { state ->
            state.copy(selectedCorrections = state.pendingCorrections.map { it.id }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedCorrections = emptySet()) }
    }

    fun loadSuggestedRules() {
        viewModelScope.launch {
            try {
                if (correctionProcessor != null) {
                    val analysis = correctionProcessor.analyzeCorrections()
                    val categories = _uiState.value.availableCategories
                    
                    val ruleModels = analysis.suggestedRules.map { rule ->
                        SuggestedRuleUiModel(
                            merchantPattern = rule.merchantPattern,
                            targetCategoryId = rule.targetCategoryId,
                            targetCategoryName = categories.find { it.id == rule.targetCategoryId }?.name 
                                ?: rule.targetCategoryId,
                            supportingCount = rule.supportingCorrections,
                            confidence = rule.confidence,
                            reason = rule.reason
                        )
                    }
                    _uiState.update { it.copy(suggestedRules = ruleModels) }
                }
            } catch (e: Exception) {
                // Silently ignore - rules are optional
            }
        }
    }

    fun acceptSuggestedRule(merchantPattern: String, categoryId: String) {
        _events.value = CorrectionEvent.RuleSuggestionAccepted(merchantPattern, categoryId)
        _uiState.update { state ->
            state.copy(suggestedRules = state.suggestedRules.filter { it.merchantPattern != merchantPattern })
        }
    }

    fun dismissSuggestedRule(merchantPattern: String) {
        _uiState.update { state ->
            state.copy(suggestedRules = state.suggestedRules.filter { it.merchantPattern != merchantPattern })
        }
    }

    fun onCorrectionClicked(id: String) {
        if (_uiState.value.isSelectionMode) {
            toggleCorrectionSelection(id)
        } else {
            val correction = _uiState.value.pendingCorrections.find { it.id == id }
            correction?.let {
                _events.value = CorrectionEvent.NavigateToTransaction(it.transactionId)
            }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            _uiState.update { it.copy(availableCategories = generateDefaultCategories()) }
        }
    }

    fun clearEvent() {
        _events.value = null
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun dismissSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }

    private fun PendingCorrection.toUiModel(id: String, categories: List<Category>): PendingCorrectionUiModel {
        val oldCategory = categories.find { it.id == oldCategoryId }
        val newCategory = categories.find { it.id == newCategoryId }
        
        return PendingCorrectionUiModel(
            id = id,
            transactionId = transactionId,
            merchantName = features.merchantNormalized,
            description = features.descriptionRaw,
            amount = formatAmount(features.amountCents, features.isDebit),
            oldCategoryId = oldCategoryId,
            oldCategoryName = oldCategory?.name ?: oldCategoryId,
            newCategoryId = newCategoryId,
            newCategoryName = newCategory?.name ?: newCategoryId,
            originalConfidence = originalConfidence,
            classifierUsed = classifierUsed,
            features = features
        )
    }

    private fun formatAmount(cents: Long, isDebit: Boolean): String {
        val abs = kotlin.math.abs(cents)
        val dollars = abs / 100
        val remainder = abs % 100
        val sign = if (isDebit) "-" else "+"
        return "$sign$$dollars.${remainder.toString().padStart(2, '0')}"
    }

    private fun generateDefaultCategories() = listOf(
        Category(id = "food", name = "Food & Dining", color = "#FF5722"),
        Category(id = "transport", name = "Transportation", color = "#2196F3"),
        Category(id = "shopping", name = "Shopping", color = "#FF9800"),
        Category(id = "entertainment", name = "Entertainment", color = "#E91E63"),
        Category(id = "utilities", name = "Utilities", color = "#607D8B"),
        Category(id = "healthcare", name = "Healthcare", color = "#F44336"),
        Category(id = "income", name = "Income", isSystemDefault = true, color = "#4CAF50"),
        Category(id = "uncategorized", name = "Uncategorized", isSystemDefault = true, color = "#9E9E9E")
    )
}
