package com.ledgerlens.receipts

object ReceiptStructureDetector {
    private val HEADER = listOf(Regex("""(?i)welcome|thank you for"""), Regex("""(?i)store\s*#"""), Regex("""(?i)address|phone|tel"""), Regex("""(?i)receipt|invoice"""), Regex("""\d{1,2}[/\-]\d{1,2}[/\-]\d{2,4}"""), Regex("""\d{1,2}:\d{2}""", RegexOption.IGNORE_CASE))
    private val TOTALS = listOf(Regex("""(?i)^[\s]*sub\s*-?\s*total"""), Regex("""(?i)^[\s]*total(?!\s*savings)"""), Regex("""(?i)^[\s]*tax"""), Regex("""(?i)^[\s]*tip|gratuity"""), Regex("""(?i)^[\s]*balance|amount\s*due"""), Regex("""(?i)^[\s]*cash|credit|debit|visa|mastercard"""))
    private val FOOTER = listOf(Regex("""(?i)thank\s*you"""), Regex("""(?i)please\s*come\s*again"""), Regex("""(?i)survey|feedback"""), Regex("""(?i)www\.|http"""), Regex("""[*]{3,}"""))
    private val PRICE = Regex("""[−\-]?\$?(\d{1,3}(?:,\d{3})*|\d+)(?:\.(\d{1,2}))?""")

    enum class LineType { HEADER, ITEM, TOTALS, FOOTER, SEPARATOR, UNKNOWN }

    fun classifyLine(line: String, currency: String = "USD"): LineType {
        val t = line.trim()
        if (t.isEmpty() || t.all { it == '-' || it == '=' || it == '*' }) return LineType.SEPARATOR
        if (TOTALS.any { it.containsMatchIn(t) }) return LineType.TOTALS
        if (FOOTER.any { it.containsMatchIn(t) }) return LineType.FOOTER
        if (HEADER.any { it.containsMatchIn(t) }) return LineType.HEADER
        if (PRICE.containsMatchIn(t)) { val l = t.lowercase(); return if (l.contains("total") || l.contains("tax") || l.contains("subtotal")) LineType.TOTALS else LineType.ITEM }
        return LineType.UNKNOWN
    }

    fun detectStructure(lines: List<String>, currency: String = "USD"): ReceiptStructure {
        if (lines.isEmpty()) return ReceiptStructure(null, null, null, null, 0)
        val cl = lines.mapIndexed { i, l -> i to classifyLine(l, currency) }
        var hEnd = -1; var iStart = -1; var iEnd = -1; var tStart = -1; var tEnd = -1; var fStart = lines.size; var cur = LineType.HEADER; var lastItem = -1
        for ((i, ty) in cl) { when {
            ty == LineType.ITEM && cur == LineType.HEADER -> { hEnd = i - 1; iStart = i; cur = LineType.ITEM; lastItem = i }
            ty == LineType.ITEM && cur == LineType.ITEM -> lastItem = i
            ty == LineType.TOTALS && cur == LineType.ITEM -> { iEnd = lastItem; tStart = i; cur = LineType.TOTALS }
            ty == LineType.TOTALS && cur == LineType.TOTALS -> tEnd = i
            ty == LineType.FOOTER && cur == LineType.TOTALS -> { if (tEnd < 0) tEnd = i - 1; fStart = i; cur = LineType.FOOTER }
            ty == LineType.FOOTER && cur != LineType.FOOTER -> { fStart = i; cur = LineType.FOOTER }
        } }
        if (iStart < 0) { val p = cl.filter { it.second == LineType.ITEM || it.second == LineType.TOTALS }; if (p.isNotEmpty()) { iStart = p.first().first; iEnd = p.last().first } }
        if (hEnd < 0) hEnd = maxOf(0, iStart - 1); if (iEnd < 0) iEnd = lastItem.takeIf { it >= 0 } ?: (tStart - 1).takeIf { it >= 0 } ?: (lines.size - 1); if (tEnd < 0 && tStart >= 0) tEnd = minOf(fStart - 1, lines.size - 1)
        return ReceiptStructure(if (hEnd >= 0) 0..hEnd else null, if (iStart >= 0 && iEnd >= iStart) iStart..iEnd else null, if (tStart >= 0 && tEnd >= tStart) tStart..tEnd else null, if (fStart < lines.size) fStart until lines.size else null, lines.size)
    }

    fun extractMerchant(lines: List<String>, s: ReceiptStructure): String? { val h = s.headerLines?.let { lines.slice(it) } ?: return null; for (l in h) { val t = l.trim(); if (t.length >= 3 && !HEADER.any { it.containsMatchIn(t) } && !t.all { it.isDigit() || it == '-' || it == '/' }) return t }; return h.firstOrNull { it.trim().isNotEmpty() }?.trim() }
    fun extractDate(lines: List<String>): String? { val p = Regex("""(\d{1,2})[/\-](\d{1,2})[/\-](\d{2,4})"""); for (l in lines) { p.find(l)?.let { val (a, b, y) = it.destructured; return "${if (y.length == 2) "20$y" else y}-${a.padStart(2, '0')}-${b.padStart(2, '0')}" } }; return null }
    fun extractTime(lines: List<String>): String? { val p = Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?\s*(AM|PM)?""", RegexOption.IGNORE_CASE); for (l in lines) { p.find(l)?.let { var h = it.groupValues[1].toIntOrNull() ?: return@let; val m = it.groupValues[2]; val ap = it.groupValues[4].uppercase(); if (ap == "PM" && h < 12) h += 12; if (ap == "AM" && h == 12) h = 0; return "${h.toString().padStart(2, '0')}:$m" } }; return null }
}
