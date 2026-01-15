package com.ledgerlens.receipts

import com.ledgerlens.domain.Money
import com.ledgerlens.domain.MoneyParser
import com.ledgerlens.domain.CurrencyMetadata

class RegexItemExtractor : ItemExtractor {
    private val PRICE_PAT = Regex("""[−\-]?\$?(\d{1,3}(?:,\d{3})*|\d+)(?:\.(\d{1,2}))?""")

    override fun extract(text: String, options: ExtractionOptions) = extractFromLines(text.split("\n", "\r\n", "\r").map { it.trim() }.filter { it.isNotEmpty() }, options)

    override fun extractFromLines(lines: List<String>, options: ExtractionOptions): ExtractionResult {
        if (lines.isEmpty()) return ExtractionResult.Failure("Empty input")
        val issues = mutableListOf<ExtractionIssue>()
        val structure = if (options.detectStructure) ReceiptStructureDetector.detectStructure(lines, options.currency) else null
        val merchant = structure?.let { ReceiptStructureDetector.extractMerchant(lines, it) } ?: extractMerchantFallback(lines, options.merchantHints)
        val date = ReceiptStructureDetector.extractDate(lines); val time = ReceiptStructureDetector.extractTime(lines)
        val itemLines = structure?.itemLines?.let { lines.slice(it) } ?: lines
        val parsedItems = LineParser.parseLines(itemLines, options.currency).filter { it.confidence >= options.minConfidence }
        if (parsedItems.isEmpty()) issues.add(ExtractionIssue.MissingItems())
        val totalsLines = structure?.totalsLines?.let { lines.slice(it) } ?: emptyList()
        val totalsInfo = extractTotals(totalsLines, options.currency)
        parsedItems.forEachIndexed { i, item -> if (item.confidence < 0.5) issues.add(ExtractionIssue.LowConfidenceItem(i, item.name, item.confidence)) }
        val allItems = parsedItems + totalsInfo.additionalItems
        val receipt = ExtractedReceipt(allItems, merchant, date, time, totalsInfo.subtotal, totalsInfo.tax, totalsInfo.tip, totalsInfo.total, totalsInfo.paymentMethod, options.currency, if (parsedItems.isEmpty()) 0.0 else parsedItems.map { it.confidence }.average(), emptyList(), lines.joinToString("\n"))
        if (options.validateTotals) issues.addAll(validateReceipt(receipt))
        return when {
            parsedItems.isEmpty() && totalsInfo.total == null -> ExtractionResult.Failure("Could not extract any items or total", receipt)
            issues.isEmpty() -> ExtractionResult.Success(receipt, structure)
            issues.any { it is ExtractionIssue.TotalMismatch || it is ExtractionIssue.MissingItems } -> ExtractionResult.NeedsReview(receipt, issues, structure)
            else -> ExtractionResult.Success(receipt.copy(warnings = issues.map { it.description }), structure)
        }
    }

    private data class TotalsInfo(val subtotal: Money?, val tax: Money?, val tip: Money?, val total: Money?, val paymentMethod: String?, val additionalItems: List<ReceiptItem>)

    private fun parsePrice(s: String, c: String): Money? { val cl = s.trim().replace("$", "").replace("−", "-").replace(",", ""); val neg = cl.startsWith("-") || cl.startsWith("("); val d = cl.replace("-", "").replace("(", "").replace(")", ""); return try { Money.fromMinorUnits(MoneyParser.parseToMinorUnits(d, CurrencyMetadata.getScale(c)).let { if (neg) -it else it }, c) } catch (e: Exception) { null } }
    private fun extractPricesFromLine(line: String, c: String) = PRICE_PAT.findAll(line).mapNotNull { parsePrice(it.value, c) }.toList()

    private fun extractTotals(lines: List<String>, c: String): TotalsInfo {
        var subtotal: Money? = null; var tax: Money? = null; var tip: Money? = null; var total: Money? = null; var pm: String? = null; val items = mutableListOf<ReceiptItem>()
        for (l in lines) { val lo = l.lowercase(); val p = extractPricesFromLine(l, c).lastOrNull()
            when { lo.contains("subtotal") || lo.contains("sub-total") || lo.contains("sub total") -> { subtotal = p; p?.let { items.add(ReceiptItem.subtotal(it, extractLabel(l))) } }
                lo.contains("tax") && !lo.contains("before tax") -> { tax = p; p?.let { items.add(ReceiptItem.tax(extractLabel(l), it)) } }
                lo.contains("tip") || lo.contains("gratuity") -> { tip = p; p?.let { items.add(ReceiptItem.tip(extractLabel(l), it)) } }
                (lo.contains("total") && !lo.contains("subtotal") && !lo.contains("sub-total") && !lo.contains("sub total")) || lo.contains("amount due") || lo.contains("balance due") -> { total = p; p?.let { items.add(ReceiptItem.total(it, extractLabel(l))) } }
                lo.contains("visa") -> pm = "VISA"; lo.contains("mastercard") -> pm = "Mastercard"; lo.contains("amex") -> pm = "Amex"; lo.contains("cash") -> pm = "Cash"; lo.contains("debit") -> pm = "Debit"; lo.contains("credit") -> pm = "Credit"
            }
        }
        return TotalsInfo(subtotal, tax, tip, total, pm, items)
    }

    private fun extractLabel(l: String) = Regex("""[−\-]?\$?\d+(?:[.,]\d{1,2})?""").replace(l, "").replace(Regex("""\s{2,}"""), " ").trim().takeIf { it.isNotEmpty() } ?: "Total"
    private fun extractMerchantFallback(lines: List<String>, hints: List<String>): String? { for (h in hints) for (l in lines.take(10)) if (l.contains(h, true)) return l.trim(); return lines.take(5).firstOrNull { it.trim().length >= 3 && !it.trim().all { c -> c.isDigit() || c == '-' || c == '/' || c == ':' } && !PRICE_PAT.containsMatchIn(it) }?.trim() }

    private fun validateReceipt(r: ExtractedReceipt): List<ExtractionIssue> {
        val issues = mutableListOf<ExtractionIssue>()
        r.subtotal?.let { val d = kotlin.math.abs(it.minorUnits - r.calculatedSubtotal.minorUnits); if (d > 5) issues.add(ExtractionIssue.SubtotalMismatch(it.minorUnits, r.calculatedSubtotal.minorUnits, d)) }
        r.totalAmount?.let { val d = kotlin.math.abs(it.minorUnits - r.calculatedTotal.minorUnits); if (d > 5) issues.add(ExtractionIssue.TotalMismatch(it.minorUnits, r.calculatedTotal.minorUnits, d)) }
        if (r.totalAmount == null && r.items.none { it.type == ReceiptItemType.TOTAL }) issues.add(ExtractionIssue.MissingTotal())
        return issues
    }
}
