package com.ledgerlens.receipts

interface ItemExtractor {
    fun extract(text: String, options: ExtractionOptions = ExtractionOptions()): ExtractionResult
    fun extractFromLines(lines: List<String>, options: ExtractionOptions = ExtractionOptions()): ExtractionResult
}

data class ExtractionOptions(val currency: String = "USD", val minConfidence: Double = 0.3, val detectStructure: Boolean = true, val validateTotals: Boolean = true, val merchantHints: List<String> = emptyList(), val dateHints: List<String> = emptyList())

sealed class ExtractionResult {
    data class Success(val receipt: ExtractedReceipt, val structureInfo: ReceiptStructure? = null) : ExtractionResult()
    data class NeedsReview(val receipt: ExtractedReceipt, val issues: List<ExtractionIssue>, val structureInfo: ReceiptStructure? = null) : ExtractionResult()
    data class Failure(val reason: String, val partialResult: ExtractedReceipt? = null) : ExtractionResult()
    val isSuccess get() = this is Success
    val receiptOrNull get() = when (this) { is Success -> receipt; is NeedsReview -> receipt; is Failure -> partialResult }
}

sealed class ExtractionIssue {
    data class TotalMismatch(val expected: Long, val calculated: Long, val differenceMinorUnits: Long) : ExtractionIssue()
    data class SubtotalMismatch(val expected: Long, val calculated: Long, val differenceMinorUnits: Long) : ExtractionIssue()
    data class LowConfidenceItem(val itemIndex: Int, val itemName: String, val confidence: Double) : ExtractionIssue()
    data class MissingTotal(val message: String = "No total found") : ExtractionIssue()
    data class MissingItems(val message: String = "No items found") : ExtractionIssue()
    data class AmbiguousPrice(val line: String) : ExtractionIssue()
    object UnrecognizedFormat : ExtractionIssue()
    val description get() = when (this) { is TotalMismatch -> "Total mismatch"; is SubtotalMismatch -> "Subtotal mismatch"; is LowConfidenceItem -> "Low confidence: $itemName"; is MissingTotal -> message; is MissingItems -> message; is AmbiguousPrice -> "Ambiguous: $line"; UnrecognizedFormat -> "Unrecognized format" }
}

data class ReceiptStructure(val headerLines: IntRange?, val itemLines: IntRange?, val totalsLines: IntRange?, val footerLines: IntRange?, val totalLineCount: Int) {
    val hasHeader get() = headerLines != null; val hasItems get() = itemLines != null; val hasTotals get() = totalsLines != null; val hasFooter get() = footerLines != null
}
