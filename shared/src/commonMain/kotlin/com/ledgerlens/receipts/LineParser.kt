package com.ledgerlens.receipts

import com.ledgerlens.domain.Money
import com.ledgerlens.domain.MoneyParser
import com.ledgerlens.domain.CurrencyMetadata

object LineParser {
    sealed class ParseResult {
        data class Item(val name: String, val quantity: Double, val unitPrice: Money?, val totalPrice: Money, val type: ReceiptItemType, val confidence: Double) : ParseResult()
        data class PartialItem(val name: String?, val price: Money?, val possibleType: ReceiptItemType?) : ParseResult()
        object Empty : ParseResult()
        object Unparseable : ParseResult()
    }

    private val QTY_AT_PRICE = Regex("""(\d+(?:\.\d+)?)\s*[@xX×]\s*\$?(\d+(?:\.\d{1,2})?)""")
    private val NAME_PRICE = Regex("""^(.+?)\s{2,}([−\-]?\$?\d+(?:\.\d{1,2})?)$""")
    private val PRICE_NAME = Regex("""^([−\-]?\$?\d+(?:\.\d{1,2})?)\s{2,}(.+?)$""")
    private val FULL_ITEM = Regex("""^(\d+(?:\.\d+)?)\s+(.+?)\s*[@xX×]\s*\$?(\d+(?:\.\d{1,2})?)\s*[=]?\s*\$?(\d+(?:\.\d{1,2})?)$""")
    private val PRICE_ONLY = Regex("""^\s*[−\-]?\$?\d+(?:\.\d{1,2})?\s*$""")
    private val PRICE_PAT = Regex("""[−\-]?\$?(\d{1,3}(?:,\d{3})*|\d+)(?:\.(\d{1,2}))?""")

    private fun parsePrice(s: String, cur: String): Money? {
        val c = s.trim().replace("$", "").replace("−", "-").replace(",", "")
        val neg = c.startsWith("-") || c.startsWith("(")
        val d = c.replace("-", "").replace("(", "").replace(")", "")
        return try { val m = MoneyParser.parseToMinorUnits(d, CurrencyMetadata.getScale(cur)); Money.fromMinorUnits(if (neg) -m else m, cur) } catch (e: Exception) { null }
    }

    private fun extractPrices(t: String, c: String) = PRICE_PAT.findAll(t).mapNotNull { parsePrice(it.value, c) }.toList()

    fun parseLine(line: String, lineNumber: Int? = null, currency: String = "USD"): ParseResult {
        val t = line.trim()
        if (t.isEmpty() || t.length < 2 || t.all { it == '-' || it == '=' || it == '*' || it == '_' }) return ParseResult.Empty

        FULL_ITEM.find(t)?.let { m -> val (q, n, u, p) = m.destructured; parsePrice(p, currency)?.let { return ParseResult.Item(n.trim(), q.toDoubleOrNull() ?: 1.0, parsePrice(u, currency), it, ReceiptItemType.fromDescription(n), 0.95) } }
        QTY_AT_PRICE.find(t)?.let { m -> val q = m.groupValues[1].toDoubleOrNull() ?: 1.0; parsePrice(m.groupValues[2], currency)?.let { u -> val n = t.substring(0, m.range.first).trim().ifEmpty { "Item" }; return ParseResult.Item(n, q, u, Money.fromMinorUnits((u.minorUnits * q).toLong(), currency), ReceiptItemType.fromDescription(n), 0.85) } }
        NAME_PRICE.find(t)?.let { m -> val (n, p) = m.destructured; parsePrice(p, currency)?.let { val ty = ReceiptItemType.fromDescription(n); return ParseResult.Item(n.trim(), 1.0, null, it, ty, if (ty.isAggregate) 0.9 else 0.8) } }
        PRICE_NAME.find(t)?.let { m -> val (p, n) = m.destructured; parsePrice(p, currency)?.let { return ParseResult.Item(n.trim(), 1.0, null, it, ReceiptItemType.fromDescription(n), 0.75) } }
        if (PRICE_ONLY.matches(t)) return parsePrice(t, currency)?.let { ParseResult.PartialItem(null, it, null) } ?: ParseResult.Unparseable

        val prices = extractPrices(t, currency)
        if (prices.isNotEmpty()) { val p = prices.last(); val n = PRICE_PAT.replace(t, "").replace(Regex("""\s{2,}"""), " ").trim(); if (n.length >= 2) return ParseResult.Item(n, 1.0, null, p, ReceiptItemType.fromDescription(n), 0.6); return ParseResult.PartialItem(null, p, null) }
        val ty = ReceiptItemType.fromDescription(t); return if (ty != ReceiptItemType.PRODUCT) ParseResult.PartialItem(t, null, ty) else ParseResult.Unparseable
    }

    fun parseLines(lines: List<String>, currency: String = "USD"): List<ReceiptItem> {
        val r = mutableListOf<ReceiptItem>(); var pending: String? = null
        for ((i, line) in lines.withIndex()) { val ln = i + 1
            when (val res = parseLine(line, ln, currency)) {
                is ParseResult.Item -> { val n = if (res.name.isEmpty() && pending != null) pending else res.name; pending = null; r.add(ReceiptItem(n, res.quantity, res.unitPrice, res.totalPrice, res.type, null, res.confidence, line, ln)) }
                is ParseResult.PartialItem -> { if (res.name != null && res.price == null) pending = res.name else if (res.price != null && pending != null) { r.add(ReceiptItem(pending, 1.0, null, res.price, res.possibleType ?: ReceiptItemType.PRODUCT, null, 0.5, line, ln)); pending = null } }
                ParseResult.Empty, ParseResult.Unparseable -> pending = null
            }
        }
        return r
    }
}
