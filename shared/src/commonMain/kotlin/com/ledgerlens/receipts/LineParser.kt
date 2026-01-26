package com.ledgerlens.receipts

import com.ledgerlens.domain.CurrencyMetadata
import com.ledgerlens.domain.Money
import com.ledgerlens.domain.MoneyParser

/**
 * Parses individual receipt lines into item candidates.
 */
object LineParser {

    sealed class ParseResult {
        data class Item(
            val name: String,
            val quantity: Double,
            val unitPrice: Money?,
            val totalPrice: Money,
            val type: ReceiptItemType,
            val confidence: Double
        ) : ParseResult()
        data class PartialItem(val name: String?, val price: Money?, val possibleType: ReceiptItemType?) : ParseResult()
        object Empty : ParseResult()
        object Unparseable : ParseResult()
    }

    private val QUANTITY_AT_PRICE = Regex("""(\d+(?:\.\d+)?)\s*[@xX×]\s*\$?(\d+(?:\.\d{1,2})?)""")
    private val NAME_PRICE = Regex("""^(.+?)\s{2,}([−\-]?\$?\d+(?:\.\d{1,2})?)$""")
    private val PRICE_NAME = Regex("""^([−\-]?\$?\d+(?:\.\d{1,2})?)\s{2,}(.+?)$""")
    private val FULL_LINE_ITEM =
        Regex("""^(\d+(?:\.\d+)?)\s+(.+?)\s*[@xX×]\s*\$?(\d+(?:\.\d{1,2})?)\s*[=]?\s*\$?(\d+(?:\.\d{1,2})?)$""")
    private val PRICE_ONLY = Regex("""^\s*[−\-]?\$?\d+(?:\.\d{1,2})?\s*$""")
    private val PRICE_PATTERN = Regex("""[−\-]?\$?(\d{1,3}(?:,\d{3})*|\d+)(?:\.(\d{1,2}))?""")

    private fun parsePrice(priceStr: String, currency: String): Money? {
        val cleaned = priceStr.trim().replace("$", "").replace("−", "-").replace(",", "")
        val negative = cleaned.startsWith("-") || cleaned.startsWith("(")
        val digits = cleaned.replace("-", "").replace("(", "").replace(")", "")
        return try {
            val scale = CurrencyMetadata.getScale(currency)
            val minorUnits = MoneyParser.parseToMinorUnits(digits, scale)
            Money.fromMinorUnits(if (negative) -minorUnits else minorUnits, currency)
        } catch (e: Exception) {
            null
        }
    }

    private fun extractPrices(text: String, currency: String): List<Money> =
        PRICE_PATTERN.findAll(text).mapNotNull { parsePrice(it.value, currency) }.toList()

    fun parseLine(line: String, lineNumber: Int? = null, currency: String = "USD"): ParseResult {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.length < 2 || trimmed.all {
                it == '-' || it == '=' || it == '*' || it == '_'
            }
        ) {
            return ParseResult.Empty
        }

        FULL_LINE_ITEM.find(trimmed)?.let { match ->
            val (qty, name, unitPrice, totalPrice) = match.destructured
            val total = parsePrice(totalPrice, currency) ?: return@let
            return ParseResult.Item(name.trim(), qty.toDoubleOrNull() ?: 1.0, parsePrice(unitPrice, currency), total, ReceiptItemType.fromDescription(name), 0.95)
        }

        QUANTITY_AT_PRICE.find(trimmed)?.let { match ->
            val qty = match.groupValues[1].toDoubleOrNull() ?: 1.0
            val unitPrice = parsePrice(match.groupValues[2], currency) ?: return@let
            val name = trimmed.substring(0, match.range.first).trim().ifEmpty { "Item" }
            val totalPrice = Money.fromMinorUnits((unitPrice.minorUnits * qty).toLong(), currency)
            return ParseResult.Item(name, qty, unitPrice, totalPrice, ReceiptItemType.fromDescription(name), 0.85)
        }

        NAME_PRICE.find(trimmed)?.let { match ->
            val (name, priceStr) = match.destructured
            val price = parsePrice(priceStr, currency) ?: return@let
            val itemType = ReceiptItemType.fromDescription(name)
            return ParseResult.Item(name.trim(), 1.0, null, price, itemType, if (itemType.isAggregate) 0.9 else 0.8)
        }

        PRICE_NAME.find(trimmed)?.let { match ->
            val (priceStr, name) = match.destructured
            val price = parsePrice(priceStr, currency) ?: return@let
            return ParseResult.Item(name.trim(), 1.0, null, price, ReceiptItemType.fromDescription(name), 0.75)
        }

        if (PRICE_ONLY.matches(trimmed)) {
            return parsePrice(trimmed, currency)?.let { ParseResult.PartialItem(null, it, null) } ?: ParseResult.Unparseable
        }

        val prices = extractPrices(trimmed, currency)
        if (prices.isNotEmpty()) {
            val price = prices.last()
            val nameCandidate = PRICE_PATTERN.replace(trimmed, "").replace(Regex("""\s{2,}"""), " ").trim()
            if (nameCandidate.length >= 2) {
                return ParseResult.Item(nameCandidate, 1.0, null, price, ReceiptItemType.fromDescription(nameCandidate), 0.6)
            }
            return ParseResult.PartialItem(null, price, null)
        }

        val possibleType = ReceiptItemType.fromDescription(trimmed)
        return if (possibleType != ReceiptItemType.PRODUCT) ParseResult.PartialItem(trimmed, null, possibleType) else ParseResult.Unparseable
    }

    fun parseLines(lines: List<String>, currency: String = "USD"): List<ReceiptItem> {
        val results = mutableListOf<ReceiptItem>()
        var pendingName: String? = null

        for ((index, line) in lines.withIndex()) {
            val lineNumber = index + 1
            when (val result = parseLine(line, lineNumber, currency)) {
                is ParseResult.Item -> {
                    val finalName = if (result.name.isEmpty() && pendingName != null) pendingName else result.name
                    pendingName = null
                    results.add(ReceiptItem(finalName, result.quantity, result.unitPrice, result.totalPrice, result.type, null, result.confidence, line, lineNumber))
                }
                is ParseResult.PartialItem -> {
                    if (result.name != null && result.price == null) {
                        pendingName = result.name
                    } else if (result.price != null && pendingName != null) {
                        results.add(ReceiptItem(pendingName, 1.0, null, result.price, result.possibleType ?: ReceiptItemType.PRODUCT, null, 0.5, line, lineNumber))
                        pendingName = null
                    }
                }
                ParseResult.Empty, ParseResult.Unparseable -> pendingName = null
            }
        }
        return results
    }
}
