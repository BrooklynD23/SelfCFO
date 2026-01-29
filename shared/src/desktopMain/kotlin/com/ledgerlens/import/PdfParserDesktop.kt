package com.ledgerlens.import

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition

/**
 * Desktop PDF parser implementation using Apache PDFBox.
 *
 * Extracts text from PDF files and parses transactions using templates.
 */
class PdfParserDesktop(
    private val templateParser: TemplateBasedParser = TemplateBasedParser()
) : PdfParser {

    override suspend fun canParse(pdfData: ByteArray): Boolean {
        return try {
            validatePdfHeader(pdfData)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun parse(
        pdfData: ByteArray,
        options: PdfParseOptions
    ): PdfParseResult {
        // Validate size
        if (pdfData.size > options.maxSizeBytes) {
            return PdfParseResult.Failure(
                ParseError.SizeExceeded(pdfData.size.toLong(), options.maxSizeBytes)
            )
        }

        // Validate PDF header
        try {
            validatePdfHeader(pdfData)
        } catch (e: Exception) {
            return PdfParseResult.Failure(
                ParseError.InvalidPdf(e.message ?: "Invalid PDF header")
            )
        }

        return try {
            withTimeout(options.timeoutMs) {
                extractAndParse(pdfData, options)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            PdfParseResult.Failure(
                ParseError.Timeout(options.timeoutMs, options.timeoutMs)
            )
        } catch (e: Exception) {
            PdfParseResult.Failure(
                ParseError.ExtractionFailed(e.message ?: "Unknown error", e)
            )
        }
    }

    private suspend fun extractAndParse(
        pdfData: ByteArray,
        options: PdfParseOptions
    ): PdfParseResult = withContext(Dispatchers.IO) {
        val document = Loader.loadPDF(pdfData)

        document.use { doc ->
            // Validate page count
            if (doc.numberOfPages > options.maxPages) {
                return@withContext PdfParseResult.Failure(
                    ParseError.PageLimitExceeded(doc.numberOfPages, options.maxPages)
                )
            }

            // Extract text from all pages
            val extractionResult = extractText(doc)

            if (extractionResult.pages.isEmpty() ||
                extractionResult.pages.all { it.lines.isEmpty() }
            ) {
                return@withContext PdfParseResult.Failure(
                    ParseError.NoTransactionsFound("No text content found in PDF")
                )
            }

            // Parse using templates
            val parseResult = templateParser.parseWithTemplate(extractionResult.pages)

            if (parseResult.transactions.isEmpty()) {
                return@withContext PdfParseResult.Failure(
                    ParseError.NoTransactionsFound(
                        "No transactions found using template: ${parseResult.template?.bankName ?: "generic"}"
                    )
                )
            }

            // Build metadata
            val metadata = StatementMetadata(
                accountName = null, // TODO: Extract from header
                accountNumber = null, // TODO: Extract last 4 digits
                statementPeriod = null, // TODO: Extract date range
                pageCount = doc.numberOfPages,
                extractionMethod = ExtractionMethod.TEXT_BASED,
                bankName = parseResult.template?.bankName,
                templateId = parseResult.template?.id
            )

            // Determine result type based on confidence
            val lowConfidenceTransactions = parseResult.transactions.filter { it.needsReview }

            if (lowConfidenceTransactions.isNotEmpty()) {
                val issues = lowConfidenceTransactions.map { tx ->
                    ParseIssue(
                        code = IssueCode.UNREADABLE_TEXT,
                        message = "Low confidence parsing for transaction",
                        location = tx.rowRef,
                        suggestedFix = "Review transaction: ${tx.descriptionRaw.take(50)}"
                    )
                }

                PdfParseResult.NeedsReview(
                    transactions = parseResult.transactions,
                    issues = issues,
                    metadata = metadata
                )
            } else {
                PdfParseResult.Success(
                    transactions = parseResult.transactions,
                    metadata = metadata,
                    warnings = parseResult.warnings
                )
            }
        }
    }

    private fun extractText(document: PDDocument): TextExtractionResult {
        val pages = mutableListOf<PageText>()
        val stripper = LineTrackingTextStripper()

        for (pageNum in 1..document.numberOfPages) {
            stripper.startPage = pageNum
            stripper.endPage = pageNum
            stripper.resetLines()

            val rawText = stripper.getText(document)
            val lines = stripper.getExtractedLines()

            pages.add(
                PageText(
                    pageNumber = pageNum,
                    lines = lines,
                    rawText = rawText
                )
            )
        }

        val hasText = pages.any { it.lines.isNotEmpty() }

        return TextExtractionResult(
            pages = pages,
            isTextBased = hasText,
            confidence = if (hasText) 0.95f else 0.0f
        )
    }

    private fun validatePdfHeader(pdfData: ByteArray) {
        require(pdfData.size >= 8) { "PDF data too small" }
        val header = pdfData.take(8).toByteArray().decodeToString()
        require(header.startsWith("%PDF-")) { "Not a valid PDF file: missing %PDF- header" }
    }
}

/**
 * Custom PDFTextStripper that tracks line positions.
 */
private class LineTrackingTextStripper : PDFTextStripper() {
    private val lines = mutableListOf<TextLine>()
    private var currentLineText = StringBuilder()
    private var currentLineNumber = 0
    private var lastY = -1f

    init {
        sortByPosition = true
    }

    fun resetLines() {
        lines.clear()
        currentLineText.clear()
        currentLineNumber = 0
        lastY = -1f
    }

    fun getExtractedLines(): List<TextLine> {
        // Flush any remaining text
        flushCurrentLine()
        return lines.toList()
    }

    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
        if (textPositions.isEmpty()) {
            currentLineText.append(text)
            return
        }

        val firstPosition = textPositions.first()
        val currentY = firstPosition.y

        // Detect new line based on Y position change
        if (lastY >= 0 && kotlin.math.abs(currentY - lastY) > 2f) {
            flushCurrentLine()
        }

        currentLineText.append(text)
        lastY = currentY
    }

    private fun flushCurrentLine() {
        val text = currentLineText.toString().trim()
        if (text.isNotEmpty()) {
            currentLineNumber++
            lines.add(
                TextLine(
                    text = text,
                    lineNumber = currentLineNumber,
                    confidence = 1.0f // Text-based extraction is high confidence
                )
            )
        }
        currentLineText.clear()
    }
}

/**
 * Factory for creating platform-specific PDF parser on Desktop.
 */
actual object PdfParserFactory {
    actual fun create(options: PdfParseOptions): PdfParser {
        return PdfParserDesktop(
            templateParser = TemplateBasedParser(options.defaultCurrencyCode)
        )
    }
}
