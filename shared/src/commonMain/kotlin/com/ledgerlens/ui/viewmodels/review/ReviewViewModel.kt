package com.ledgerlens.ui.viewmodels.review

import com.ledgerlens.categorization.pipeline.ReviewDecision
import com.ledgerlens.categorization.pipeline.ReviewQueueItem
import com.ledgerlens.categorization.pipeline.ReviewStatus
import com.ledgerlens.data.repositories.ReviewQueueRepository
import com.ledgerlens.data.repositories.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

/**
 * ViewModel for the Review inbox and detail screens.
 * Manages the review queue, filtering, and batch operations.
 * Now uses persistent ReviewQueueRepository with Flow-based observation.
 */
class ReviewViewModel(
    private val reviewQueueRepository: ReviewQueueRepository,
    private val transactionRepository: TransactionRepository
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private val _selectedItem = MutableStateFlow<ReviewItemUi?>(null)
    val selectedItem: StateFlow<ReviewItemUi?> = _selectedItem.asStateFlow()

    init {
        // Observe pending items from repository Flow
        viewModelScope.launch {
            combine(
                reviewQueueRepository.pendingItems,
                reviewQueueRepository.getStats()
            ) { items, stats ->
                val uiItems = items.map { it.toUiModel() }
                val currentFilter = _uiState.value.currentFilter
                
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        items = uiItems,
                        filteredItems = applyFilter(uiItems, currentFilter),
                        stats = ReviewStats(
                            totalItems = stats.totalItems,
                            pendingCount = stats.pendingCount,
                            acceptedCount = stats.acceptedCount,
                            rejectedCount = stats.rejectedCount,
                            lowConfidenceCount = uiItems.count { it.confidence < 0.5f && it.status == ReviewItemStatus.PENDING },
                            duplicateCount = uiItems.count { it.reviewType == ReviewType.POSSIBLE_DUPLICATE && it.status == ReviewItemStatus.PENDING },
                            uncategorizedCount = uiItems.count { it.reviewType == ReviewType.UNCATEGORIZED && it.status == ReviewItemStatus.PENDING }
                        ),
                        error = null
                    )
                }
            }.collect { }
        }
    }

    fun loadReviewItems() {
        // Flow automatically updates, but we can trigger a refresh if needed
        // The combine() in init will handle updates automatically
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
        }
    }

    fun setFilter(filter: ReviewFilter) {
        _uiState.update { state ->
            state.copy(
                currentFilter = filter,
                filteredItems = applyFilter(state.items, filter)
            )
        }
    }

    fun selectItem(item: ReviewItemUi) {
        _selectedItem.value = item
    }

    fun clearSelection() {
        _selectedItem.value = null
    }

    fun acceptSuggestion(itemId: String) {
        viewModelScope.launch {
            try {
                // Record decision in review queue
                reviewQueueRepository.recordDecision(itemId, ReviewDecision.Accept())
                // Mark transaction as reviewed
                transactionRepository.markAsReviewed(itemId)
                // Flow will automatically update UI
                // Move to next item if in detail view
                if (_selectedItem.value?.id == itemId) {
                    selectNextPendingItem()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to accept suggestion: ${e.message}") }
            }
        }
    }

    fun rejectSuggestion(itemId: String, newCategoryId: String) {
        viewModelScope.launch {
            try {
                // Record decision in review queue
                reviewQueueRepository.recordDecision(
                    itemId,
                    ReviewDecision.Reject(newCategoryId = newCategoryId, reason = "User correction")
                )
                // Update category in transaction repository
                transactionRepository.updateCategory(
                    transactionId = itemId,
                    categoryId = newCategoryId,
                    confidence = 1.0f,
                    reason = "user_override"
                )
                // Mark as reviewed
                transactionRepository.markAsReviewed(itemId)
                // Flow will automatically update UI
                // Move to next item if in detail view
                if (_selectedItem.value?.id == itemId) {
                    selectNextPendingItem()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to reject suggestion: ${e.message}") }
            }
        }
    }

    fun deferItem(itemId: String) {
        viewModelScope.launch {
            try {
                // Record decision in review queue
                reviewQueueRepository.recordDecision(itemId, ReviewDecision.Defer(reason = "Deferred by user"))
                // Flow will automatically update UI
                // Move to next item if in detail view
                if (_selectedItem.value?.id == itemId) {
                    selectNextPendingItem()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to defer item: ${e.message}") }
            }
        }
    }

    fun acceptAll() {
        viewModelScope.launch {
            try {
                val pendingItems = _uiState.value.filteredItems.filter {
                    it.status == ReviewItemStatus.PENDING
                }
                pendingItems.forEach { item ->
                    reviewQueueRepository.recordDecision(item.transactionId, ReviewDecision.Accept())
                    transactionRepository.markAsReviewed(item.transactionId)
                }
                // Flow will automatically update UI
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to accept all: ${e.message}") }
            }
        }
    }

    fun dismissAll() {
        viewModelScope.launch {
            try {
                val pendingItems = _uiState.value.filteredItems.filter {
                    it.status == ReviewItemStatus.PENDING
                }
                // Dismiss is treated as defer with "dismissed" reason
                pendingItems.forEach { item ->
                    reviewQueueRepository.recordDecision(
                        item.transactionId,
                        ReviewDecision.Defer(reason = "Dismissed by user")
                    )
                }
                // Flow will automatically update UI
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to dismiss all: ${e.message}") }
            }
        }
    }

    private fun selectNextPendingItem() {
        val nextItem = _uiState.value.filteredItems.firstOrNull {
            it.status == ReviewItemStatus.PENDING
        }
        _selectedItem.value = nextItem
    }

    private fun applyFilter(items: List<ReviewItemUi>, filter: ReviewFilter): List<ReviewItemUi> {
        return items.filter { item ->
            when (filter) {
                ReviewFilter.ALL -> true
                ReviewFilter.PENDING -> item.status == ReviewItemStatus.PENDING
                ReviewFilter.LOW_CONFIDENCE -> item.confidence < 0.5f && item.status == ReviewItemStatus.PENDING
                ReviewFilter.DUPLICATES -> item.reviewType == ReviewType.POSSIBLE_DUPLICATE && item.status == ReviewItemStatus.PENDING
                ReviewFilter.UNCATEGORIZED -> item.reviewType == ReviewType.UNCATEGORIZED && item.status == ReviewItemStatus.PENDING
            }
        }
    }

    private fun calculateStats(items: List<ReviewItemUi>): ReviewStats {
        val pending = items.count { it.status == ReviewItemStatus.PENDING }
        val accepted = items.count { it.status == ReviewItemStatus.ACCEPTED }
        val rejected = items.count { it.status == ReviewItemStatus.REJECTED }
        val lowConfidence = items.count { it.confidence < 0.5f && it.status == ReviewItemStatus.PENDING }
        val duplicates = items.count { it.reviewType == ReviewType.POSSIBLE_DUPLICATE && it.status == ReviewItemStatus.PENDING }
        val uncategorized = items.count { it.reviewType == ReviewType.UNCATEGORIZED && it.status == ReviewItemStatus.PENDING }

        return ReviewStats(
            totalItems = items.size,
            pendingCount = pending,
            acceptedCount = accepted,
            rejectedCount = rejected,
            lowConfidenceCount = lowConfidence,
            duplicateCount = duplicates,
            uncategorizedCount = uncategorized
        )
    }

    // Extension function to map ReviewQueueItem to ReviewItemUi
    private fun ReviewQueueItem.toUiModel(): ReviewItemUi {
        return ReviewItemUi(
            id = transactionId,
            transactionId = transactionId,
            merchantName = features.descriptionRaw,
            normalizedMerchant = features.merchantNormalized,
            description = features.descriptionRaw,
            amount = features.amountCents,
            date = enqueuedAt.toString().take(10),
            suggestedCategoryId = suggestedCategory.categoryId,
            suggestedCategoryName = suggestedCategory.categoryId, // Category name lookup would be separate
            confidence = confidence,
            alternatives = alternatives.map {
                CategoryAlternative(it.categoryId, it.categoryId, it.score)
            },
            explanation = suggestedCategory.explanation.classifierUsed,
            reviewType = when {
                confidence < 0.5f -> ReviewType.LOW_CONFIDENCE
                suggestedCategory.categoryId.isEmpty() -> ReviewType.UNCATEGORIZED
                else -> ReviewType.LOW_CONFIDENCE
            },
            status = when (this.status) {
                ReviewStatus.PENDING -> ReviewItemStatus.PENDING
                ReviewStatus.ACCEPTED -> ReviewItemStatus.ACCEPTED
                ReviewStatus.REJECTED -> ReviewItemStatus.REJECTED
                ReviewStatus.DEFERRED -> ReviewItemStatus.DEFERRED
            }
        )
    }
}

/**
 * UI state for the Review screens.
 */
data class ReviewUiState(
    val isLoading: Boolean = false,
    val items: List<ReviewItemUi> = emptyList(),
    val filteredItems: List<ReviewItemUi> = emptyList(),
    val currentFilter: ReviewFilter = ReviewFilter.PENDING,
    val stats: ReviewStats = ReviewStats(),
    val error: String? = null
)

/**
 * Statistics for the review queue.
 */
data class ReviewStats(
    val totalItems: Int = 0,
    val pendingCount: Int = 0,
    val acceptedCount: Int = 0,
    val rejectedCount: Int = 0,
    val lowConfidenceCount: Int = 0,
    val duplicateCount: Int = 0,
    val uncategorizedCount: Int = 0
) {
    val processedCount: Int get() = acceptedCount + rejectedCount
    val completionPercent: Int get() = if (totalItems > 0) ((processedCount * 100) / totalItems) else 0
}

/**
 * UI representation of a review item.
 */
data class ReviewItemUi(
    val id: String,
    val transactionId: String,
    val merchantName: String,
    val normalizedMerchant: String,
    val description: String,
    val amount: Long,
    val date: String,
    val suggestedCategoryId: String?,
    val suggestedCategoryName: String?,
    val confidence: Float,
    val alternatives: List<CategoryAlternative>,
    val explanation: String,
    val reviewType: ReviewType,
    val duplicateOf: DuplicateInfo? = null,
    val status: ReviewItemStatus = ReviewItemStatus.PENDING,
    val selectedCategoryId: String? = null,
    val reviewedAt: Instant? = null
) {
    val confidencePercent: Int get() = (confidence * 100).toInt()
    val isLowConfidence: Boolean get() = confidence < 0.5f
    val hasSuggestion: Boolean get() = suggestedCategoryId != null
    val hasAlternatives: Boolean get() = alternatives.isNotEmpty()
    val isPossibleDuplicate: Boolean get() = duplicateOf != null

    val formattedAmount: String
        get() {
            val absAmount = kotlin.math.abs(amount)
            val dollars = absAmount / 100
            val cents = absAmount % 100
            val sign = if (amount < 0) "-" else "+"
            return "$sign$$dollars.${cents.toString().padStart(2, '0')}"
        }
}

data class CategoryAlternative(
    val categoryId: String,
    val categoryName: String,
    val confidence: Float
) {
    val confidencePercent: Int get() = (confidence * 100).toInt()
}

data class DuplicateInfo(
    val transactionId: String,
    val date: String,
    val amount: Long,
    val similarity: Float
) {
    val similarityPercent: Int get() = (similarity * 100).toInt()
}

enum class ReviewType {
    LOW_CONFIDENCE,
    POSSIBLE_DUPLICATE,
    UNCATEGORIZED
}

enum class ReviewItemStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    DEFERRED,
    DISMISSED
}

enum class ReviewFilter(val displayName: String) {
    ALL("All"),
    PENDING("Pending"),
    LOW_CONFIDENCE("Low Confidence"),
    DUPLICATES("Possible Duplicates"),
    UNCATEGORIZED("Uncategorized")
}
