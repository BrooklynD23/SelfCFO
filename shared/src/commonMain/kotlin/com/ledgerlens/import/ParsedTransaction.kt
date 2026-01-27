package com.ledgerlens.import

import com.ledgerlens.domain.Money
import kotlinx.datetime.LocalDate

/**
 * Canonical parsed transaction output from any import source (CSV/PDF/etc).
 *
 * This is intentionally UI-agnostic; review/overrides happen in other layers.
 */
data class ParsedTransaction(
    val rowRef: String, // e.g. "row:42" or "page:2,line:15"
    val postedDate: LocalDate,
    val transactionDate: LocalDate?,
    val descriptionRaw: String,
    val amount: Money,
    val balance: Money?,
    val confidence: Float // parse confidence (not categorization confidence)
)

sealed class ParseWarning {
    data class SkippedRow(val rowRef: String, val reason: String) : ParseWarning()
}

sealed class ParseError(message: String) : Exception(message) {
    data object EmptyFile : ParseError("Empty file")
    data class InvalidFormat(val detail: String) : ParseError("Invalid format: $detail")
}

