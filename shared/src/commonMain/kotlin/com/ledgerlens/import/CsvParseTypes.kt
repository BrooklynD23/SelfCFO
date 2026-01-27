package com.ledgerlens.import

sealed class CsvParseWarning {
    data class SkippedRow(val rowRef: String, val reason: String) : CsvParseWarning()
}

sealed class CsvParseError(message: String) : Exception(message) {
    data object EmptyFile : CsvParseError("Empty file")
    data class InvalidFormat(val detail: String) : CsvParseError("Invalid format: $detail")
}

