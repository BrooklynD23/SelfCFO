package com.ledgerlens.ui.screens.dashboard

import com.ledgerlens.categorization.Category
import com.ledgerlens.domain.Money

/**
 * UI model for displaying a transaction in lists and summaries.
 */
data class TransactionUiModel(
    val id: String,
    val date: String,
    val merchantName: String,
    val normalizedMerchant: String? = null,
    val description: String,
    val amount: Money,
    val category: Category? = null,
    val categoryConfidence: Float? = null,
    val accountName: String? = null,
    val hasReceipt: Boolean = false,
    val needsReview: Boolean = false,
    val tags: List<String> = emptyList()
) {
    val isIncome: Boolean get() = amount.isPositive
    val isExpense: Boolean get() = amount.isNegative
    val displayMerchant: String get() = normalizedMerchant ?: merchantName
}

/**
 * UI model for category breakdown display.
 */
data class CategoryBreakdownUiModel(
    val category: Category,
    val amount: Money,
    val transactionCount: Int,
    val percentage: Float
)

/**
 * UI model for spending summary.
 */
data class SpendingSummaryUiModel(
    val totalSpending: Money,
    val totalIncome: Money,
    val netChange: Money,
    val transactionCount: Int,
    val periodLabel: String
) {
    val isPositiveNet: Boolean get() = netChange.isPositive
}

/**
 * Date range for filtering.
 */
data class DateRange(
    val startDate: String,
    val endDate: String,
    val label: String
) {
    companion object {
        fun thisMonth(label: String = "This Month") = DateRange("", "", label)
        fun lastMonth(label: String = "Last Month") = DateRange("", "", label)
        fun last30Days(label: String = "Last 30 Days") = DateRange("", "", label)
        fun last90Days(label: String = "Last 90 Days") = DateRange("", "", label)
        fun thisYear(label: String = "This Year") = DateRange("", "", label)
        fun custom(start: String, end: String) = DateRange(start, end, "Custom")
    }
}
