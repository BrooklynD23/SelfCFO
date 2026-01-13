package com.ledgerlens.import

import com.ledgerlens.domain.CurrencyMetadata
import com.ledgerlens.domain.Money
import com.ledgerlens.domain.MoneyParser
import com.ledgerlens.domain.RoundingMode

class CsvParserImpl : CsvParser {
    private val detector = CsvAutoDetector()
    private val columnMapper = ColumnMapper()
    private val dateParser = FlexibleDateParser()

    override suspend fun parse(
        csvData: ByteArray,
        options: CsvParseOptions
    ): CsvParseResult {
        if (csvData.isEmpty()) {
            return CsvParseResult.Failure(ParseError.EmptyFile)
        }

        val currencyCode = options.currencyCode ?: "USD"

        val encoding = options.encoding ?: detector.detectEncoding(csvData)
        val textData = detector.stripBom(csvData, encoding)
        val text = textData.decodeToString()
        val lines = text.lines().filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            return CsvParseResult.Failure(ParseError.EmptyFile)
        }

        val delimiter = options.delimiter ?: detector.detectDelimiter(lines)
        val rows = lines.map { parseCsvLine(it, delimiter) }

        val hasHeader = options.hasHeader ?: detector.detectHasHeader(rows)
        val headers = if (hasHeader) rows.first() else generateHeaders(rows.first().size)
        val dataRows = if (hasHeader) rows.drop(1) else rows

        if (dataRows.isEmpty()) {
            return CsvParseResult.Failure(ParseError.EmptyFile)
        }

        val mapping = columnMapper.detectColumnMapping(headers, dataRows.take(5))

        if (mapping.dateColumn == null) {
            return CsvParseResult.NeedsMapping(headers, dataRows.take(5), mapping)
        }
        if (mapping.amountColumn == null && mapping.debitColumn == null && mapping.creditColumn == null) {
            return CsvParseResult.NeedsMapping(headers, dataRows.take(5), mapping)
        }

        val warnings = mutableListOf<ParseWarning>()
        val transactions = dataRows.mapIndexedNotNull { index, row ->
            val rowNumber = if (hasHeader) index + 2 else index + 1
            parseRow(row, mapping, rowNumber, currencyCode, warnings)
        }

        return CsvParseResult.Success(
            transactions = transactions,
            detectedOptions = options.copy(
                delimiter = delimiter,
                encoding = encoding,
                hasHeader = hasHeader
            ),
            warnings = warnings
        )
    }

    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var prevWasQuote = false

        for (char in line) {
            when {
                char == '"' && !inQuotes -> {
                    inQuotes = true
                    prevWasQuote = false
                }
                char == '"' && inQuotes -> {
                    if (prevWasQuote) {
                        current.append('"')
                        prevWasQuote = false
                    } else {
                        prevWasQuote = true
                    }
                }
                char == delimiter && !inQuotes -> {
                    result.add(current.toString().trim())
                    current = StringBuilder()
                    prevWasQuote = false
                }
                prevWasQuote -> {
                    inQuotes = false
                    prevWasQuote = false
                    if (char != delimiter) {
                        current.append(char)
                    } else {
                        result.add(current.toString().trim())
                        current = StringBuilder()
                    }
                }
                else -> current.append(char)
            }
        }

        if (prevWasQuote) inQuotes = false
        result.add(current.toString().trim())
        return result
    }

    private fun generateHeaders(size: Int): List<String> {
        return (1..size).map { "Column$it" }
    }

    private fun parseRow(
        row: List<String>,
        mapping: ColumnMapping,
        rowNumber: Int,
        currencyCode: String,
        warnings: MutableList<ParseWarning>
    ): ParsedTransaction? {
        try {
            val dateStr = mapping.dateColumn?.let { row.getOrNull(it) }?.takeIf { it.isNotBlank() }
            if (dateStr == null) {
                warnings.add(ParseWarning(rowNumber, "Missing date value", "date"))
                return null
            }

            val date = dateParser.parse(dateStr)
            if (date == null) {
                warnings.add(ParseWarning(rowNumber, "Could not parse date: $dateStr", "date"))
                return null
            }

            val amount = when {
                mapping.amountColumn != null -> {
                    val amountStr = row.getOrNull(mapping.amountColumn) ?: ""
                    if (amountStr.isBlank()) {
                        warnings.add(ParseWarning(rowNumber, "Missing amount value", "amount"))
                        return null
                    }
                    parseAmount(amountStr, currencyCode)
                }
                mapping.debitColumn != null || mapping.creditColumn != null -> {
                    val debitStr = mapping.debitColumn?.let { row.getOrNull(it) } ?: ""
                    val creditStr = mapping.creditColumn?.let { row.getOrNull(it) } ?: ""

                    val debit = if (debitStr.isNotBlank()) parseAmount(debitStr, currencyCode) else Money.zero(currencyCode)
                    val credit = if (creditStr.isNotBlank()) parseAmount(creditStr, currencyCode) else Money.zero(currencyCode)
                    credit - debit
                }
                else -> {
                    warnings.add(ParseWarning(rowNumber, "No amount column found", "amount"))
                    return null
                }
            }

            val description = mapping.descriptionColumn?.let { row.getOrNull(it) }?.trim()
                ?: row.filterIndexed { i, _ ->
                    i != mapping.dateColumn &&
                    i != mapping.amountColumn &&
                    i != mapping.debitColumn &&
                    i != mapping.creditColumn &&
                    i != mapping.balanceColumn
                }.joinToString(" ").trim()

            val balance = mapping.balanceColumn?.let { row.getOrNull(it) }
                ?.takeIf { it.isNotBlank() }
                ?.let { parseAmountOrNull(it, currencyCode) }

            return ParsedTransaction(
                rowRef = "row:$rowNumber",
                postedDate = date,
                transactionDate = null,
                descriptionRaw = description,
                amount = amount,
                balance = balance,
                confidence = 0.95f
            )
        } catch (e: Exception) {
            warnings.add(ParseWarning(rowNumber, "Parse error: ${e.message}", null))
            return null
        }
    }

    private fun parseAmount(value: String, currencyCode: String): Money {
        val scale = CurrencyMetadata.getScale(currencyCode)
        val minorUnits = MoneyParser.parseToMinorUnits(
            amountString = value,
            scale = scale,
            roundingMode = RoundingMode.HALF_UP
        )
        return Money.fromMinorUnits(minorUnits, currencyCode)
    }

    private fun parseAmountOrNull(value: String, currencyCode: String): Money? {
        return try {
            parseAmount(value, currencyCode)
        } catch (e: Exception) {
            null
        }
    }
}
