package com.ledgerlens.import

import com.ledgerlens.domain.CurrencyMetadata
import com.ledgerlens.domain.Money
import com.ledgerlens.domain.MoneyParser
import com.ledgerlens.domain.RoundingMode

class CsvParserImpl : CsvParser {
    private val detector = CsvAutoDetector()
    private val dateParser = FlexibleDateParser()

    override suspend fun parse(csvData: ByteArray, options: CsvParseOptions): CsvParseResult {
        val currencyCode = options.currencyCode ?: "USD"

        val text = csvData.decodeUtf8Lenient()
        val lines = text
            .lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .toList()

        if (lines.isEmpty()) return CsvParseResult.Failure(CsvParseError.EmptyFile)

        val delimiter = options.delimiter ?: detector.detectDelimiter(lines)
        val rows = lines.map { parseCsvLine(it, delimiter) }

        val hasHeader = options.hasHeader ?: detector.detectHeader(rows)
        val headers = if (hasHeader) rows.first() else generateHeaders(rows.first().size)
        val dataRows = if (hasHeader) rows.drop(1) else rows

        val mapping = detector.detectColumnMapping(headers)
        if (mapping.dateColumn == null) {
            return CsvParseResult.NeedsMapping(headers, dataRows.take(5), mapping)
        }
        if (mapping.amountColumn == null && mapping.debitColumn == null && mapping.creditColumn == null) {
            return CsvParseResult.NeedsMapping(headers, dataRows.take(5), mapping)
        }

        val warnings = mutableListOf<CsvParseWarning>()
        val transactions = dataRows.mapIndexedNotNull { index, row ->
            val rowRef = "row:${index + 1 + if (hasHeader) 1 else 0}"
            parseRow(row, mapping, rowRef, currencyCode, warnings)
        }

        return CsvParseResult.Success(
            transactions = transactions,
            detectedOptions = options.copy(delimiter = delimiter, hasHeader = hasHeader, currencyCode = currencyCode),
            warnings = warnings
        )
    }

    private fun parseRow(
        row: List<String>,
        mapping: ColumnMapping,
        rowRef: String,
        currencyCode: String,
        warnings: MutableList<CsvParseWarning>
    ): ParsedTransaction? {
        try {
            val dateStr = mapping.dateColumn?.let { row.getOrNull(it) }?.trim().orEmpty()
            val postedDate = dateParser.parse(dateStr)
            if (postedDate == null) {
                warnings += CsvParseWarning.SkippedRow(rowRef, "Unparseable date: '$dateStr'")
                return null
            }

            val amount = when {
                mapping.amountColumn != null -> parseAmount(row.getOrNull(mapping.amountColumn).orEmpty(), currencyCode)
                mapping.debitColumn != null || mapping.creditColumn != null -> {
                    val debit = parseAmount(row.getOrNull(mapping.debitColumn ?: -1).orEmpty(), currencyCode)
                    val credit = parseAmount(row.getOrNull(mapping.creditColumn ?: -1).orEmpty(), currencyCode)
                    Money.fromMinorUnits(credit.minorUnits - debit.minorUnits, currencyCode)
                }
                else -> {
                    warnings += CsvParseWarning.SkippedRow(rowRef, "Missing amount columns")
                    return null
                }
            }

            val description = mapping.descriptionColumn?.let { idx ->
                row.getOrNull(idx)?.trim().orEmpty()
            }.takeIf { !it.isNullOrBlank() } ?: row.joinToString(" ") { it.trim() }.trim()

            val balance = mapping.balanceColumn?.let { idx ->
                row.getOrNull(idx)?.trim()?.takeIf { it.isNotBlank() }?.let { parseAmount(it, currencyCode) }
            }

            return ParsedTransaction(
                rowRef = rowRef,
                postedDate = postedDate,
                transactionDate = null,
                descriptionRaw = description,
                amount = amount,
                balance = balance,
                confidence = 0.95f
            )
        } catch (e: Exception) {
            warnings += CsvParseWarning.SkippedRow(rowRef, "Parse error: ${e.message ?: "unknown"}")
            return null
        }
    }

    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    // Handle escaped quotes ("")
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i += 1
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(c)
            }
            i += 1
        }

        result.add(current.toString().trim())
        return result
    }

    private fun generateHeaders(count: Int): List<String> = List(count) { i -> "col_${i + 1}" }

    private fun parseAmount(value: String, currencyCode: String): Money {
        val cleaned = value.trim()
        if (cleaned.isBlank()) return Money.zero(currencyCode)

        val scale = CurrencyMetadata.getScale(currencyCode)
        val minorUnits = MoneyParser.parseToMinorUnits(
            amountString = cleaned,
            scale = scale,
            roundingMode = RoundingMode.HALF_UP
        )
        return Money.fromMinorUnits(minorUnits, currencyCode)
    }
}

private fun ByteArray.decodeUtf8Lenient(): String {
    // Remove UTF-8 BOM if present.
    return if (size >= 3 && this[0] == 0xEF.toByte() && this[1] == 0xBB.toByte() && this[2] == 0xBF.toByte()) {
        copyOfRange(3, size).decodeToString()
    } else {
        decodeToString()
    }
}

