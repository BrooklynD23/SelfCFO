package com.ledgerlens.receipts

import com.ledgerlens.domain.Money
import com.ledgerlens.domain.MoneyParser
import com.ledgerlens.domain.CurrencyMetadata

/**
 * Pattern-based implementation of ItemExtractor.
 */
class RegexItemExtractor : ItemExtractor {

    private val PRICE_PATTERN = Regex("""[−\-]?\$?(\d{1,3}(?:,\d{3})*|\d+)(?:\.(\d{1,2}))?""")

    override fun extract(text: String, options: ExtractionOptions): ExtractionResult {
        val lines = text.split("\n", "\r\n", "\r").map { it.trim() }.filter { it.isNotEmpty() }
        return extractFromLines(lines, options)
    }

    override fun extractFromLines(lines: List<String>, options: ExtractionOptions): ExtractionResult {
        if (lines.isEmpty()) return ExtractionResult.Failure("Empty input")

        val issues = mutableListOf<ExtractionIssue>()
        val structure = if (options.detectStructure) ReceiptStructureDetector.detectStructure(lines, options.currency) else null
        val merchant = structure?.let { ReceiptStructureDetector.extractMerchant(lines, it) } ?: extractMerchantFallback(lines, options.merchantHints)
        val date = ReceiptStructureDetector.extractDate(lines)
        val time = ReceiptStructureDetector.extractTime(lines)

        val itemLines = structure?.itemLines?.let { lines.slice(it) } ?: lines
        val parsedItems = LineParser.parseLines(itemLines, options.currency).filter { it.confidence >= options.minConfidence }

        if (parsedItems.isEmpty()) issues.add(ExtractionIssue.MissingItems())

        val totalsLines = structure?.totalsLines?.let { lines.slice(it) } ?: emptyList()
        val totalsInfo = extractTotals(totalsLines, options.currency)

        parsedItems.forEachIndexed { index, item -> if (item.confidence < 0.5) issues.add(ExtractionIssue.LowConfidenceItem(index, item.name, item.confidence)) }

        val allItems = parsedItems + totalsInfo.additionalItems
        val receipt = ExtractedReceipt(allItems, merchant, date, time, totalsInfo.subtotal, totalsInfo.tax, totalsInfo.tip, totalsInfo.total, totalsInfo.paymentMethod, options.currency, calculateOverallConfidence(parsedItems), emptyList(), lines.joinToString("\n"))

        if (options.validateTotals) issues.addAll(validateReceipt(receipt))

        return when {
            parsedItems.isEmpty() && totalsInfo.total == null -> ExtractionResult.Failure("Could not extract any items or total", receipt)
            issues.isEmpty() -> ExtractionResult.Success(receipt, structure)
            issues.any { it is ExtractionIssue.TotalMismatch || it is ExtractionIssue.MissingItems } -> ExtractionResult.NeedsReview(receipt, issues, structure)
            else -> ExtractionResult.Success(receipt.copy(warnings = issues.map { it.description }), structure)
        }
    }

    private data class TotalsInfo(val subtotal: Money?, val tax: Money?, val tip: Money?, val total: Money?, val paymentMethod: String?, val additionalItems: List<ReceiptItem>)

    private fun parsePrice(priceStr: String, currency: String): Money? {
        val cleaned = priceStr.trim().replace("$", "").replace("−", "-").replace(",", "")
        val negative = cleaned.startsWith("-") || cleaned.startsWith("(")
        val digits = cleaned.replace("-", "").replace("(", "").replace(")", "")
        return try {
            val minorUnits = MoneyParser.parseToMinorUnits(digits, CurrencyMetadata.getScale(currency))
            Money.fromMinorUnits(if (negative) -minorUnits else minorUnits, currency)
        } catch (e: Exception) { null }
    }

    private fun extractPricesFromLine(line: String, currency: String) = PRICE_PATTERN.findAll(line).mapNotNull { parsePrice(it.value, currency) }.toList()

    private fun extractTotals(lines: List<String>, currency: String): TotalsInfo {
        var subtotal: Money? = null; var tax: Money? = null; var tip: Money? = null; var total: Money? = null; var paymentMethod: String? = null
        val additionalItems = mutableListOf<ReceiptItem>()

        for (line in lines) {
            val lower = line.lowercase()
            val price = extractPricesFromLine(line, currency).lastOrNull()
            when {
                lower.contains("subtotal") || lower.contains("sub-total") || lower.contains("sub total") -> { subtotal = price; price?.let { additionalItems.add(ReceiptItem.subtotal(it, extractLabel(line))) } }
                lower.contains("tax") && !lower.contains("before tax") -> { tax = price; price?.let { additionalItems.add(ReceiptItem.tax(extractLabel(line), it)) } }
                lower.contains("tip") || lower.contains("gratuity") -> { tip = price; price?.let { additionalItems.add(ReceiptItem.tip(extractLabel(line), it)) } }
                (lower.contains("total") && !lower.contains("subtotal") && !lower.contains("sub-total") && !lower.contains("sub total")) || lower.contains("amount due") || lower.contains("balance due") -> { total = price; price?.let { additionalItems.add(ReceiptItem.total(it, extractLabel(line))) } }
                lower.contains("visa") -> paymentMethod = "VISA"
                lower.contains("mastercard") -> paymentMethod = "Mastercard"
                lower.contains("amex") || lower.contains("american express") -> paymentMethod = "Amex"
                lower.contains("discover") -> paymentMethod = "Discover"
                lower.contains("cash") -> paymentMethod = "Cash"
                lower.contains("debit") -> paymentMethod = "Debit"
                lower.contains("credit") -> paymentMethod = "Credit"
            }
        }
        return TotalsInfo(subtotal, tax, tip, total, paymentMethod, additionalItems)
    }

    private fun extractLabel(line: String) = Regex("""[−\-]?\$?\d+(?:[.,]\d{1,2})?""").replace(line, "").replace(Regex("""\s{2,}"""), " ").trim().takeIf { it.isNotEmpty() } ?: "Total"

    private fun extractMerchantFallback(lines: List<String>, hints: List<String>): String? {
        for (hint in hints) for (line in lines.take(10)) if (line.contains(hint, ignoreCase = true)) return line.trim()
        return lines.take(5).firstOrNull { it.trim().length >= 3 && !it.trim().all { c -> c.isDigit() || c == '-' || c == '/' || c == ':' } && !PRICE_PATTERN.containsMatchIn(it) }?.trim()
    }

    private fun calculateOverallConfidence(items: List<ReceiptItem>) = if (items.isEmpty()) 0.0 else items.map { it.confidence }.average()

    private fun validateReceipt(receipt: ExtractedReceipt): List<ExtractionIssue> {
        val issues = mutableListOf<ExtractionIssue>()
        receipt.subtotal?.let { val diff = kotlin.math.abs(it.minorUnits - receipt.calculatedSubtotal.minorUnits); if (diff > 5) issues.add(ExtractionIssue.SubtotalMismatch(it.minorUnits, receipt.calculatedSubtotal.minorUnits, diff)) }
        receipt.totalAmount?.let { val diff = kotlin.math.abs(it.minorUnits - receipt.calculatedTotal.minorUnits); if (diff > 5) issues.add(ExtractionIssue.TotalMismatch(it.minorUnits, receipt.calculatedTotal.minorUnits, diff)) }
        if (receipt.totalAmount == null && receipt.items.none { it.type == ReceiptItemType.TOTAL }) issues.add(ExtractionIssue.MissingTotal())
        return issues
    }
}
