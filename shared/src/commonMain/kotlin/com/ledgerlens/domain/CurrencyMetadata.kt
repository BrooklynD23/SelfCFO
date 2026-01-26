package com.ledgerlens.domain

/**
 * Currency metadata for ISO 4217 currencies.
 * Provides scale (decimal places) and symbols for formatting.
 */
object CurrencyMetadata {
    private val scales = mapOf(
        // Major currencies
        "USD" to 2,
        "EUR" to 2,
        "GBP" to 2,
        "CAD" to 2,
        "AUD" to 2,
        "CHF" to 2,
        "CNY" to 2,
        "INR" to 2,
        "MXN" to 2,
        "BRL" to 2,

        // Zero decimal currencies
        "JPY" to 0,
        "KRW" to 0,
        "VND" to 0,

        // Three decimal currencies
        "BHD" to 3,
        "KWD" to 3,
        "OMR" to 3,

        // Cryptocurrencies (common representations)
        "BTC" to 8,
        "ETH" to 18,
        "USDC" to 6,
        "USDT" to 6
    )

    private val symbols = mapOf(
        "USD" to "$",
        "EUR" to "€",
        "GBP" to "£",
        "JPY" to "¥",
        "CNY" to "¥",
        "INR" to "₹",
        "BTC" to "₿"
    )

    fun getScale(currencyCode: String): Int {
        return scales[currencyCode.uppercase()] ?: 2 // Default to 2 decimal places
    }

    fun getSymbol(currencyCode: String): String {
        return symbols[currencyCode.uppercase()] ?: currencyCode
    }

    fun isValidCurrency(currencyCode: String): Boolean {
        return currencyCode.length == 3 && currencyCode.all { it.isLetter() }
    }
}
