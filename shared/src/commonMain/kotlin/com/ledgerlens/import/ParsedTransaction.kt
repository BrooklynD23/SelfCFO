package com.ledgerlens.import

import com.ledgerlens.domain.Money
import kotlinx.datetime.LocalDate

/**
 * A transaction parsed from a bank statement.
 *
 * This is an intermediate representation before normalization and storage.
 * Contains raw extracted data with confidence scores for review.
 */
data class ParsedTransaction(
    val rowRef: String,  // e.g., "page:2,line:15"
    val postedDate: LocalDate,
    val transactionDate: LocalDate?,
    val descriptionRaw: String,
    val amount: Money,
    val balance: Money?,
    val confidence: Float,  // 0.0 to 1.0
    val transactionType: TransactionType = TransactionType.UNKNOWN,
    val checkNumber: String? = null,
    val referenceNumber: String? = null
) {
    /**
     * Whether this transaction has high enough confidence to be auto-imported.
     */
    val isHighConfidence: Boolean get() = confidence >= 0.9f

    /**
     * Whether this transaction requires manual review.
     */
    val needsReview: Boolean get() = confidence < 0.7f
}

/**
 * Type of transaction as detected from statement.
 */
enum class TransactionType {
    DEBIT,       // Money going out
    CREDIT,      // Money coming in
    TRANSFER,    // Internal transfer
    FEE,         // Bank fee
    INTEREST,    // Interest earned/charged
    ADJUSTMENT,  // Account adjustment
    UNKNOWN      // Could not determine
}

/**
 * Raw extracted row from PDF before parsing.
 */
data class RawTableRow(
    val pageNumber: Int,
    val lineNumber: Int,
    val cells: List<String>,
    val rawText: String,
    val boundingBox: BoundingBox = BoundingBox.EMPTY
) {
    val rowRef: String get() = "page:$pageNumber,line:$lineNumber"
}

/**
 * Table region detected in a PDF page.
 */
data class TableRegion(
    val pageNumber: Int,
    val rows: List<RawTableRow>,
    val headerRow: RawTableRow?,
    val boundingBox: BoundingBox
)

/**
 * Result of amount extraction.
 */
data class ExtractedAmount(
    val value: Money,
    val isDebit: Boolean,
    val confidence: Float,
    val rawText: String
)

/**
 * Result of date extraction.
 */
data class ExtractedDate(
    val date: LocalDate,
    val confidence: Float,
    val rawText: String,
    val format: DateFormat
)

/**
 * Common date formats found in bank statements.
 */
enum class DateFormat {
    MM_DD_YYYY,      // 01/15/2024
    MM_DD_YY,        // 01/15/24
    DD_MM_YYYY,      // 15/01/2024
    YYYY_MM_DD,      // 2024-01-15
    MMM_DD_YYYY,     // Jan 15, 2024
    DD_MMM_YYYY,     // 15 Jan 2024
    UNKNOWN
}

/**
 * Import batch representing a parsed statement.
 */
data class ImportBatch(
    val id: String,
    val sourceFile: String,
    val metadata: StatementMetadata,
    val transactions: List<ParsedTransaction>,
    val status: ImportStatus,
    val createdAt: kotlinx.datetime.Instant
)

/**
 * Status of an import batch.
 */
enum class ImportStatus {
    PENDING_REVIEW,
    APPROVED,
    PARTIALLY_IMPORTED,
    IMPORTED,
    REJECTED,
    FAILED
}
