package com.ledgerlens.import

internal class CsvAutoDetector {
    fun detectDelimiter(lines: List<String>): Char {
        val candidates = listOf(',', '\t', ';', '|')
        val scores = candidates.associateWith { delimiter -> scoreDelimiter(lines, delimiter) }
        return scores.maxByOrNull { it.value }?.key ?: ','
    }

    private fun scoreDelimiter(lines: List<String>, delimiter: Char): Int {
        if (lines.isEmpty()) return 0
        val counts = lines.take(10).map { line -> line.count { it == delimiter } }
        val distinctCounts = counts.distinct().size
        val avg = counts.average()
        return if (distinctCounts <= 2 && avg > 0) (avg * 10 / distinctCounts).toInt() else 0
    }

    fun detectHeader(rows: List<List<String>>): Boolean {
        if (rows.isEmpty()) return false
        val first = rows.first()
        // Heuristic: header row has more non-numeric tokens than data rows.
        fun score(row: List<String>): Int =
            row.count { cell ->
                val c = cell.trim()
                c.isNotEmpty() && c.any { it.isLetter() } && c.none { it.isDigit() }
            }
        val firstScore = score(first)
        val nextScore = rows.drop(1).take(3).map { score(it) }.average()
        return firstScore > nextScore
    }

    fun detectColumnMapping(headers: List<String>): ColumnMapping {
        val lowered = headers.map { it.lowercase() }

        fun tokens(header: String): Set<String> =
            header
                .lowercase()
                .split(Regex("""[^a-z0-9]+"""))
                .filter { it.isNotBlank() }
                .toSet()

        fun findByTokens(vararg keys: String): Int? {
            val keySet = keys.toSet()
            return lowered.indexOfFirst { h -> tokens(h).any { it in keySet } }.takeIf { it >= 0 }
        }

        fun findByNames(vararg keys: String): Int? =
            lowered.indexOfFirst { h -> keys.any { k -> h.contains(k) } }.takeIf { it >= 0 }

        val dateColumn = findByNames("date", "posted", "posting")
        val descriptionColumn = findByNames("description", "memo", "details", "merchant", "payee")
        val amountColumn = findByNames("amount", "amt", "value")
        // Debit/credit columns should match whole tokens to avoid false positives
        // (e.g. "description" contains "cr" as substring).
        val debitColumn = findByTokens("debit", "withdrawal", "outflow", "dr")
        val creditColumn = findByTokens("credit", "deposit", "inflow", "cr")
        val balanceColumn = findByNames("balance", "running")

        return ColumnMapping(
            dateColumn = dateColumn,
            descriptionColumn = descriptionColumn,
            amountColumn = if (debitColumn == null && creditColumn == null) amountColumn else null,
            debitColumn = debitColumn,
            creditColumn = creditColumn,
            balanceColumn = balanceColumn
        )
    }
}

