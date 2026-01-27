package com.ledgerlens.import

import com.ledgerlens.domain.Money
import kotlinx.datetime.LocalDate

interface CsvParser {
    suspend fun parse(
        csvData: ByteArray,
        options: CsvParseOptions = CsvParseOptions()
    ): CsvParseResult
}

data class CsvParseOptions(
    val delimiter: Char? = null,
    val encoding: String? = null,
    val hasHeader: Boolean? = null,
    val dateFormat: String? = null,
    val amountFormat: AmountFormat? = null,
    val currencyCode: String? = null
)

enum class AmountFormat {
    SIGNED_SINGLE,
    SEPARATE_COLUMNS,
    ABSOLUTE_WITH_TYPE
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

    data class Failure(val error: ParseError) : CsvParseResult()
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

data class ParsedTransaction(
    val rowRef: String,
    val postedDate: LocalDate,
    val transactionDate: LocalDate?,
    val descriptionRaw: String,
    val amount: Money,
    val balance: Money?,
    val confidence: Float
)

data class ParseWarning(
    val rowNumber: Int,
    val message: String,
    val field: String?
)

sealed class ParseError {
    object EmptyFile : ParseError()
    data class InvalidEncoding(val encoding: String) : ParseError()
    data class NoDateColumn(val headers: List<String>) : ParseError()
    data class NoAmountColumn(val headers: List<String>) : ParseError()
    data class MalformedRow(val rowNumber: Int, val reason: String) : ParseError()
    data class Unknown(val message: String) : ParseError()
}
