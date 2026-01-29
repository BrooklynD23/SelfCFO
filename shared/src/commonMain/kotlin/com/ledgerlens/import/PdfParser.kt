package com.ledgerlens.import

import kotlinx.datetime.LocalDate

/**
 * PDF parser interface for extracting transactions from bank statements.
 *
 * Platform implementations:
 * - Desktop: PDFBox for text-based PDFs
 * - Android: ML Kit for OCR on scanned PDFs
 */
interface PdfParser {
    /**
     * Parse a PDF bank statement.
     * @param pdfData Raw PDF bytes
     * @param options Parsing options
     * @return Parsed statement result
     */
    suspend fun parse(
        pdfData: ByteArray,
        options: PdfParseOptions = PdfParseOptions()
    ): PdfParseResult

    /**
     * Check if this parser supports the given PDF.
     * @param pdfData Raw PDF bytes
     * @return true if the parser can handle this PDF
     */
    suspend fun canParse(pdfData: ByteArray): Boolean
}

/**
 * Options for PDF parsing operations.
 */
data class PdfParseOptions(
    val maxPages: Int = 500,
    val maxSizeBytes: Long = 50 * 1024 * 1024, // 50 MB
    val timeoutMs: Long = 60_000, // 60 seconds
    val memoryLimitBytes: Long = 512 * 1024 * 1024, // 512 MB
    val ocrEnabled: Boolean = true,
    val ocrLanguage: String = "eng",
    val defaultCurrencyCode: String = "USD"
)

/**
 * Result of parsing a PDF statement.
 */
sealed class PdfParseResult {
    /**
     * Successful parsing with high confidence.
     */
    data class Success(
        val transactions: List<ParsedTransaction>,
        val metadata: StatementMetadata,
        val warnings: List<ParseWarning>
    ) : PdfParseResult()

    /**
     * Parsing completed but with issues requiring user review.
     */
    data class NeedsReview(
        val transactions: List<ParsedTransaction>,
        val issues: List<ParseIssue>,
        val metadata: StatementMetadata
    ) : PdfParseResult()

    /**
     * Parsing failed.
     */
    data class Failure(
        val error: ParseError
    ) : PdfParseResult()
}

/**
 * Metadata extracted from a bank statement.
 */
data class StatementMetadata(
    val accountName: String?,
    val accountNumber: String?, // Last 4 digits only for security
    val statementPeriod: DateRange?,
    val pageCount: Int,
    val extractionMethod: ExtractionMethod,
    val bankName: String? = null,
    val templateId: String? = null
)

/**
 * Date range for statement period.
 */
data class DateRange(
    val start: LocalDate,
    val end: LocalDate
)

/**
 * Method used to extract text from PDF.
 */
enum class ExtractionMethod {
    TEXT_BASED, // Embedded text extracted directly
    OCR, // Optical character recognition for scanned PDFs
    HYBRID // Combination of text and OCR
}

/**
 * Warning that doesn't prevent parsing but should be noted.
 */
data class ParseWarning(
    val code: WarningCode,
    val message: String,
    val location: String? = null // e.g., "page:2,line:15"
)

enum class WarningCode {
    LOW_CONFIDENCE,
    AMBIGUOUS_DATE,
    AMBIGUOUS_AMOUNT,
    MISSING_BALANCE,
    TRUNCATED_DESCRIPTION,
    SKIPPED_ROWS,
    UNSUPPORTED_FORMAT
}

/**
 * Issue requiring user review.
 */
data class ParseIssue(
    val code: IssueCode,
    val message: String,
    val location: String,
    val suggestedFix: String? = null
)

enum class IssueCode {
    UNREADABLE_TEXT,
    MISSING_DATE,
    INVALID_AMOUNT,
    DUPLICATE_TRANSACTION,
    BALANCE_MISMATCH,
    TEMPLATE_MISMATCH
}

/**
 * Fatal parsing error.
 */
sealed class ParseError {
    data class InvalidPdf(val reason: String) : ParseError()
    data class SizeExceeded(val actualSize: Long, val maxSize: Long) : ParseError()
    data class PageLimitExceeded(val actualPages: Int, val maxPages: Int) : ParseError()
    data class Timeout(val elapsedMs: Long, val timeoutMs: Long) : ParseError()
    data class MemoryExceeded(val message: String) : ParseError()
    data class UnsupportedFormat(val format: String) : ParseError()
    data class ExtractionFailed(val reason: String, val cause: Throwable? = null) : ParseError()
    data class NoTransactionsFound(val reason: String) : ParseError()
}

/**
 * Bounding box for text location in PDF.
 */
data class BoundingBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    companion object {
        val EMPTY = BoundingBox(0f, 0f, 0f, 0f)
    }
}

/**
 * Text extraction result from PDF.
 */
data class TextExtractionResult(
    val pages: List<PageText>,
    val isTextBased: Boolean,
    val confidence: Float
)

/**
 * Text content from a single page.
 */
data class PageText(
    val pageNumber: Int,
    val lines: List<TextLine>,
    val rawText: String = ""
)

/**
 * A line of text with position info.
 */
data class TextLine(
    val text: String,
    val lineNumber: Int,
    val boundingBox: BoundingBox = BoundingBox.EMPTY,
    val confidence: Float = 1.0f
)
