package com.ledgerlens.ui.viewmodels.review

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * ViewModel for the Review inbox and detail screens.
 * Manages the review queue, filtering, and batch operations.
 */
class ReviewViewModel(
    // TODO: Inject actual dependencies when available
    // private val reviewQueueManager: ReviewQueueManager,
    // private val categoryRepository: CategoryRepository,
    // private val transactionRepository: TransactionRepository
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private val _selectedItem = MutableStateFlow<ReviewItemUi?>(null)
    val selectedItem: StateFlow<ReviewItemUi?> = _selectedItem.asStateFlow()

    init {
        loadReviewItems()
    }

    fun loadReviewItems() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // TODO: Replace with actual data from ReviewQueueManager
            val mockItems = generateMockReviewItems()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    items = mockItems,
                    stats = calculateStats(mockItems)
                )
            }
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
        updateItemStatus(itemId, ReviewItemStatus.ACCEPTED)
    }

    fun rejectSuggestion(itemId: String, newCategoryId: String) {
        viewModelScope.launch {
            _uiState.update { state ->
                val updatedItems = state.items.map { item ->
                    if (item.id == itemId) {
                        item.copy(
                            status = ReviewItemStatus.REJECTED,
                            selectedCategoryId = newCategoryId,
                            reviewedAt = Clock.System.now()
                        )
                    } else item
                }
                state.copy(
                    items = updatedItems,
                    filteredItems = applyFilter(updatedItems, state.currentFilter),
                    stats = calculateStats(updatedItems)
                )
            }
            // Move to next item if in detail view
            if (_selectedItem.value?.id == itemId) {
                selectNextPendingItem()
            }
        }
    }

    fun deferItem(itemId: String) {
        updateItemStatus(itemId, ReviewItemStatus.DEFERRED)
    }

    fun acceptAll() {
        viewModelScope.launch {
            _uiState.update { state ->
                val updatedItems = state.filteredItems.map { item ->
                    if (item.status == ReviewItemStatus.PENDING) {
                        item.copy(
                            status = ReviewItemStatus.ACCEPTED,
                            reviewedAt = Clock.System.now()
                        )
                    } else item
                }
                // Merge back into full list
                val allItems = state.items.map { item ->
                    updatedItems.find { it.id == item.id } ?: item
                }
                state.copy(
                    items = allItems,
                    filteredItems = applyFilter(allItems, state.currentFilter),
                    stats = calculateStats(allItems)
                )
            }
        }
    }

    fun dismissAll() {
        viewModelScope.launch {
            _uiState.update { state ->
                val updatedItems = state.filteredItems.map { item ->
                    if (item.status == ReviewItemStatus.PENDING) {
                        item.copy(
                            status = ReviewItemStatus.DISMISSED,
                            reviewedAt = Clock.System.now()
                        )
                    } else item
                }
                val allItems = state.items.map { item ->
                    updatedItems.find { it.id == item.id } ?: item
                }
                state.copy(
                    items = allItems,
                    filteredItems = applyFilter(allItems, state.currentFilter),
                    stats = calculateStats(allItems)
                )
            }
        }
    }

    private fun updateItemStatus(itemId: String, status: ReviewItemStatus) {
        viewModelScope.launch {
            _uiState.update { state ->
                val updatedItems = state.items.map { item ->
                    if (item.id == itemId) {
                        item.copy(status = status, reviewedAt = Clock.System.now())
                    } else item
                }
                state.copy(
                    items = updatedItems,
                    filteredItems = applyFilter(updatedItems, state.currentFilter),
                    stats = calculateStats(updatedItems)
                )
            }
            // Move to next item if in detail view
            if (_selectedItem.value?.id == itemId) {
                selectNextPendingItem()
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

    private fun generateMockReviewItems(): List<ReviewItemUi> {
        // TODO: Replace with actual data from ReviewQueueManager
        return listOf(
            ReviewItemUi(
                id = "1",
                transactionId = "txn_001",
                merchantName = "AMZN MKTP US*2K4J8H9",
                normalizedMerchant = "Amazon",
                description = "Online purchase",
                amount = -4599,
                date = "2024-01-15",
                suggestedCategoryId = "shopping",
                suggestedCategoryName = "Shopping",
                confidence = 0.45f,
                alternatives = listOf(
                    CategoryAlternative("online_services", "Online Services", 0.30f),
                    CategoryAlternative("entertainment", "Entertainment", 0.15f)
                ),
                explanation = "Merchant 'Amazon' often categorized as Shopping, but low confidence due to ambiguous description",
                reviewType = ReviewType.LOW_CONFIDENCE,
                status = ReviewItemStatus.PENDING
            ),
            ReviewItemUi(
                id = "2",
                transactionId = "txn_002",
                merchantName = "UBER *TRIP",
                normalizedMerchant = "Uber",
                description = "Uber trip",
                amount = -1850,
                date = "2024-01-14",
                suggestedCategoryId = "transportation",
                suggestedCategoryName = "Transportation",
                confidence = 0.92f,
                alternatives = emptyList(),
                explanation = "Merchant 'Uber' consistently categorized as Transportation",
                reviewType = ReviewType.POSSIBLE_DUPLICATE,
                duplicateOf = DuplicateInfo(
                    transactionId = "txn_existing_045",
                    date = "2024-01-14",
                    amount = -1850,
                    similarity = 0.95f
                ),
                status = ReviewItemStatus.PENDING
            ),
            ReviewItemUi(
                id = "3",
                transactionId = "txn_003",
                merchantName = "POS DEBIT 12345",
                normalizedMerchant = "Unknown",
                description = "Point of sale transaction",
                amount = -2340,
                date = "2024-01-13",
                suggestedCategoryId = null,
                suggestedCategoryName = null,
                confidence = 0.0f,
                alternatives = listOf(
                    CategoryAlternative("shopping", "Shopping", 0.20f),
                    CategoryAlternative("dining", "Dining", 0.15f),
                    CategoryAlternative("groceries", "Groceries", 0.10f)
                ),
                explanation = "Unable to determine category from generic POS description",
                reviewType = ReviewType.UNCATEGORIZED,
                status = ReviewItemStatus.PENDING
            ),
            ReviewItemUi(
                id = "4",
                transactionId = "txn_004",
                merchantName = "SPOTIFY USA",
                normalizedMerchant = "Spotify",
                description = "Monthly subscription",
                amount = -999,
                date = "2024-01-12",
                suggestedCategoryId = "subscriptions",
                suggestedCategoryName = "Subscriptions",
                confidence = 0.38f,
                alternatives = listOf(
                    CategoryAlternative("entertainment", "Entertainment", 0.35f),
                    CategoryAlternative("music", "Music", 0.20f)
                ),
                explanation = "Merchant 'Spotify' could be Subscriptions or Entertainment",
                reviewType = ReviewType.LOW_CONFIDENCE,
                status = ReviewItemStatus.PENDING
            ),
            ReviewItemUi(
                id = "5",
                transactionId = "txn_005",
                merchantName = "WHOLEFDS MKT 10847",
                normalizedMerchant = "Whole Foods",
                description = "Grocery purchase",
                amount = -8745,
                date = "2024-01-11",
                suggestedCategoryId = "groceries",
                suggestedCategoryName = "Groceries",
                confidence = 0.88f,
                alternatives = emptyList(),
                explanation = "Merchant 'Whole Foods' consistently categorized as Groceries",
                reviewType = ReviewType.LOW_CONFIDENCE,
                status = ReviewItemStatus.PENDING
            )
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
