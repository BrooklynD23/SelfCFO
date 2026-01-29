package com.ledgerlens.import

/**
 * CSV parser with light auto-detection and column mapping.
 *
 * Note: Encoding auto-detection is intentionally minimal in commonMain (assumes UTF-8 / BOM).
 */
interface CsvParser {
    suspend fun parse(
        csvData: ByteArray,
        options: CsvParseOptions = CsvParseOptions()
    ): CsvParseResult
}

data class CsvParseOptions(
    val delimiter: Char? = null, // null = auto-detect
    val hasHeader: Boolean? = null, // null = auto-detect
    val currencyCode: String? = null
)

sealed class CsvParseResult {
    data class Success(
        val transactions: List<ParsedTransaction>,
        val detectedOptions: CsvParseOptions,
        val warnings: List<CsvParseWarning>
    ) : CsvParseResult()

    data class NeedsMapping(
        val headers: List<String>,
        val sampleRows: List<List<String>>,
        val suggestedMapping: ColumnMapping
    ) : CsvParseResult()

    data class Failure(
        val error: CsvParseError
    ) : CsvParseResult()
}

data class ColumnMapping(
    val dateColumn: Int?,
    val descriptionColumn: Int?,
    val amountColumn: Int?,
    val debitColumn: Int?,
    val creditColumn: Int?,
    val balanceColumn: Int?
)
