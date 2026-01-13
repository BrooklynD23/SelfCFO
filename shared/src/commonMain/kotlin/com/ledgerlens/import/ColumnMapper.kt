package com.ledgerlens.import

class ColumnMapper {

    fun detectColumnMapping(
        headers: List<String>,
        sampleRows: List<List<String>>
    ): ColumnMapping {
        val dateColumn = findDateColumn(headers, sampleRows)
        val amountColumn = findAmountColumn(headers, sampleRows)
        val debitColumn = findColumnByName(headers, listOf("debit", "withdrawal", "dr", "out"))
        val creditColumn = findColumnByName(headers, listOf("credit", "deposit", "cr", "in"))
        val balanceColumn = findColumnByName(headers, listOf("balance", "running", "total"))
        val categoryColumn = findColumnByName(headers, listOf("category", "type", "memo"))

        val usedColumns = setOfNotNull(dateColumn, amountColumn, debitColumn, creditColumn, balanceColumn, categoryColumn)
        val descriptionColumn = findDescriptionColumn(headers, sampleRows, usedColumns)

        return ColumnMapping(
            dateColumn = dateColumn,
            descriptionColumn = descriptionColumn,
            amountColumn = if (debitColumn == null && creditColumn == null) amountColumn else null,
            debitColumn = debitColumn,
            creditColumn = creditColumn,
            balanceColumn = balanceColumn,
            categoryColumn = categoryColumn
        )
    }

    private fun findDateColumn(headers: List<String>, sampleRows: List<List<String>>): Int? {
        val byName = findColumnByName(headers, listOf("date", "posted", "transaction", "trans"))
        if (byName != null && validateDateColumn(sampleRows, byName)) {
            return byName
        }

        return findColumnByPattern(sampleRows, ::isDateValue)
    }

    private fun findAmountColumn(headers: List<String>, sampleRows: List<List<String>>): Int? {
        val byName = findColumnByName(headers, listOf("amount", "value", "sum", "total"))
        if (byName != null && validateAmountColumn(sampleRows, byName)) {
            return byName
        }

        return findColumnByPattern(sampleRows, ::isAmountValue)
    }

    private fun findDescriptionColumn(
        headers: List<String>,
        sampleRows: List<List<String>>,
        usedColumns: Set<Int>
    ): Int? {
        val byName = findColumnByName(headers, listOf("description", "desc", "memo", "payee", "narrative", "details"))
        if (byName != null && byName !in usedColumns) {
            return byName
        }

        return headers.indices.firstOrNull { idx ->
            idx !in usedColumns && sampleRows.any { row ->
                row.getOrNull(idx)?.length ?: 0 > 10
            }
        }
    }

    private fun findColumnByName(headers: List<String>, keywords: List<String>): Int? {
        return headers.indexOfFirst { header ->
            keywords.any { keyword -> header.lowercase().contains(keyword) }
        }.takeIf { it >= 0 }
    }

    private fun findColumnByPattern(
        sampleRows: List<List<String>>,
        predicate: (String) -> Boolean
    ): Int? {
        if (sampleRows.isEmpty()) return null
        val columnCount = sampleRows.maxOfOrNull { it.size } ?: 0

        for (col in 0 until columnCount) {
            val matchCount = sampleRows.count { row ->
                row.getOrNull(col)?.let { predicate(it) } == true
            }
            if (matchCount >= sampleRows.size * 0.8) {
                return col
            }
        }
        return null
    }

    private fun validateDateColumn(sampleRows: List<List<String>>, column: Int): Boolean {
        return sampleRows.count { row ->
            row.getOrNull(column)?.let { isDateValue(it) } == true
        } >= sampleRows.size * 0.8
    }

    private fun validateAmountColumn(sampleRows: List<List<String>>, column: Int): Boolean {
        return sampleRows.count { row ->
            row.getOrNull(column)?.let { isAmountValue(it) } == true
        } >= sampleRows.size * 0.8
    }

    internal fun isDateValue(value: String): Boolean {
        val trimmed = value.trim()
        val datePatterns = listOf(
            Regex("""\d{1,2}/\d{1,2}/\d{2,4}"""),
            Regex("""\d{4}-\d{2}-\d{2}"""),
            Regex("""\d{1,2}-\w{3}-\d{2,4}"""),
            Regex("""\d{1,2}\.\d{1,2}\.\d{2,4}"""),
            Regex("""\w{3,9}\s+\d{1,2},?\s+\d{2,4}""")
        )
        return datePatterns.any { it.matches(trimmed) }
    }

    internal fun isAmountValue(value: String): Boolean {
        val cleaned = value.replace(Regex("[,$()\\s]"), "").trim()
        return cleaned.matches(Regex("-?\\d+\\.?\\d*")) && cleaned.isNotEmpty()
    }
}
