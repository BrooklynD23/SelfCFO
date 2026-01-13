# 05: CSV Import Pipeline

## Overview

Implement CSV parsing with auto-detection for delimiter, encoding, and column mapping.

---

## Implementation Steps

### Step 1: Define CSV Parser Interface

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/import/CsvParser.kt
package com.ledgerlens.import

interface CsvParser {
    suspend fun parse(
        csvData: ByteArray,
        options: CsvParseOptions = CsvParseOptions()
    ): CsvParseResult
}

data class CsvParseOptions(
    val delimiter: Char? = null,  // null = auto-detect
    val encoding: String? = null,  // null = auto-detect
    val hasHeader: Boolean? = null,  // null = auto-detect
    val dateFormat: String? = null,  // null = auto-detect
    val amountFormat: AmountFormat? = null,  // null = auto-detect
    val currencyCode: String? = null  // Prefer from Account/settings; never hardcode at parse sites
)

enum class AmountFormat {
    SIGNED_SINGLE,     // One column with +/- values
    SEPARATE_COLUMNS,  // Debit and Credit columns
    ABSOLUTE_WITH_TYPE // Amount + transaction type indicator
}

sealed class CsvParseResult {
    data class Success(
        val transactions: List<ParsedTransaction>,
        val detectedOptions: CsvParseOptions,
        val warnings: List<ParseWarning>
    ) : CsvParseResult()

    data class NeedsMapping(
        val headers: List<String>,
        val sampleRows: List<List<String>>,
        val suggestedMapping: ColumnMapping
    ) : CsvParseResult()

    data class Failure(
        val error: ParseError
    ) : CsvParseResult()
}

data class ColumnMapping(
    val dateColumn: Int?,
    val descriptionColumn: Int?,
    val amountColumn: Int?,
    val debitColumn: Int?,
    val creditColumn: Int?,
    val balanceColumn: Int?,
    val categoryColumn: Int?
)
```

### Step 2: Auto-Detection Logic

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/import/CsvAutoDetector.kt
package com.ledgerlens.import

class CsvAutoDetector {

    fun detectEncoding(data: ByteArray): String {
        // Check for BOM
        if (data.size >= 3) {
            if (data[0] == 0xEF.toByte() &&
                data[1] == 0xBB.toByte() &&
                data[2] == 0xBF.toByte()) {
                return "UTF-8"
            }
        }
        if (data.size >= 2) {
            if (data[0] == 0xFF.toByte() && data[1] == 0xFE.toByte()) {
                return "UTF-16LE"
            }
            if (data[0] == 0xFE.toByte() && data[1] == 0xFF.toByte()) {
                return "UTF-16BE"
            }
        }

        // Heuristic: check for high-bit characters
        val text = data.decodeToString()
        return if (text.any { it.code > 127 }) "UTF-8" else "ASCII"
    }

    fun detectDelimiter(lines: List<String>): Char {
        val candidates = listOf(',', '\t', ';', '|')
        val scores = candidates.associateWith { delimiter ->
            scoreDelimiter(lines, delimiter)
        }
        return scores.maxByOrNull { it.value }?.key ?: ','
    }

    private fun scoreDelimiter(lines: List<String>, delimiter: Char): Int {
        if (lines.isEmpty()) return 0

        val counts = lines.take(10).map { line ->
            line.count { it == delimiter }
        }

        // Good delimiter: consistent count across rows
        val variance = counts.distinct().size
        val avgCount = counts.average()

        return if (variance <= 2 && avgCount > 0) {
            (avgCount * 10 / variance).toInt()
        } else {
            0
        }
    }

    fun detectColumnMapping(
        headers: List<String>,
        sampleRows: List<List<String>>
    ): ColumnMapping {
        val dateColumn = findColumnByPattern(headers, sampleRows, ::isDateValue)
        val amountColumn = findColumnByPattern(headers, sampleRows, ::isAmountValue)
        val descriptionColumn = findDescriptionColumn(headers, sampleRows, dateColumn, amountColumn)

        // Check for separate debit/credit columns
        val debitColumn = findColumnByName(headers, listOf("debit", "withdrawal", "dr"))
        val creditColumn = findColumnByName(headers, listOf("credit", "deposit", "cr"))

        return ColumnMapping(
            dateColumn = dateColumn,
            descriptionColumn = descriptionColumn,
            amountColumn = if (debitColumn == null && creditColumn == null) amountColumn else null,
            debitColumn = debitColumn,
            creditColumn = creditColumn,
            balanceColumn = findColumnByName(headers, listOf("balance", "running")),
            categoryColumn = findColumnByName(headers, listOf("category", "type"))
        )
    }

    private fun isDateValue(value: String): Boolean {
        val datePatterns = listOf(
            Regex("""\d{1,2}/\d{1,2}/\d{2,4}"""),
            Regex("""\d{4}-\d{2}-\d{2}"""),
            Regex("""\d{1,2}-\w{3}-\d{2,4}"""),
        )
        return datePatterns.any { it.matches(value.trim()) }
    }

    private fun isAmountValue(value: String): Boolean {
        val cleaned = value.replace(Regex("[,$()\\s]"), "")
        return cleaned.matches(Regex("-?\\d+\\.?\\d*"))
    }

    private fun findColumnByName(
        headers: List<String>,
        keywords: List<String>
    ): Int? {
        return headers.indexOfFirst { header ->
            keywords.any { header.lowercase().contains(it) }
        }.takeIf { it >= 0 }
    }
}
```

### Step 3: CSV Parser Implementation

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/import/CsvParserImpl.kt
package com.ledgerlens.import

class CsvParserImpl : CsvParser {
    private val detector = CsvAutoDetector()
    private val dateParser = FlexibleDateParser()

    override suspend fun parse(
        csvData: ByteArray,
        options: CsvParseOptions
    ): CsvParseResult {
        // Currency should be provided by the ImportOrchestrator based on the selected account.
        val currencyCode = options.currencyCode ?: "USD"

        // Detect encoding
        val encoding = options.encoding ?: detector.detectEncoding(csvData)
        val text = csvData.toString(Charset.forName(encoding))
        val lines = text.lines().filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            return CsvParseResult.Failure(ParseError.EmptyFile)
        }

        // Detect delimiter
        val delimiter = options.delimiter ?: detector.detectDelimiter(lines)

        // Parse rows
        val rows = lines.map { parseCsvLine(it, delimiter) }

        // Detect header
        val hasHeader = options.hasHeader ?: detectHeader(rows)
        val headers = if (hasHeader) rows.first() else generateHeaders(rows.first().size)
        val dataRows = if (hasHeader) rows.drop(1) else rows

        // Detect column mapping
        val mapping = detector.detectColumnMapping(headers, dataRows.take(5))

        // Validate we have minimum required columns
        if (mapping.dateColumn == null) {
            return CsvParseResult.NeedsMapping(headers, dataRows.take(5), mapping)
        }
        if (mapping.amountColumn == null && mapping.debitColumn == null) {
            return CsvParseResult.NeedsMapping(headers, dataRows.take(5), mapping)
        }

        // Parse transactions
        val transactions = dataRows.mapIndexedNotNull { index, row ->
            parseRow(row, mapping, index + 1, currencyCode)
        }

        return CsvParseResult.Success(
            transactions = transactions,
            detectedOptions = options.copy(
                delimiter = delimiter,
                encoding = encoding,
                hasHeader = hasHeader
            ),
            warnings = emptyList()
        )
    }

    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == delimiter && !inQuotes -> {
                    result.add(current.toString().trim())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString().trim())
        return result
    }

    private fun parseRow(
        row: List<String>,
        mapping: ColumnMapping,
        rowNumber: Int,
        currencyCode: String
    ): ParsedTransaction? {
        try {
            val dateStr = mapping.dateColumn?.let { row.getOrNull(it) } ?: return null
            val date = dateParser.parse(dateStr) ?: return null

            val amount = when {
                mapping.amountColumn != null -> {
                    parseAmount(row[mapping.amountColumn], currencyCode)
                }
                mapping.debitColumn != null && mapping.creditColumn != null -> {
                    val debit = parseAmount(row.getOrNull(mapping.debitColumn) ?: "0", currencyCode)
                    val credit = parseAmount(row.getOrNull(mapping.creditColumn) ?: "0", currencyCode)
                    credit - debit
                }
                else -> return null
            }

            val description = mapping.descriptionColumn?.let {
                row.getOrNull(it) ?: ""
            } ?: row.filterIndexed { i, _ ->
                i != mapping.dateColumn &&
                i != mapping.amountColumn &&
                i != mapping.debitColumn &&
                i != mapping.creditColumn
            }.joinToString(" ")

            return ParsedTransaction(
                rowRef = "row:$rowNumber",
                postedDate = date,
                transactionDate = null,
                descriptionRaw = description,
                amount = amount,
                balance = mapping.balanceColumn?.let { row.getOrNull(it) }
                    ?.let { parseAmount(it, currencyCode) },
                confidence = 0.95f
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseAmount(value: String, currencyCode: String): Money {
        val cleaned = value.trim()

        // Parse deterministically (no Double/Float).
        val scale = CurrencyMetadata.getScale(currencyCode)
        val minorUnits = MoneyParser.parseToMinorUnits(
            amountString = cleaned,
            scale = scale,
            roundingMode = RoundingMode.HALF_UP
        )
        return Money.fromMinorUnits(minorUnits, currencyCode)
    }
}
```

### Step 4: Flexible Date Parser

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/import/FlexibleDateParser.kt
package com.ledgerlens.import

class FlexibleDateParser {
    private val patterns = listOf(
        "MM/dd/yyyy",
        "M/d/yyyy",
        "MM/dd/yy",
        "M/d/yy",
        "yyyy-MM-dd",
        "dd-MMM-yyyy",
        "dd/MM/yyyy",
        "MMM dd, yyyy",
        "MMMM dd, yyyy",
    )

    fun parse(dateString: String): LocalDate? {
        val cleaned = dateString.trim()

        for (pattern in patterns) {
            try {
                return LocalDate.parse(cleaned, DateTimeFormatter.ofPattern(pattern))
            } catch (e: Exception) {
                continue
            }
        }

        return null
    }
}
```

---

## Acceptance Criteria

- [ ] Auto-detect delimiter (comma, tab, semicolon, pipe)
- [ ] Auto-detect encoding (UTF-8, UTF-16, ASCII)
- [ ] Auto-detect header row
- [ ] Column mapping heuristics work
- [ ] Handles separate debit/credit columns
- [ ] Parses common date formats
- [ ] Handles amount formats (parentheses for negative, etc.)
- [ ] >99% parse rate on standard bank CSVs

---

## Dependencies

- Kotlinx DateTime

---

## Estimated Complexity

**Medium** - Well-defined with comprehensive heuristics.
