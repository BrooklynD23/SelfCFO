package com.ledgerlens.money

/**
 * Deterministic parser for monetary amounts.
 * Avoids floating-point by parsing integer and fractional parts separately.
 */
object MoneyParser {

    private val CURRENCY_SYMBOLS = mapOf(
        "$" to "USD",
        "€" to "EUR",
        "£" to "GBP",
        "¥" to "JPY",
        "₹" to "INR",
        "₩" to "KRW",
        "₽" to "RUB",
        "₺" to "TRY",
        "฿" to "THB",
        "₫" to "VND",
        "₱" to "PHP",
        "₪" to "ILS"
    )

    private val PRICE_PATTERN = Regex(
        """([−\-])?[\s]*([£€$¥₹₩₽₺฿₫₱₪])?[\s]*([−\-])?[\s]*(\d{1,3}(?:[,\s]?\d{3})*|\d+)(?:[.,](\d{1,3}))?[\s]*([A-Z]{3})?"""
    )

    /**
     * Parse a string into Money.
     * Handles formats like: "$12.34", "12.34", "€ 1,234.56", "1234", "-$5.00"
     *
     * @param input The string to parse
     * @param defaultCurrency Currency code to use if none detected
     * @return Parsed Money or null if parsing fails
     */
    fun parse(input: String, defaultCurrency: String = "USD"): Money? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val match = PRICE_PATTERN.find(trimmed) ?: return null

        val (negPrefix, symbol, negAfterSymbol, wholePart, fractionalPart, currencyCode) = match.destructured

        val isNegative = negPrefix.isNotEmpty() || negAfterSymbol.isNotEmpty() ||
            trimmed.startsWith("(") && trimmed.endsWith(")")

        val currency = when {
            currencyCode.isNotEmpty() -> currencyCode
            symbol.isNotEmpty() -> CURRENCY_SYMBOLS[symbol] ?: defaultCurrency
            else -> defaultCurrency
        }

        val wholeDigits = wholePart.replace(",", "").replace(" ", "")
        val whole = wholeDigits.toLongOrNull() ?: return null

        val scale = CurrencyMetadata.getScale(currency)
        val fractional = if (fractionalPart.isNotEmpty()) {
            val padded = fractionalPart.padEnd(scale, '0').take(scale)
            padded.toIntOrNull() ?: return null
        } else {
            0
        }

        val minorUnits = whole * powOf10(scale) + fractional
        val signedUnits = if (isNegative) -minorUnits else minorUnits

        return Money(signedUnits, currency)
    }

    /**
     * Parse or throw exception.
     */
    fun parseOrThrow(input: String, defaultCurrency: String = "USD"): Money =
        parse(input, defaultCurrency)
            ?: throw IllegalArgumentException("Cannot parse '$input' as money")

    /**
     * Extract all money amounts from a text string.
     */
    fun extractAll(text: String, defaultCurrency: String = "USD"): List<Money> {
        return PRICE_PATTERN.findAll(text)
            .mapNotNull { match -> parse(match.value, defaultCurrency) }
            .toList()
    }

    /**
     * Try to detect currency from a string.
     */
    fun detectCurrency(input: String): String? {
        for ((symbol, code) in CURRENCY_SYMBOLS) {
            if (input.contains(symbol)) return code
        }
        val codeMatch = Regex("[A-Z]{3}").find(input)
        return codeMatch?.value?.takeIf { CurrencyMetadata.isSupported(it) }
    }

    private fun powOf10(exp: Int): Long {
        var result = 1L
        repeat(exp) { result *= 10 }
        return result
    }
}
