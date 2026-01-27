package com.ledgerlens.import

import com.ledgerlens.domain.Money
import kotlinx.datetime.LocalDate

/**
 * Canonical parsed transaction output from any import source (CSV/PDF/etc).
 *
 * This is intentionally UI-agnostic; review/overrides happen in other layers.
 */
data class ParsedTransaction(
    val rowRef: String, // e.g. "row:42" or "page:2,line:15"
    val postedDate: LocalDate,
    val transactionDate: LocalDate? = null,
    val descriptionRaw: String,
    val amount: Money,
    val balance: Money? = null,
    val confidence: Float = 1.0f, // parse confidence (not categorization confidence)
    val transactionType: TransactionType = TransactionType.UNKNOWN,
    val checkNumber: String? = null,
    val referenceNumber: String? = null
) {
    val isHighConfidence: Boolean get() = confidence >= 0.9f
    val needsReview: Boolean get() = confidence < 0.7f
}

enum class TransactionType {
    DEBIT,       // Money going out
    CREDIT,      // Money coming in
    TRANSFER,    // Internal transfer
    FEE,         // Bank fee
    INTEREST,    // Interest earned/charged
    ADJUSTMENT,  // Account adjustment
    UNKNOWN      // Could not determine
}

data class ExtractedAmount(
    val value: Money,
    val isDebit: Boolean,
    val confidence: Float,
    val rawText: String
)

data class ExtractedDate(
    val date: LocalDate,
    val confidence: Float,
    val rawText: String,
    val format: DateFormat
)

enum class DateFormat {
    MM_DD_YYYY,      // 01/15/2024
    MM_DD_YY,        // 01/15/24
    DD_MM_YYYY,      // 15/01/2024
    YYYY_MM_DD,      // 2024-01-15
    MMM_DD_YYYY,     // Jan 15, 2024
    DD_MMM_YYYY,     // 15 Jan 2024
    UNKNOWN
}
